package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.AssetAccount;
import com.project.flowfinserver.domain.AssetItem;
import com.project.flowfinserver.domain.CategoryType;
import com.project.flowfinserver.domain.ManualAsset;
import com.project.flowfinserver.domain.ManualAssetType;
import com.project.flowfinserver.dto.asset.AssetSummaryResponse;
import com.project.flowfinserver.dto.asset.StockAccountResponse;
import com.project.flowfinserver.dto.asset.StockItemResponse;
import com.project.flowfinserver.dto.codef.StockAssetDto;
import com.project.flowfinserver.dto.codef.StockItemDto;
import com.project.flowfinserver.repository.AssetAccountRepository;
import com.project.flowfinserver.repository.AssetItemRepository;
import com.project.flowfinserver.repository.CategoryRepository;
import com.project.flowfinserver.repository.ExpenseRepository;
import com.project.flowfinserver.repository.ManualAssetRepository;
import com.project.flowfinserver.repository.PortfolioRepository;
import com.project.flowfinserver.util.MaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetAccountRepository assetAccountRepository;
    private final AssetItemRepository assetItemRepository;
    private final ManualAssetRepository manualAssetRepository;
    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final PortfolioRepository portfolioRepository;

    private static final List<ManualAssetType> LIQUID_ASSET_TYPES =
            List.of(ManualAssetType.DEPOSIT, ManualAssetType.SAVINGS, ManualAssetType.CASH);

    /**
     * 계좌가 있으면 totalAsset·depositReceived 업데이트, 없으면 신규 저장.
     * accountNo AES-256 암호화는 @Convert(AesEncryptConverter)가 자동 처리.
     */
    @Transactional
    public AssetAccount saveOrUpdateAccount(Long userId, StockAssetDto dto) {
        // account_no는 AES-256 랜덤 IV 암호화 → JPA WHERE 절로 직접 조회 불가
        // userId + brokerCode 조합으로 계좌를 식별 (기관당 계좌 1개 기준)
        return assetAccountRepository
                .findByUserIdAndBrokerCode(userId, dto.brokerCode())
                .map(existing -> {
                    existing.updateAsset(dto.totalAsset(), dto.depositReceived());
                    log.debug("[Asset] 계좌 업데이트 userId={} broker={}", userId, dto.brokerCode());
                    return existing;
                })
                .orElseGet(() -> {
                    AssetAccount created = AssetAccount.create(
                            userId, dto.brokerCode(), dto.accountNo(),
                            dto.totalAsset(), dto.depositReceived());
                    AssetAccount saved = assetAccountRepository.save(created);
                    log.debug("[Asset] 계좌 신규 저장 userId={} broker={}", userId, dto.brokerCode());
                    return saved;
                });
    }

    /**
     * 각 종목을 upsert. (account_id, item_code) UNIQUE — 있으면 업데이트, 없으면 저장.
     */
    @Transactional
    public void saveOrUpdateItems(AssetAccount account, List<StockItemDto> items) {
        for (StockItemDto dto : items) {
            assetItemRepository
                    .findByAccountIdAndItemCode(account.getId(), dto.itemCode())
                    .ifPresentOrElse(
                            existing -> existing.update(
                                    dto.quantity(), dto.purchaseAmount(),
                                    dto.valuationAmt(), dto.valuationPl(), dto.earningsRate()),
                            () -> assetItemRepository.save(AssetItem.create(
                                    account.getId(), account.getUserId(),
                                    dto.productType(), dto.itemName(), dto.itemCode(),
                                    dto.quantity(), dto.purchaseAmount(),
                                    dto.valuationAmt(), dto.valuationPl(), dto.earningsRate()))
                    );
        }
        log.debug("[Asset] 종목 upsert 완료 accountId={} count={}", account.getId(), items.size());
    }

    /**
     * CODEF 증권 자산 데이터를 단일 트랜잭션으로 계좌·종목 upsert한다.
     * 조회 기준: userId + brokerCode (account_no는 AES-256 암호화로 JPA 직접 조회 불가)
     */
    @Transactional
    public void syncAssetData(Long userId, StockAssetDto accountDto, List<StockItemDto> items) {
        AssetAccount account = assetAccountRepository
                .findByUserIdAndBrokerCode(userId, accountDto.brokerCode())
                .map(existing -> {
                    existing.updateAsset(accountDto.totalAsset(), accountDto.depositReceived());
                    log.debug("[Asset] 계좌 업데이트 userId={} broker={}", userId, accountDto.brokerCode());
                    return existing;
                })
                .orElseGet(() -> {
                    AssetAccount created = AssetAccount.create(
                            userId, accountDto.brokerCode(), accountDto.accountNo(),
                            accountDto.totalAsset(), accountDto.depositReceived());
                    AssetAccount saved = assetAccountRepository.save(created);
                    log.debug("[Asset] 계좌 신규 저장 userId={} broker={}", userId, accountDto.brokerCode());
                    return saved;
                });

        for (StockItemDto itemDto : items) {
            // itemCode 미제공 기관 존재 — item_name 대체 시 동명 종목 충돌 가능성으로 skip 선택
            if (itemDto.itemCode() == null || itemDto.itemCode().isBlank()) {
                log.debug("[AssetItem] itemCode 없음 skip itemName={}", itemDto.itemName());
                continue;
            }
            try {
                assetItemRepository
                        .findByAccountIdAndItemCode(account.getId(), itemDto.itemCode())
                        .ifPresentOrElse(
                                existing -> existing.update(
                                        itemDto.quantity(), itemDto.purchaseAmount(),
                                        itemDto.valuationAmt(), itemDto.valuationPl(), itemDto.earningsRate()),
                                () -> assetItemRepository.save(AssetItem.create(
                                        account.getId(), account.getUserId(),
                                        itemDto.productType(), itemDto.itemName(), itemDto.itemCode(),
                                        itemDto.quantity(), itemDto.purchaseAmount(),
                                        itemDto.valuationAmt(), itemDto.valuationPl(), itemDto.earningsRate()))
                        );
            } catch (DataIntegrityViolationException e) {
                log.warn("[AssetItem] 중복 저장 시도 감지 accountId={} itemCode={}", account.getId(), itemDto.itemCode());
            }
        }

        updateInvestableAmount(userId);
    }

    /**
     * 투자 가능 금액을 산출하여 Portfolio 테이블을 동기화한다.
     * 공식: max(0, 예수금합 + 유동수동자산합 - 고정비월평균 - 비상금(고정비1개월))
     * 포트폴리오가 없는 경우 계산만 수행하고 업데이트는 건너뜀.
     */
    @Transactional
    public long updateInvestableAmount(Long userId) {
        long depositSum = assetAccountRepository.findAllByUserId(userId)
                .stream().mapToLong(AssetAccount::getDepositReceived).sum();

        long liquidManualSum = manualAssetRepository
                .findByUserIdAndAssetTypeIn(userId, LIQUID_ASSET_TYPES)
                .stream().mapToLong(ManualAsset::getAmount).sum();

        long fixedMonthlyAvg = computeFixedMonthlyAvg(userId);
        long emergencyFund = fixedMonthlyAvg;
        long investable = Math.max(0L, depositSum + liquidManualSum - fixedMonthlyAvg - emergencyFund);

        portfolioRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .ifPresent(p -> p.updateInvestableAmount(investable));

        log.debug("[Asset] investable_amount 갱신 userId={} deposit={} liquidManual={} fixedAvg={} result={}",
                userId, depositSum, liquidManualSum, fixedMonthlyAvg, investable);
        return investable;
    }

    /**
     * 총 자산 요약을 반환한다. (읽기 전용, Portfolio 업데이트 없음)
     */
    @Transactional(readOnly = true)
    public AssetSummaryResponse getAssetSummary(Long userId) {
        List<AssetAccount> accounts = assetAccountRepository.findAllByUserId(userId);
        long totalStockAsset = accounts.stream().mapToLong(AssetAccount::getTotalAsset).sum();
        long depositSum = accounts.stream().mapToLong(AssetAccount::getDepositReceived).sum();

        long liquidManualSum = manualAssetRepository
                .findByUserIdAndAssetTypeIn(userId, LIQUID_ASSET_TYPES)
                .stream().mapToLong(ManualAsset::getAmount).sum();

        long totalManualSum = manualAssetRepository.findAllByUserId(userId)
                .stream().mapToLong(ManualAsset::getAmount).sum();

        long fixedMonthlyAvg = computeFixedMonthlyAvg(userId);
        long emergencyFund = fixedMonthlyAvg;
        long investable = Math.max(0L, depositSum + liquidManualSum - fixedMonthlyAvg - emergencyFund);

        return new AssetSummaryResponse(
                totalStockAsset, depositSum, liquidManualSum,
                totalManualSum, investable, fixedMonthlyAvg, emergencyFund);
    }

    private long computeFixedMonthlyAvg(Long userId) {
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);

        List<Long> fixedCategoryIds = categoryRepository.findByType(CategoryType.FIXED)
                .stream().map(c -> c.getId()).toList();
        if (fixedCategoryIds.isEmpty()) return 0L;

        var fixedExpenses = expenseRepository
                .findByUserIdAndCategoryIdInAndExpenseDateAfter(userId, fixedCategoryIds, threeMonthsAgo);
        if (fixedExpenses.isEmpty()) return 0L;

        long fixedTotal = fixedExpenses.stream().mapToLong(e -> e.getAmount()).sum();

        // 실제 데이터 기간(최소 1개월)으로 나눠 월 평균 산출
        LocalDateTime earliest = fixedExpenses.stream()
                .map(e -> e.getExpenseDate())
                .min(Comparator.naturalOrder())
                .orElse(threeMonthsAgo);
        long months = Math.max(1, ChronoUnit.MONTHS.between(earliest.toLocalDate(), LocalDateTime.now().toLocalDate()));

        return fixedTotal / months;
    }

    @Transactional(readOnly = true)
    public List<StockAccountResponse> getStocks(Long userId) {
        List<AssetAccount> accounts = assetAccountRepository.findAllByUserId(userId);

        return accounts.stream()
                .map(account -> {
                    List<StockItemResponse> itemResponses = assetItemRepository
                            .findAllByAccountId(account.getId())
                            .stream()
                            .map(item -> new StockItemResponse(
                                    item.getItemName(),
                                    item.getItemCode(),
                                    item.getQuantity(),
                                    item.getValuationAmt(),
                                    item.getValuationPl(),
                                    item.getEarningsRate()))
                            .toList();

                    return new StockAccountResponse(
                            account.getBrokerCode(),
                            MaskingUtil.maskAccountNumber(account.getAccountNo()),
                            account.getTotalAsset(),
                            account.getDepositReceived(),
                            itemResponses);
                })
                .toList();
    }
}
