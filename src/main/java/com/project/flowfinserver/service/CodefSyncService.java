package com.project.flowfinserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.flowfinserver.codef.CodefApiClient;
import com.project.flowfinserver.codef.CodefErrorClassifier;
import com.project.flowfinserver.domain.*;
import com.project.flowfinserver.dto.codef.CardBillingDto;
import com.project.flowfinserver.dto.ExpenseSaveResult;
import com.project.flowfinserver.dto.codef.CodefSyncResultDto;
import com.project.flowfinserver.dto.codef.StockAssetDto;
import com.project.flowfinserver.dto.codef.StockItemDto;
import com.project.flowfinserver.exception.*;
import com.project.flowfinserver.openai.AiExpenseClassifier;
import com.project.flowfinserver.util.MaskingUtil;
import com.project.flowfinserver.repository.CodefConnectedAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.springframework.core.task.TaskRejectedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
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
    private static final int DEFAULT_CARD_MONTHS = 12;
    private static final int BATCH_CARD_MONTHS = 2;
    private static final int LIMITED_CARD_MONTHS = 4;   // BC카드(0305), 수협(0320) 조회 가능 기간 제한
    private static final Set<String> LIMITED_ORG_CARDS = Set.of("0305", "0320");
    private static final String JEJUCARD_ORG = "0321";  // 제주카드: startDate yyyyMMdd 형식 요구
    private static final long SYNC_LOCK_TTL_SECONDS = 300; // 동기화 락 TTL — 서버 장애 시 자동 해제용

    // 2026-05-20 기준 고정 환율: 1 USD = 1,500 KRW
    private static final long USD_TO_KRW_RATE = 1_500L;
    // 평가금액·매입금액·평가손익이 항상 원화로 내려오는 기관
    private static final Set<String> GROUP_A_ORGS = Set.of("0218", "0247", "1247");
    // resAccountCurrency 신뢰 불가 — 전 필드 원화로 간주하는 기관
    private static final Set<String> GROUP_B_ORGS = Set.of("0267", "1267", "0287");

    private final CodefApiClient codefApiClient;
    private final CodefConnectedAccountRepository connectedAccountRepository;
    private final ExpenseSaveService expenseSaveService;
    private final AiExpenseClassifier aiExpenseClassifier;
    private final AssetService assetService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    // 카드 수동 새로고침 — Redis 쿨다운 5분 (키: codef:refresh:cooldown:{userId}:CARD)
    public CodefSyncResultDto manualSyncCard(Long userId) {
        String key = "codef:refresh:cooldown:" + userId + ":CARD";
        Boolean cooldownSet = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 5, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(cooldownSet)) {
            throw new TooManyRequestsException("새로고침은 5분에 한 번만 가능합니다.");
        }
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
        Boolean cooldownSet = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 5, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(cooldownSet)) {
            throw new TooManyRequestsException("새로고침은 5분에 한 번만 가능합니다.");
        }
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

    // 배치 전용 — 단일 연동 계정 동기화 (최근 2개월치만 조회)
    // TRANSIENT_ERROR → CodefRetryableException (배치가 재시도)
    // 그 외 오류 → handleCodefError에서 처리 후 CodefApiException 또는 CodefAuthException throw
    public void syncConnection(CodefConnectedAccount conn) throws Exception {
        if (conn.getAccountType() == AccountType.CARD) {
            syncSingleCardAccount(conn.getUserId(), conn, BATCH_CARD_MONTHS);
        } else if (conn.getAccountType() == AccountType.STOCK) {
            syncSingleStockAccount(conn.getUserId(), conn);
        }
    }

    // 최초 연동 전용 — 전체 기간(12개월) 조회
    public void syncConnectionInitial(CodefConnectedAccount conn) throws Exception {
        if (conn.getAccountType() == AccountType.CARD) {
            syncSingleCardAccount(conn.getUserId(), conn, DEFAULT_CARD_MONTHS);
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

    public CodefSyncResultDto syncCard(Long userId) {
        List<CodefConnectedAccount> accounts =
                connectedAccountRepository.findByUserIdAndAccountTypeAndIsActiveTrue(userId, AccountType.CARD);
        if (accounts.isEmpty()) {
            throw new CodefAccountNotFoundException("연동된 카드 계정이 없습니다. userId=" + userId);
        }

        int savedCount = 0, skippedCount = 0;
        List<String> failedAccounts = new ArrayList<>();

        for (CodefConnectedAccount account : accounts) {
            try {
                int[] counts = syncSingleCardAccount(userId, account, BATCH_CARD_MONTHS);
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
                // 종목 조회 실패 건 skip
                if ("0".equals(firstNonEmpty(item, "resResultCode"))) {
                    log.debug("[CODEF] resResultCode=0 skip itemName={}", firstNonEmpty(item, "resItemName"));
                    skippedCount++;
                    continue;
                }

                String currencyCode = firstNonEmpty(item, "resAccountCurrency");

                long valuationAmt;
                long purchaseAmt;
                long valuationPL;

                if (GROUP_A_ORGS.contains(organizationCode) || GROUP_B_ORGS.contains(organizationCode)) {
                    valuationAmt = parseLongField(item, "resValuationAmt");
                    purchaseAmt  = parseLongField(item, "resPurchaseAmount");
                    valuationPL  = parseLongField(item, "resValuationPL");
                } else {
                    // 그룹 C: resAccountCurrency 기준으로 USD → KRW 환산
                    valuationAmt = toKrw(parseLongField(item, "resValuationAmt"),  currencyCode);
                    purchaseAmt  = toKrw(parseLongField(item, "resPurchaseAmount"), currencyCode);
                    valuationPL  = toKrw(parseLongField(item, "resValuationPL"),   currencyCode);
                }
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
                        purchaseAmt,
                        valuationAmt,
                        valuationPL,
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

    private int[] syncSingleCardAccount(Long userId, CodefConnectedAccount account, int requestedMonths) throws Exception {
        String lockKey = "codef:lock:" + account.getConnectedId();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", SYNC_LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(acquired)) {
            throw new TooManyRequestsException("동기화가 이미 진행 중입니다. 잠시 후 다시 시도해 주세요.");
        }
        try {
            String org = account.getOrganizationCode();
            int months = LIMITED_ORG_CARDS.contains(org)
                    ? Math.min(requestedMonths, LIMITED_CARD_MONTHS)
                    : requestedMonths;

            LocalDate cursor = LocalDate.now().withDayOfMonth(1).minusMonths(months - 1);
            LocalDate end    = LocalDate.now().withDayOfMonth(1);

            int totalSaved = 0, totalSkipped = 0;
            while (!cursor.isAfter(end)) {
                // 제주카드(0321): startDate를 yyyyMMdd(해당 월 1일) 형식으로 전송
                String startDate = JEJUCARD_ORG.equals(org)
                        ? cursor.format(PARSE_FMT)
                        : cursor.format(DATE_FMT);

                HashMap<String, Object> params = new HashMap<>();
                params.put("connectedId", account.getConnectedId());
                params.put("organization", org);
                params.put("startDate", startDate);

                String response = codefApiClient.requestProduct(CARD_PRODUCT_URL, params);
                JsonNode root = objectMapper.readTree(response);

                String resultCode = root.path("result").path("code").asText();
                if (!CODEF_SUCCESS.equals(resultCode)) {
                    String message = root.path("result").path("message").asText();
                    handleCodefError(resultCode, message, account);
                    return new int[]{totalSaved, totalSkipped};
                }

                JsonNode data = root.path("data");
                if (data.isArray()) {
                    log.info("[CODEF Sync] 청구 내역 없음 org={} startDate={}", org, startDate);
                } else {
                    JsonNode txArray = data.path("resChargeHistoryList");
                    if (txArray.isArray()) {
                        int[] counts = saveExpensesFromTxArray(userId, org, txArray);
                        totalSaved   += counts[0];
                        totalSkipped += counts[1];
                    } else {
                        log.warn("[CODEF Sync] resChargeHistoryList 없음 org={} startDate={}", org, startDate);
                    }
                }
                cursor = cursor.plusMonths(1);
            }
            return new int[]{totalSaved, totalSkipped};
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    private int syncSingleStockAccount(Long userId, CodefConnectedAccount account) throws Exception {
        if (account.getAccountNumber() == null || account.getAccountNumber().isBlank()) {
            log.warn("[CODEF Sync] accountNumber 없음 — org={} 건너뜀", account.getOrganizationCode());
            return -1;
        }

        String lockKey = "codef:lock:" + account.getConnectedId();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", SYNC_LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(acquired)) {
            throw new TooManyRequestsException("동기화가 이미 진행 중입니다. 잠시 후 다시 시도해 주세요.");
        }
        try {
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

            String organization = account.getOrganizationCode();
            long totalAsset = 0L;
            int skippedItems = 0;
            List<StockItemDto> items = new ArrayList<>();
            if (itemList.isArray()) {
                for (JsonNode item : itemList) {
                    // 종목 조회 실패 건 skip
                    if ("0".equals(firstNonEmpty(item, "resResultCode"))) {
                        log.debug("[CODEF Sync] resResultCode=0 skip org={} itemName={}", organization, firstNonEmpty(item, "resItemName"));
                        skippedItems++;
                        continue;
                    }
                    // itemCode 없으면 UNIQUE 제약 키 없음 — skip
                    String itemCode = firstNonEmpty(item, "resItemCode");
                    if (itemCode.isBlank()) {
                        log.debug("[CODEF Sync] itemCode 없음 skip org={} itemName={}", organization, firstNonEmpty(item, "resItemName"));
                        skippedItems++;
                        continue;
                    }

                    String currencyCode = firstNonEmpty(item, "resAccountCurrency");

                    long valuationAmt;
                    long purchaseAmt;
                    long valuationPL;

                    if (GROUP_A_ORGS.contains(organization) || GROUP_B_ORGS.contains(organization)) {
                        valuationAmt = parseLongField(item, "resValuationAmt");
                        purchaseAmt  = parseLongField(item, "resPurchaseAmount");
                        valuationPL  = parseLongField(item, "resValuationPL");
                    } else {
                        // 그룹 C: resAccountCurrency 기준으로 USD → KRW 환산
                        valuationAmt = toKrw(parseLongField(item, "resValuationAmt"),  currencyCode);
                        purchaseAmt  = toKrw(parseLongField(item, "resPurchaseAmount"), currencyCode);
                        valuationPL  = toKrw(parseLongField(item, "resValuationPL"),   currencyCode);
                    }
                    totalAsset += valuationAmt;

                    String earningsRateStr = firstNonEmpty(item, "resEarningsRate").replaceAll("[^0-9.\\-]", "");
                    BigDecimal earningsRate = earningsRateStr.isEmpty() ? BigDecimal.ZERO : new BigDecimal(earningsRateStr);

                    items.add(new StockItemDto(
                            firstNonEmpty(item, "resProductType"),
                            firstNonEmpty(item, "resItemName"),
                            itemCode,
                            (int) parseLongField(item, "resQuantity"),
                            purchaseAmt,
                            valuationAmt,
                            valuationPL,
                            earningsRate
                    ));
                }
            }

            long depositReceived = parseLongField(data, "resDepositReceived");
            StockAssetDto assetDto = new StockAssetDto(
                    organization, account.getAccountNumber(), totalAsset, depositReceived);
            AssetAccount assetAccount = assetService.saveOrUpdateAccount(userId, assetDto);
            if (!items.isEmpty()) {
                assetService.saveOrUpdateItems(assetAccount, items);
            }

            log.info("[CODEF Sync] 증권 upsert 완료 org={} 종목={}건 skip={}건", account.getOrganizationCode(), items.size(), skippedItems);
            return 1;
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    // txArray → CardBillingDto 리스트 변환 후 ExpenseSaveService 위임
    // Rule 분류 + 중복 스킵은 ExpenseSaveService, Rule 실패 건은 트랜잭션 커밋 후 AiExpenseClassifier로 비동기 투입
    private int[] saveExpensesFromTxArray(Long userId, String organizationCode, JsonNode txArray) {
        List<CardBillingDto> items = new ArrayList<>();
        for (JsonNode tx : txArray) {
            String paymentType = firstNonEmpty(tx, "resPaymentType");
            // 단기/장기 카드대출은 지출이 아닌 대출상환 — 저장 제외
            if ("4".equals(paymentType) || "5".equals(paymentType)) continue;

            String dateStr = firstNonEmpty(tx, "resUsedDate");
            // resUsedDate 미제공 시 결제예정일로 대체 (KB 할인혜택, 현대 이월약정, 신한 연회비 등)
            if (dateStr.isEmpty()) dateStr = firstNonEmpty(tx, "resPaymentDueDate");

            String merchant  = firstNonEmpty(tx, "resMemberStoreName");
            String amountStr = firstNonEmpty(tx, "resUsedAmount", "resPaymentPrincipal", "resPaymentAmt")
                    .replaceAll("[^0-9\\-]", "");

            if (dateStr.isEmpty() || merchant.isEmpty() || amountStr.isEmpty()) continue;

            long amount = Long.parseLong(amountStr);
            if (amount == 0) continue;

            String usedCard = firstNonEmpty(tx, "resUsedCard");

            LocalDateTime expenseDate = LocalDate.parse(dateStr, PARSE_FMT).atStartOfDay();
            items.add(new CardBillingDto(organizationCode, usedCard, amount, merchant, expenseDate, paymentType));
        }

        log.info("[CODEF] 청구 내역 파싱 org={} total={}", organizationCode, items.size());

        ExpenseSaveResult result = expenseSaveService.saveExpenses(userId, items);

        // AI 분류 대기 Expense → 트랜잭션 커밋 후 비동기 큐 투입
        if (!result.pendingAiIds().isEmpty()) {
            List<Long> ids = result.pendingAiIds();
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (Long id : ids) {
                            try {
                                aiExpenseClassifier.classifyAndUpdate(id);
                            } catch (TaskRejectedException e) {
                                log.warn("[AiClassify] 큐 포화 → 기타지출 동기 확정 expenseId={}", id);
                                aiExpenseClassifier.fallbackToEtc(id);
                            }
                        }
                    }
                });
            } else {
                for (Long id : ids) {
                    try {
                        aiExpenseClassifier.classifyAndUpdate(id);
                    } catch (TaskRejectedException e) {
                        log.warn("[AiClassify] 큐 포화 → 기타지출 동기 확정 expenseId={}", id);
                        aiExpenseClassifier.fallbackToEtc(id);
                    }
                }
            }
        }

        return new int[]{result.savedCount(), items.size() - result.savedCount()};
    }

    private String firstNonEmpty(JsonNode node, String... fields) {
        for (String field : fields) {
            String val = node.path(field).asText("").trim();
            if (!val.isEmpty()) return val;
        }
        return "";
    }

    private long parseLongField(JsonNode node, String... fields) {
        String raw = firstNonEmpty(node, fields).replaceAll("[^0-9.\\-]", "");
        if (raw.isEmpty()) return 0L;
        try {
            return Math.round(Double.parseDouble(raw));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private long toKrw(long amount, String currencyCode) {
        if ("USD".equalsIgnoreCase(currencyCode)) {
            return amount * USD_TO_KRW_RATE;
        }
        return amount;
    }
}
