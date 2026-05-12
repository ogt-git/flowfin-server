package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.AssetAccount;
import com.project.flowfinserver.domain.AssetItem;
import com.project.flowfinserver.dto.asset.StockAccountResponse;
import com.project.flowfinserver.dto.asset.StockItemResponse;
import com.project.flowfinserver.dto.codef.StockAssetDto;
import com.project.flowfinserver.dto.codef.StockItemDto;
import com.project.flowfinserver.repository.AssetAccountRepository;
import com.project.flowfinserver.repository.AssetItemRepository;
import com.project.flowfinserver.util.MaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetAccountRepository assetAccountRepository;
    private final AssetItemRepository assetItemRepository;

    /**
     * 계좌가 있으면 totalAsset·depositReceived 업데이트, 없으면 신규 저장.
     * accountNo AES-256 암호화는 @Convert(AesEncryptConverter)가 자동 처리.
     */
    @Transactional
    public AssetAccount saveOrUpdateAccount(Long userId, StockAssetDto dto) {
        return assetAccountRepository
                .findByUserIdAndBrokerCodeAndAccountNo(userId, dto.brokerCode(), dto.accountNo())
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

    // TODO: Sprint 2② — investable_amount 산출 로직 연결
    private void updateInvestableAmount(Long userId) {
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
