package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.AssetAccount;
import com.project.flowfinserver.domain.AssetItem;
import com.project.flowfinserver.domain.CategoryType;
import com.project.flowfinserver.domain.ManualAsset;
import com.project.flowfinserver.domain.ManualAssetType;
import com.project.flowfinserver.domain.ZeroReason;
import com.project.flowfinserver.dto.asset.AssetSummaryResponse;
import com.project.flowfinserver.dto.asset.StockAccountResponse;
import com.project.flowfinserver.dto.asset.StockItemResponse;
import com.project.flowfinserver.dto.codef.StockAssetDto;
import com.project.flowfinserver.dto.codef.StockItemDto;
import com.project.flowfinserver.dto.portfolio.InvestableAmountResult;
import com.project.flowfinserver.repository.AssetAccountRepository;
import com.project.flowfinserver.repository.AssetItemRepository;
import com.project.flowfinserver.repository.CategoryRepository;
import com.project.flowfinserver.repository.ExpenseRepository;
import com.project.flowfinserver.repository.ManualAssetRepository;
import com.project.flowfinserver.util.MaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetAccountRepository assetAccountRepository;
    private final AssetItemRepository assetItemRepository;
    private final ManualAssetRepository manualAssetRepository;
    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;

    private static final List<ManualAssetType> LIQUID_ASSET_TYPES =
            List.of(ManualAssetType.DEPOSIT, ManualAssetType.CASH);

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

    @Transactional(readOnly = true)
    public java.util.Optional<AssetAccount> findAccount(Long userId, String brokerCode) {
        return assetAccountRepository.findByUserIdAndBrokerCode(userId, brokerCode);
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
     * 신뢰 가능한 CODEF 응답일 때 reconcile + upsert.
     * holdingItemCodes에 없는 기존 종목(매도 완료)을 삭제하고, 보유 종목만 upsert.
     */
    @Transactional
    public void reconcileAndUpsertItems(AssetAccount account, List<StockItemDto> holdingItems,
                                        Set<String> holdingItemCodes) {
        if (holdingItemCodes.isEmpty()) {
            assetItemRepository.deleteByAccountId(account.getId());
            log.debug("[Asset] 전량 매도 — 계좌 내 종목 전체 삭제 accountId={}", account.getId());
        } else {
            assetItemRepository.deleteByAccountIdAndItemCodeNotIn(account.getId(), holdingItemCodes);
            log.debug("[Asset] reconcile 삭제 완료 accountId={} 보유종목코드={}", account.getId(), holdingItemCodes);
        }
        if (!holdingItems.isEmpty()) {
            saveOrUpdateItems(account, holdingItems);
        }
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
     * 투자 가능 금액을 산출하여 반환한다. (자산 동기화 완료 후 호출)
     * Portfolio.investableAmount는 추천 생성 시점의 스냅샷이므로 여기서 갱신하지 않는다.
     */
    @Transactional
    public long updateInvestableAmount(Long userId) {
        long investable = calculateInvestableAmount(userId).amount();
        log.debug("[Asset] investable_amount 산출 userId={} result={}", userId, investable);
        return investable;
    }

    /**
     * 투자 가능 금액을 산출하고 플래그 정보를 포함한 결과를 반환한다.
     * assetLinked=false이면 계산을 시도하지 않고 즉시 반환한다.
     */
    @Transactional(readOnly = true)
    public InvestableAmountResult calculateInvestableAmount(Long userId) {
        List<AssetAccount> accounts = assetAccountRepository.findAllByUserId(userId);
        if (accounts.isEmpty()) {
            return new InvestableAmountResult(false, 0L, ZeroReason.NONE, false);
        }

        long depositSum = accounts.stream().mapToLong(AssetAccount::getDepositReceived).sum();
        long liquidManualSum = manualAssetRepository
                .findByUserIdAndAssetTypeIn(userId, LIQUID_ASSET_TYPES)
                .stream().mapToLong(ManualAsset::getAmount).sum();

        FixedCostResult fixedCost = computeFixedCost(userId);
        long fixedMonthlyAvg = fixedCost.monthlyAvg();
        long emergencyFund = fixedMonthlyAvg;  // 미설정 시 고정비 1개월치 기본 적용

        long liquidTotal = depositSum + liquidManualSum;
        long raw = liquidTotal - fixedMonthlyAvg - emergencyFund;

        ZeroReason zeroReason;
        long amount;
        if (raw > 0) {
            amount = raw;
            zeroReason = ZeroReason.NONE;
        } else if (liquidTotal == 0) {
            amount = 0L;
            zeroReason = ZeroReason.CALCULATED_ZERO;
        } else {
            amount = 0L;
            zeroReason = ZeroReason.CLAMPED;
        }

        log.debug("[Asset] calculateInvestableAmount userId={} deposit={} liquidManual={} fixedAvg={} raw={} result={}",
                userId, depositSum, liquidManualSum, fixedMonthlyAvg, raw, amount);

        return new InvestableAmountResult(true, amount, zeroReason, fixedCost.missing());
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

        long fixedMonthlyAvg = computeFixedCost(userId).monthlyAvg();
        long emergencyFund = fixedMonthlyAvg;
        long investable = Math.max(0L, depositSum + liquidManualSum - fixedMonthlyAvg - emergencyFund);

        return new AssetSummaryResponse(
                totalStockAsset, depositSum, liquidManualSum,
                totalManualSum, investable, fixedMonthlyAvg, emergencyFund);
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
                                    item.getPurchaseAmount(),
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

    private FixedCostResult computeFixedCost(Long userId) {
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);

        List<Long> fixedCategoryIds = categoryRepository.findByType(CategoryType.FIXED)
                .stream().map(c -> c.getId()).toList();
        if (fixedCategoryIds.isEmpty()) {
            return new FixedCostResult(0L, true);
        }

        var fixedExpenses = expenseRepository
                .findByUserIdAndCategoryIdInAndExpenseDateAfterAndIsExcludedFalse(userId, fixedCategoryIds, threeMonthsAgo);
        if (fixedExpenses.isEmpty()) {
            return new FixedCostResult(0L, true);
        }

        long fixedTotal = fixedExpenses.stream().mapToLong(e -> e.getAmount()).sum();

        // 실제 데이터 기간(최소 1개월)으로 나눠 월 평균 산출
        LocalDateTime earliest = fixedExpenses.stream()
                .map(e -> e.getExpenseDate())
                .min(Comparator.naturalOrder())
                .orElse(threeMonthsAgo);
        long months = Math.max(1, ChronoUnit.MONTHS.between(earliest.toLocalDate(), LocalDateTime.now().toLocalDate()));

        return new FixedCostResult(fixedTotal / months, false);
    }

    private record FixedCostResult(long monthlyAvg, boolean missing) {}
}
