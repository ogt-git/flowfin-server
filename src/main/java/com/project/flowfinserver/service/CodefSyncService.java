package com.project.flowfinserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.flowfinserver.codef.CodefApiClient;
import com.project.flowfinserver.codef.CodefErrorClassifier;
import com.project.flowfinserver.domain.*;
import com.project.flowfinserver.dto.codef.CardBillingDto;
import com.project.flowfinserver.dto.codef.CodefSyncResultDto;
import com.project.flowfinserver.dto.codef.StockAssetDto;
import com.project.flowfinserver.dto.codef.StockItemDto;
import com.project.flowfinserver.exception.*;
import com.project.flowfinserver.util.MaskingUtil;
import com.project.flowfinserver.repository.CodefConnectedAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    private final ExpenseSaveService expenseSaveService;
    private final AssetService assetService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    // 카드 수동 새로고침 — Redis 쿨다운 5분 (키: codef:refresh:cooldown:{userId}:CARD)
    public CodefSyncResultDto manualSyncCard(Long userId) {
        String key = "codef:refresh:cooldown:" + userId + ":CARD";
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            throw new TooManyRequestsException("새로고침은 5분에 한 번만 가능합니다.");
        }
        stringRedisTemplate.opsForValue().set(key, "1", 5, TimeUnit.MINUTES);
        LocalDateTime nextAvailableAt = LocalDateTime.now().plusMinutes(5);

        CodefSyncResultDto result;
        try {
            result = syncCard(userId);
        } catch (CodefAccountNotFoundException e) {
            log.info("[ManualSyncCard] 카드 계정 없음 userId={}", userId);
            result = CodefSyncResultDto.builder()
                    .savedCount(0).skippedCount(0).failedAccounts(List.of())
                    .syncedAt(LocalDateTime.now()).build();
        }
        return CodefSyncResultDto.builder()
                .savedCount(result.getSavedCount())
                .skippedCount(result.getSkippedCount())
                .failedAccounts(result.getFailedAccounts())
                .syncedAt(result.getSyncedAt())
                .accountType("CARD")
                .nextAvailableAt(nextAvailableAt)
                .build();
    }

    // 증권 수동 새로고침 — Redis 쿨다운 5분 (키: codef:refresh:cooldown:{userId}:STOCK)
    public CodefSyncResultDto manualSyncStock(Long userId) {
        String key = "codef:refresh:cooldown:" + userId + ":STOCK";
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            throw new TooManyRequestsException("새로고침은 5분에 한 번만 가능합니다.");
        }
        stringRedisTemplate.opsForValue().set(key, "1", 5, TimeUnit.MINUTES);
        LocalDateTime nextAvailableAt = LocalDateTime.now().plusMinutes(5);

        CodefSyncResultDto result;
        try {
            result = syncStock(userId);
        } catch (CodefAccountNotFoundException e) {
            log.info("[ManualSyncStock] 증권 계좌 없음 userId={}", userId);
            result = CodefSyncResultDto.builder()
                    .savedCount(0).skippedCount(0).failedAccounts(List.of())
                    .syncedAt(LocalDateTime.now()).build();
        }
        return CodefSyncResultDto.builder()
                .savedCount(result.getSavedCount())
                .skippedCount(result.getSkippedCount())
                .failedAccounts(result.getFailedAccounts())
                .syncedAt(result.getSyncedAt())
                .accountType("STOCK")
                .nextAvailableAt(nextAvailableAt)
                .build();
    }

    // 배치 전용 — 단일 연동 계정 동기화
    // TRANSIENT_ERROR → CodefRetryableException (배치가 재시도)
    // 그 외 오류 → handleCodefError에서 처리 후 CodefApiException 또는 CodefAuthException throw
    public void syncConnection(CodefConnectedAccount conn) throws Exception {
        if (conn.getAccountType() == AccountType.CARD) {
            syncSingleCardAccount(conn.getUserId(), conn);
        } else if (conn.getAccountType() == AccountType.STOCK) {
            syncSingleStockAccount(conn.getUserId(), conn);
        }
    }

    // 배치 전용 — 트랜잭션 내에서 연동 비활성화 (detached 엔티티 문제 방지)
    @Transactional
    public void deactivateConnection(Long connectionId) {
        connectedAccountRepository.findById(connectionId)
                .ifPresent(CodefConnectedAccount::deactivate);
    }

    // 카드 지출 내역 연동 — 최근 3개월, 계정별 독립 try-catch
    public CodefSyncResultDto syncCard(Long userId) {
        List<CodefConnectedAccount> accounts =
                connectedAccountRepository.findByUserIdAndAccountTypeAndIsActiveTrue(userId, AccountType.CARD);
        if (accounts.isEmpty()) {
            throw new CodefAccountNotFoundException("연동된 카드 계정이 없습니다. userId=" + userId);
        }

        int savedCount = 0, skippedCount = 0;
        List<String> failedAccounts = new ArrayList<>();

        for (CodefConnectedAccount account : accounts) {
            String lockKey = "codef:lock:" + account.getConnectedId();
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey))) {
                throw new TooManyRequestsException("이전 요청이 처리 중입니다. 잠시 후 다시 시도해 주세요.");
            }
            stringRedisTemplate.opsForValue().set(lockKey, "1", 10, TimeUnit.SECONDS);
            try {
                int[] counts = syncSingleCardAccount(userId, account);
                savedCount   += counts[0];
                skippedCount += counts[1];
            } catch (CodefAccountNotFoundException e) {
                throw e;
            } catch (CodefApiException e) {
                failedAccounts.add(account.getOrganizationCode() + "(code=" + e.getCodefCode() + ")");
            } catch (Exception e) {
                log.error("[CODEF Sync] 카드 동기화 실패 org={}", account.getOrganizationCode(), e);
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

    // 증권 종합자산 — AssetAccount/AssetItem upsert
    public CodefSyncResultDto syncStock(Long userId) {
        List<CodefConnectedAccount> accounts =
                connectedAccountRepository.findByUserIdAndAccountTypeAndIsActiveTrue(userId, AccountType.STOCK);
        if (accounts.isEmpty()) {
            throw new CodefAccountNotFoundException("연동된 증권 계좌가 없습니다. userId=" + userId);
        }

        int savedCount = 0, skippedCount = 0;
        List<String> failedAccounts = new ArrayList<>();

        for (CodefConnectedAccount account : accounts) {
            try {
                int result = syncSingleStockAccount(userId, account);
                if (result > 0) savedCount++;
                else skippedCount++;
            } catch (CodefAccountNotFoundException e) {
                throw e;
            } catch (CodefApiException e) {
                failedAccounts.add(account.getOrganizationCode() + "(code=" + e.getCodefCode() + ")");
            } catch (Exception e) {
                log.error("[CODEF Sync] 증권 동기화 실패 org={}", account.getOrganizationCode(), e);
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

    // 증권 원본 응답 JSON을 직접 받아서 파싱+저장 (CodefController 단건 호출용)
    public CodefSyncResultDto saveStockFromRawResponse(Long userId, String organizationCode,
                                                       String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);

        String resultCode = root.path("result").path("code").asText();
        if (!CODEF_SUCCESS.equals(resultCode)) {
            String message = root.path("result").path("message").asText();
            throw new CodefApiException(resultCode, message);
        }

        JsonNode data = root.path("data");
        JsonNode itemList = data.path("resItemList");

        long totalAsset = 0L;
        int skippedCount = 0;
        List<StockItemDto> items = new ArrayList<>();
        if (itemList.isArray()) {
            for (JsonNode item : itemList) {
                long valuationAmt = parseLongField(item, "resValuationAmt");
                totalAsset += valuationAmt;

                String itemCode = firstNonEmpty(item, "resItemCode");
                if (itemCode.isBlank()) {
                    log.debug("[CODEF] itemCode 없음 skip itemName={}", firstNonEmpty(item, "resItemName"));
                    skippedCount++;
                    continue;
                }

                String earningsRateStr = firstNonEmpty(item, "resEarningsRate").replaceAll("[^0-9.\\-]", "");
                BigDecimal earningsRate = earningsRateStr.isEmpty() ? BigDecimal.ZERO : new BigDecimal(earningsRateStr);

                items.add(new StockItemDto(
                        firstNonEmpty(item, "resProductType"),
                        firstNonEmpty(item, "resItemName"),
                        itemCode,
                        (int) parseLongField(item, "resQuantity"),
                        parseLongField(item, "resPurchaseAmount"),
                        valuationAmt,
                        parseLongField(item, "resValuationPL"),
                        earningsRate
                ));
            }
        }

        long depositReceived = parseLongField(data, "resDepositReceived");
        String accountNo = data.path("resAccount").asText("").trim();

        StockAssetDto assetDto = new StockAssetDto(organizationCode, accountNo, totalAsset, depositReceived);
        assetService.syncAssetData(userId, assetDto, items);

        log.info("[CODEF] 증권 자산 저장 완료 org={} 종목={}건 skip={}건", organizationCode, items.size(), skippedCount);
        return CodefSyncResultDto.builder()
                .savedCount(items.size())
                .skippedCount(skippedCount)
                .failedAccounts(List.of())
                .syncedAt(LocalDateTime.now())
                .build();
    }

    // 원본 응답 JSON을 직접 받아서 파싱+저장 (CodefController 단건 호출용)
    public CodefSyncResultDto saveFromRawResponse(Long userId, String connectedId,
                                                  String organizationCode, String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);

        String resultCode = root.path("result").path("code").asText();
        if (!CODEF_SUCCESS.equals(resultCode)) {
            String message = root.path("result").path("message").asText();
            if ("CF-12201".equals(resultCode)) {
                log.warn("[CF-12201] 중복 로그인 세션 — userId={} org={} connectedId={}",
                        userId, organizationCode, MaskingUtil.maskConnectedId(connectedId));
            }
            throw new CodefApiException(resultCode, message);
        }

        JsonNode data = root.path("data");
        if (data.isArray()) {
            log.info("[CODEF] 청구 내역 없음 org={}", organizationCode);
            return CodefSyncResultDto.builder()
                    .savedCount(0).skippedCount(0).failedAccounts(List.of())
                    .syncedAt(LocalDateTime.now()).build();
        }

        JsonNode txArray = data.path("resChargeHistoryList");
        if (!txArray.isArray()) {
            log.warn("[CODEF] 응답 형식 불일치 — resChargeHistoryList 없음 org={}", organizationCode);
            throw new CodefApiException("PARSE_ERROR", "CODEF 응답 형식 불일치: resChargeHistoryList 없음");
        }

        int[] counts = saveExpensesFromTxArray(userId, organizationCode, txArray);
        return CodefSyncResultDto.builder()
                .savedCount(counts[0]).skippedCount(counts[1]).failedAccounts(List.of())
                .syncedAt(LocalDateTime.now()).build();
    }

    // --- private helpers ---

    /**
     * CODEF 오류 코드를 분류하여 적절한 처리를 수행한다.
     * - AUTH_ERROR: updateAccount 호출 (실패 시 연동 비활성화)
     * - AUTH_UNRECOVERABLE / PERMANENT / UNKNOWN: 연동 비활성화
     * - TRANSIENT_ERROR: CodefRetryableException throw (배치 재시도 신호)
     * - RATE_LIMIT: 경고 로그만 기록, skip
     */
    private void handleCodefError(String errorCode, String message, CodefConnectedAccount conn) throws Exception {
        CodefErrorType type = CodefErrorClassifier.classify(errorCode);
        log.warn("[CodefError] code={} type={} connectionId={}", errorCode, type, conn.getId());

        switch (type) {
            case AUTH_ERROR -> {
                log.warn("[CodefError] 인증 오류 — updateAccount 시도 connectionId={}", conn.getId());
                tryUpdateAccount(conn);
            }
            case AUTH_UNRECOVERABLE -> {
                log.error("[CodefError] 복구 불가 인증 오류 — 연동 비활성화 connectionId={}", conn.getId());
                deactivateById(conn.getId());
                throw new CodefApiException(errorCode, message);
            }
            case TRANSIENT_ERROR -> {
                if ("CF-12201".equals(errorCode)) {
                    log.warn("[CF-12201] 중복 로그인 세션 — userId={} org={} connectedId={}",
                            conn.getUserId(), conn.getOrganizationCode(),
                            MaskingUtil.maskConnectedId(conn.getConnectedId()));
                }
                throw new CodefRetryableException(errorCode, "일시적 CODEF 오류: " + errorCode + " — " + message);
            }
            case RATE_LIMIT_ERROR -> log.warn("[CodefError] 요청 한도 초과 — 건너뜀 connectionId={}", conn.getId());
            case PERMANENT_ERROR, UNKNOWN -> {
                log.error("[CodefError] 영구/미분류 오류 — 연동 비활성화 connectionId={}", conn.getId());
                deactivateById(conn.getId());
                throw new CodefApiException(errorCode, message);
            }
        }
    }

    private void tryUpdateAccount(CodefConnectedAccount conn) throws Exception {
        String businessType = conn.getAccountType() == AccountType.CARD ? "CD" : "ST";
        HashMap<String, Object> accountMap = new HashMap<>();
        accountMap.put("connectedId", conn.getConnectedId());
        accountMap.put("countryCode", "KR");
        accountMap.put("businessType", businessType);
        accountMap.put("clientType", "ST".equals(businessType) ? "A" : "P");
        accountMap.put("organization", conn.getOrganizationCode());

        ArrayList<HashMap<String, Object>> accountList = new ArrayList<>();
        accountList.add(accountMap);

        HashMap<String, Object> params = new HashMap<>();
        params.put("accountList", accountList);

        try {
            codefApiClient.updateAccount(params);
            log.info("[CodefError] updateAccount 성공 connectionId={}", conn.getId());
        } catch (Exception e) {
            log.error("[CodefError] updateAccount 실패 — 연동 비활성화 connectionId={}", conn.getId(), e);
            deactivateById(conn.getId());
            throw new CodefAuthException("CODEF updateAccount 실패 — 연동 비활성화됨");
        }
    }

    @Transactional
    void deactivateById(Long connectionId) {
        connectedAccountRepository.findById(connectionId)
                .ifPresent(CodefConnectedAccount::deactivate);
    }

    private int[] syncSingleCardAccount(Long userId, CodefConnectedAccount account) throws Exception {
        String startDate = LocalDate.now().minusMonths(3).format(DATE_FMT);

        HashMap<String, Object> params = new HashMap<>();
        params.put("connectedId", account.getConnectedId());
        params.put("organization", account.getOrganizationCode());
        params.put("startDate", startDate);

        String response = codefApiClient.requestProduct(CARD_PRODUCT_URL, params);
        JsonNode root = objectMapper.readTree(response);

        String resultCode = root.path("result").path("code").asText();
        if (!CODEF_SUCCESS.equals(resultCode)) {
            String message = root.path("result").path("message").asText();
            handleCodefError(resultCode, message, account);
            return new int[]{0, 0};
        }

        JsonNode data = root.path("data");
        if (data.isArray()) {
            log.info("[CODEF Sync] 청구 내역 없음 org={} startDate={}", account.getOrganizationCode(), startDate);
            return new int[]{0, 0};
        }

        JsonNode txArray = data.path("resChargeHistoryList");
        if (!txArray.isArray()) {
            log.warn("[CODEF Sync] resChargeHistoryList 없음 org={}", account.getOrganizationCode());
            return new int[]{0, 0};
        }

        return saveExpensesFromTxArray(userId, account.getOrganizationCode(), txArray);
    }

    private int syncSingleStockAccount(Long userId, CodefConnectedAccount account) throws Exception {
        if (account.getAccountNumber() == null || account.getAccountNumber().isBlank()) {
            log.warn("[CODEF Sync] accountNumber 없음 — org={} 건너뜀", account.getOrganizationCode());
            return -1;
        }

        HashMap<String, Object> params = new HashMap<>();
        params.put("connectedId", account.getConnectedId());
        params.put("organization", account.getOrganizationCode());
        params.put("account", account.getAccountNumber());

        String response = codefApiClient.requestProduct(STOCK_PRODUCT_URL, params);
        JsonNode root = objectMapper.readTree(response);

        String resultCode = root.path("result").path("code").asText();
        if (!CODEF_SUCCESS.equals(resultCode)) {
            String message = root.path("result").path("message").asText();
            handleCodefError(resultCode, message, account);
            return 0;
        }

        JsonNode data = root.path("data");
        JsonNode itemList = data.path("resItemList");

        long totalAsset = 0L;
        List<StockItemDto> items = new ArrayList<>();
        if (itemList.isArray()) {
            for (JsonNode item : itemList) {
                long valuationAmt = parseLongField(item, "resValuationAmt");
                totalAsset += valuationAmt;

                String earningsRateStr = firstNonEmpty(item, "resEarningsRate").replaceAll("[^0-9.\\-]", "");
                BigDecimal earningsRate = earningsRateStr.isEmpty() ? BigDecimal.ZERO : new BigDecimal(earningsRateStr);

                items.add(new StockItemDto(
                        firstNonEmpty(item, "resProductType"),
                        firstNonEmpty(item, "resItemName"),
                        firstNonEmpty(item, "resItemCode"),
                        (int) parseLongField(item, "resQuantity"),
                        parseLongField(item, "resPurchaseAmount"),
                        valuationAmt,
                        parseLongField(item, "resValuationPL"),
                        earningsRate
                ));
            }
        }

        long depositReceived = parseLongField(data, "resDepositReceived");
        StockAssetDto assetDto = new StockAssetDto(
                account.getOrganizationCode(), account.getAccountNumber(), totalAsset, depositReceived);
        AssetAccount assetAccount = assetService.saveOrUpdateAccount(userId, assetDto);
        if (!items.isEmpty()) {
            assetService.saveOrUpdateItems(assetAccount, items);
        }

        log.info("[CODEF Sync] 증권 upsert 완료 org={} 종목={}건", account.getOrganizationCode(), items.size());
        return 1;
    }

    // txArray → CardBillingDto 리스트 변환 후 ExpenseSaveService 위임
    // 분류(Rule→GPT→Fallback) + 중복 스킵은 ExpenseSaveService 내부에서 처리
    private int[] saveExpensesFromTxArray(Long userId, String organizationCode, JsonNode txArray) {
        List<CardBillingDto> items = new ArrayList<>();
        for (JsonNode tx : txArray) {
            String dateStr   = firstNonEmpty(tx, "resUsedDate");
            String merchant  = firstNonEmpty(tx, "resMemberStoreName");
            String amountStr = firstNonEmpty(tx, "resUsedAmount", "resPaymentAmt", "resPaymentPrincipal")
                    .replaceAll("[^0-9\\-]", "");

            if (dateStr.isEmpty() || merchant.isEmpty() || amountStr.isEmpty()) continue;

            long amount = Long.parseLong(amountStr);
            if (amount == 0) continue;

            String paymentType = firstNonEmpty(tx, "resPaymentType");

            LocalDateTime expenseDate = LocalDate.parse(dateStr, PARSE_FMT).atStartOfDay();
            items.add(new CardBillingDto(organizationCode, amount, merchant, expenseDate, paymentType));
        }

        log.info("[CODEF] 청구 내역 파싱 org={} total={}", organizationCode, items.size());

        int saved = expenseSaveService.saveExpenses(userId, items);
        return new int[]{saved, items.size() - saved};
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
}
