package com.project.flowfinserver.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.flowfinserver.codef.CodefApiClient;
import com.project.flowfinserver.domain.*;
import com.project.flowfinserver.dto.codef.CodefSyncResultDto;
import com.project.flowfinserver.exception.CodefAccountNotFoundException;
import com.project.flowfinserver.repository.AssetAccountRepository;
import com.project.flowfinserver.repository.AssetItemRepository;
import com.project.flowfinserver.repository.CodefConnectedAccountRepository;
import com.project.flowfinserver.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CodefSyncService {

    private static final String CARD_PRODUCT_URL = "/v1/kr/card/p/account/billing-list";
    private static final String STOCK_PRODUCT_URL = "/v1/kr/stock/a/account/financial-assets";
    private static final String CODEF_SUCCESS = "CF-00000";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter PARSE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final CodefApiClient codefApiClient;
    private final CodefConnectedAccountRepository connectedAccountRepository;
    private final ExpenseRepository expenseRepository;
    private final AssetAccountRepository assetAccountRepository;
    private final AssetItemRepository assetItemRepository;
    private final CategoryClassificationService classificationService;
    private final ObjectMapper objectMapper;

    // [우선순위 1] 카드 지출 내역 연동 — 최근 3개월, 계정별 독립 try-catch
    public CodefSyncResultDto syncCard(Long userId) {
        List<CodefConnectedAccount> accounts =
                connectedAccountRepository.findByUserIdAndAccountTypeAndIsActiveTrue(userId, AccountType.CARD);
        if (accounts.isEmpty()) {
            throw new CodefAccountNotFoundException("연동된 카드 계정이 없습니다. userId=" + userId);
        }

        int savedCount = 0;
        int skippedCount = 0;
        List<String> failedAccounts = new ArrayList<>();

        String endDate = LocalDate.now().format(DATE_FMT);
        String startDate = LocalDate.now().minusMonths(3).format(DATE_FMT);

        for (CodefConnectedAccount account : accounts) {
            try {
                HashMap<String, Object> params = new HashMap<>();
                params.put("connectedId", account.getConnectedId());
                params.put("organization", account.getOrganizationCode());
                params.put("startDate", startDate);
                params.put("endDate", endDate);
                params.put("orderBy", "0");

                String response = codefApiClient.requestProduct(CARD_PRODUCT_URL, params);
                JsonNode root = objectMapper.readTree(response);

                String resultCode = root.path("result").path("code").asText();
                if (!CODEF_SUCCESS.equals(resultCode)) {
                    failedAccounts.add(account.getOrganizationCode() + "(code=" + resultCode + ")");
                    continue;
                }

                JsonNode data = root.path("data");
                if (data.isArray()) {
                    log.info("[CODEF Sync] data=[] — no billing records for org={} period={}/{}", account.getOrganizationCode(), startDate, endDate);
                    continue;
                }

                JsonNode txArray = data.path("resChargeHistoryList");
                if (!txArray.isArray()) {
                    log.warn("[CODEF Sync] unexpected data format for org={}: data={}", account.getOrganizationCode(), data);
                    failedAccounts.add(account.getOrganizationCode() + "(응답 형식 불일치)");
                    continue;
                }

                for (JsonNode tx : txArray) {
                    String dateStr = firstNonEmpty(tx, "resUsedDate");
                    String merchant = firstNonEmpty(tx, "resMemberStoreName");
                    String amountStr = firstNonEmpty(tx, "resUsedAmount", "resPaymentAmt", "resPaymentPrincipal")
                            .replaceAll("[^0-9]", "");

                    if (dateStr.isEmpty() || merchant.isEmpty() || amountStr.isEmpty()) continue;

                    LocalDate expenseDate = LocalDate.parse(dateStr, PARSE_FMT);
                    long amount = Long.parseLong(amountStr);

                    if (expenseRepository.existsByUserIdAndExpenseDateAndMerchantNameAndAmount(
                            userId, expenseDate, merchant, amount)) {
                        skippedCount++;
                        continue;
                    }

                    Long categoryId = classificationService.classifyByRule(merchant).orElse(null);
                    ClassifiedBy classifiedBy = classificationService.resolveClassifiedBy(categoryId);
                    ExpenseType expenseType = classificationService.resolveExpenseType(categoryId);

                    Map<String, Object> rawData = objectMapper.convertValue(tx, new TypeReference<>() {});
                    expenseRepository.save(Expense.builder()
                            .userId(userId)
                            .expenseDate(expenseDate)
                            .merchantName(merchant)
                            .amount(amount)
                            .cardCompany(account.getOrganizationCode())
                            .categoryId(categoryId)
                            .classifiedBy(classifiedBy)
                            .expenseType(expenseType)
                            .rawData(rawData)
                            .build());
                    savedCount++;
                }

            } catch (CodefAccountNotFoundException e) {
                throw e;
            } catch (Exception e) {
                log.error("[CODEF Sync] card sync failed for org={}", account.getOrganizationCode(), e);
                failedAccounts.add(account.getOrganizationCode());
            }
        }

        return CodefSyncResultDto.builder()
                .savedCount(savedCount)
                .skippedCount(skippedCount)
                .failedAccounts(failedAccounts)
                .syncedAt(LocalDateTime.now())
                .build();
    }

    // [우선순위 2] 증권 종합자산 연동 — AssetAccount + AssetItem 갱신
    public CodefSyncResultDto syncStock(Long userId) {
        List<CodefConnectedAccount> accounts =
                connectedAccountRepository.findByUserIdAndAccountTypeAndIsActiveTrue(userId, AccountType.STOCK);
        if (accounts.isEmpty()) {
            throw new CodefAccountNotFoundException("연동된 증권 계좌가 없습니다. userId=" + userId);
        }

        int savedCount = 0;
        int skippedCount = 0;
        List<String> failedAccounts = new ArrayList<>();

        for (CodefConnectedAccount account : accounts) {
            try {
                if (account.getAccountNumber() == null || account.getAccountNumber().isBlank()) {
                    log.warn("[CODEF Sync] accountNumber 없음 — org={} 건너뜀", account.getOrganizationCode());
                    failedAccounts.add(account.getOrganizationCode() + "(계좌번호 미등록)");
                    continue;
                }

                HashMap<String, Object> params = new HashMap<>();
                params.put("connectedId", account.getConnectedId());
                params.put("organization", account.getOrganizationCode());
                params.put("account", account.getAccountNumber());

                String response = codefApiClient.requestProduct(STOCK_PRODUCT_URL, params);
                JsonNode root = objectMapper.readTree(response);

                String resultCode = root.path("result").path("code").asText();
                if (!CODEF_SUCCESS.equals(resultCode)) {
                    failedAccounts.add(account.getOrganizationCode() + "(code=" + resultCode + ")");
                    continue;
                }

                String brokerCode = account.getOrganizationCode();
                JsonNode data = root.path("data");
                JsonNode itemList = data.path("resItemList");

                long depositReceived = parseLongField(data, "resDepositReceived");
                long totalEval = 0L;

                AssetAccount assetAccount = assetAccountRepository
                        .findByUserIdAndBrokerCode(userId, brokerCode)
                        .orElseGet(() -> assetAccountRepository.save(
                                AssetAccount.builder()
                                        .userId(userId)
                                        .brokerCode(brokerCode)
                                        .accountNo(account.getAccountNumber())
                                        .totalAsset(0L)
                                        .depositReceived(0L)
                                        .build()
                        ));

                assetItemRepository.deleteByAccountId(assetAccount.getId());

                if (itemList.isArray()) {
                    for (JsonNode item : itemList) {
                        long valuationAmt = parseLongField(item, "resValuationAmt");
                        totalEval += valuationAmt;

                        assetItemRepository.save(AssetItem.builder()
                                .accountId(assetAccount.getId())
                                .userId(userId)
                                .productType(item.path("resProductType").asText(null))
                                .itemName(item.path("resProductName").asText(null))
                                .itemCode(item.path("resItemCode").asText(null))
                                .quantity(parseIntField(item, "resHoldingsQuantity"))
                                .purchaseAmount(parseLongField(item, "resPurchaseAmount"))
                                .valuationAmt(valuationAmt)
                                .valuationPl(parseLongField(item, "resValuationPL"))
                                .earningsRate(parseBigDecimalField(item, "resEarningsRate"))
                                .build());
                        savedCount++;
                    }
                }

                assetAccount.update(totalEval + depositReceived, depositReceived);

            } catch (CodefAccountNotFoundException e) {
                throw e;
            } catch (Exception e) {
                log.error("[CODEF Sync] stock sync failed for org={}", account.getOrganizationCode(), e);
                failedAccounts.add(account.getOrganizationCode());
            }
        }

        return CodefSyncResultDto.builder()
                .savedCount(savedCount)
                .skippedCount(skippedCount)
                .failedAccounts(failedAccounts)
                .syncedAt(LocalDateTime.now())
                .build();
    }

    private String firstNonEmpty(JsonNode node, String... fields) {
        for (String field : fields) {
            String val = node.path(field).asText("").trim();
            if (!val.isEmpty()) return val;
        }
        return "";
    }

    private long parseLongField(JsonNode node, String... fields) {
        String raw = firstNonEmpty(node, fields).replaceAll("[^0-9\\-]", "");
        return raw.isEmpty() ? 0L : Long.parseLong(raw);
    }

    private Integer parseIntField(JsonNode node, String... fields) {
        String raw = firstNonEmpty(node, fields).replaceAll("[^0-9]", "");
        return raw.isEmpty() ? null : Integer.parseInt(raw);
    }

    private BigDecimal parseBigDecimalField(JsonNode node, String... fields) {
        String raw = firstNonEmpty(node, fields).replaceAll("[^0-9.\\-]", "");
        return raw.isEmpty() ? null : new BigDecimal(raw);
    }
}
