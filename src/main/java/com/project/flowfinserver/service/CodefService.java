package com.project.flowfinserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.flowfinserver.cache.ExpenseStatsCacheManager;
import com.project.flowfinserver.codef.CodefApiClient;
import com.project.flowfinserver.domain.AccountType;
import com.project.flowfinserver.domain.CodefConnectedAccount;
import com.project.flowfinserver.dto.codef.CodefCardRequest;
import com.project.flowfinserver.dto.codef.CodefConnectRequest;
import com.project.flowfinserver.dto.codef.CodefStockRequest;
import com.project.flowfinserver.exception.CodefAccountNotFoundException;
import com.project.flowfinserver.exception.CodefApiException;
import com.project.flowfinserver.exception.CodefUnsupportedOperationException;
import com.project.flowfinserver.repository.AssetAccountRepository;
import com.project.flowfinserver.repository.AssetItemRepository;
import com.project.flowfinserver.repository.CodefConnectedAccountRepository;
import com.project.flowfinserver.repository.ExpenseRepository;
import com.project.flowfinserver.util.AesEncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodefService {

    private static final DateTimeFormatter BILLING_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMM");

    // ID/PW 방식(loginType=1)으로 증권 자산 조회를 지원하지 않는 증권사 기관 코드
    private static final Set<String> STOCK_IDPW_UNSUPPORTED_ORGS = Set.of(
            "0225",  // IBK투자증권
            "0243",  // 한국투자증권
            "0262",  // 하이투자증권
            "0264",  // 키움증권
            "0270",  // 하나증권
            "0279",  // DB금융투자
            "0287"   // 메리츠증권
    );

    // 인증서 방식(loginType=0)으로 증권 자산 조회를 지원하지 않는 증권사 기관 코드
    private static final Set<String> STOCK_CERT_UNSUPPORTED_ORGS = Set.of(
            "0227"   // 다올투자증권
    );

    private final CodefApiClient codefApiClient;
    private final CodefConnectedAccountRepository connectedAccountRepository;
    private final CodefSyncService codefSyncService;
    private final ObjectMapper objectMapper;
    private final ExpenseRepository expenseRepository;
    private final AssetAccountRepository assetAccountRepository;
    private final AssetItemRepository assetItemRepository;
    private final ExpenseStatsCacheManager expenseStatsCacheManager;
    private final AesEncryptionUtil aesEncryptionUtil;
    private final SyncStatusService syncStatusService;

    // self-injection: @Async는 Spring 프록시를 통해야 동작 — 동일 클래스 내 직접 호출 시 비동기 미적용 방지
    @Lazy
    @Autowired
    private CodefService self;

    // 카드/증권 계정 연결 (connectedId 발급)
    public String connectAccount(Long userId, CodefConnectRequest request) throws Exception {
        String businessType = request.getBusinessType().toUpperCase();
        String loginType    = request.getLoginType();

        if (!hasValue(request.getOrganization())) {
            throw new IllegalArgumentException("organization은 필수입니다.");
        }
        if (!"CD".equals(businessType) && !"ST".equals(businessType)) {
            throw new IllegalArgumentException("businessType은 CD(카드) 또는 ST(증권)만 허용됩니다.");
        }
        if (!"0".equals(loginType) && !"1".equals(loginType)) {
            throw new IllegalArgumentException("loginType은 '0'(인증서) 또는 '1'(아이디/패스워드)만 허용됩니다.");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("password는 필수입니다.");
        }

        // 증권 + 미지원 로그인 방식 조합 사전 차단 — CODEF createAccount 호출 전 거절
        if ("ST".equals(businessType)) {
            if ("1".equals(loginType) && STOCK_IDPW_UNSUPPORTED_ORGS.contains(request.getOrganization())) {
                throw new CodefUnsupportedOperationException(
                        "해당 증권사는 ID/PW 방식으로 자산 조회를 지원하지 않습니다. 인증서 방식으로 연동해 주세요.",
                        "STOCK_IDPW_UNSUPPORTED");
            }
            if ("0".equals(loginType) && STOCK_CERT_UNSUPPORTED_ORGS.contains(request.getOrganization())) {
                throw new CodefUnsupportedOperationException(
                        "해당 증권사는 인증서 방식으로 자산 조회를 지원하지 않습니다.",
                        "STOCK_CERT_UNSUPPORTED");
            }
        }

        log.info("[Connect] userId={} organization={} businessType={} loginType={}",
                userId, request.getOrganization(), businessType, loginType);

        String encryptedPw = codefApiClient.encryptRSA(request.getPassword());

        HashMap<String, Object> accountMap = new HashMap<>();
        accountMap.put("countryCode", "KR");
        accountMap.put("businessType", businessType);
        accountMap.put("clientType", resolveClientType(businessType));
        accountMap.put("organization", request.getOrganization());
        accountMap.put("loginType", loginType);
        accountMap.put("password", encryptedPw);

        if ("0".equals(loginType)) {
            // 인증서 방식 — derFile, keyFile 필수 / certType 하드코딩
            if (!hasValue(request.getDerFileBase64()) || !hasValue(request.getKeyFileBase64())) {
                throw new IllegalArgumentException("인증서 방식(loginType=0)은 derFile과 keyFile이 필수입니다.");
            }
            accountMap.put("certType", "1");
            accountMap.put("derFile", request.getDerFileBase64());
            accountMap.put("keyFile", request.getKeyFileBase64());
        } else {
            // 아이디/패스워드 방식 — id 필수
            if (!hasValue(request.getId())) {
                throw new IllegalArgumentException("아이디/패스워드 방식(loginType=1)은 id가 필수입니다.");
            }
            accountMap.put("id", request.getId());
        }

        if (hasValue(request.getBirthDate())) {
            accountMap.put("birthDate", request.getBirthDate());
        }

        if ("CD".equals(businessType) && hasValue(request.getAccountNumber())) {
            accountMap.put("cardNo", request.getAccountNumber());
        }
        if ("CD".equals(businessType) && hasValue(request.getAccountPassword())) {
            accountMap.put("cardPassword", codefApiClient.encryptRSA(request.getAccountPassword()));
        }

        List<HashMap<String, Object>> accountList = new ArrayList<>();
        accountList.add(accountMap);

        HashMap<String, Object> parameterMap = new HashMap<>();
        parameterMap.put("accountList", accountList);

        String response = codefApiClient.createAccount(parameterMap);

        JsonNode root = objectMapper.readTree(response);
        String resultCode    = root.path("result").path("code").asText();
        String resultMessage = root.path("result").path("message").asText();

        log.info("[Connect] CODEF result code={} message={}", resultCode, resultMessage);

        if (!"CF-00000".equals(resultCode)) {
            throw new CodefApiException(resultCode, resultMessage);
        }

        JsonNode successList = root.path("data").path("successList");
        if (successList.isArray()) {
            String connectedId = root.path("data").path("connectedId").asText().replaceAll("[\\r\\n\\s]", "");
            if (connectedId.isEmpty()) {
                throw new CodefApiException("CONNECTED_ID_EMPTY", "CODEF connectedId가 비어 있습니다. organizationCode=" + request.getOrganization());
            }
            AccountType accountType = "ST".equals(businessType) ? AccountType.STOCK : AccountType.CARD;

            // 인증서 방식(loginType=0)은 loginId=null, ID/PW 방식(loginType=1)은 request.getId() 사용
            String loginId     = "1".equals(loginType) ? request.getId() : null;
            String loginIdHash = (loginId != null) ? aesEncryptionUtil.hash(loginId) : null;

            for (JsonNode account : successList) {
                String organization = account.path("organization").asText();

                // 이미 활성화된 연동이 있으면 skip
                boolean alreadyActive = (loginIdHash != null)
                        ? connectedAccountRepository.existsByUserIdAndOrganizationCodeAndAccountTypeAndLoginIdHashAndIsActiveTrue(
                        userId, organization, accountType, loginIdHash)
                        : connectedAccountRepository.existsByUserIdAndOrganizationCodeAndAccountTypeAndLoginIdHashIsNullAndIsActiveTrue(
                        userId, organization, accountType);

                if (alreadyActive) {
                    log.info("[Connect] already active for org={} type={}", organization, accountType);
                    if (hasValue(request.getAccountNumber()) || hasValue(request.getAccountPassword())) {
                        Optional<CodefConnectedAccount> existingOpt = (loginIdHash != null)
                                ? connectedAccountRepository.findByUserIdAndOrganizationCodeAndAccountTypeAndLoginIdHashAndIsActiveTrue(
                                userId, organization, accountType, loginIdHash)
                                : connectedAccountRepository.findByUserIdAndOrganizationCodeAndAccountTypeAndLoginIdHashIsNullAndIsActiveTrue(
                                userId, organization, accountType);
                        existingOpt.ifPresent(existing -> {
                            if (hasValue(request.getAccountNumber()))   existing.updateAccountNumber(request.getAccountNumber());
                            if (hasValue(request.getAccountPassword())) existing.updateAccountPassword(request.getAccountPassword());
                            connectedAccountRepository.save(existing);
                            log.info("[Connect] 기존 활성 연동 accountNumber/Password 업데이트 org={}", organization);
                        });
                    }
                    continue;
                }

                // 해제(inactive) 상태 레코드가 있으면 재활성화, 없으면 새로 생성
                CodefConnectedAccount conn = ((loginIdHash != null)
                        ? connectedAccountRepository.findByUserIdAndOrganizationCodeAndAccountTypeAndLoginIdHashAndIsActiveFalse(
                        userId, organization, accountType, loginIdHash)
                        : connectedAccountRepository.findByUserIdAndOrganizationCodeAndAccountTypeAndLoginIdHashIsNullAndIsActiveFalse(
                        userId, organization, accountType))
                        .orElseGet(() -> CodefConnectedAccount.create(
                                userId, connectedId, organization, accountType, loginId, loginIdHash));

                conn.reactivate(connectedId, loginId, loginIdHash);
                // STOCK: 증권 계좌번호/비밀번호 / CARD(0455·0301): 카드번호/비밀번호 — 동일 컬럼 재활용
                if (hasValue(request.getAccountNumber()))   conn.updateAccountNumber(request.getAccountNumber());
                if (hasValue(request.getAccountPassword())) conn.updateAccountPassword(request.getAccountPassword());
                CodefConnectedAccount saved = connectedAccountRepository.save(conn);
                log.info("[Connect] saved/reactivated connectedId for org={} type={}", organization, accountType);

                // 최초 동기화 비동기 트리거 — 즉시 200 OK 반환 후 별도 스레드에서 실행
                try {
                    self.triggerInitialSync(userId, saved);
                } catch (Exception e) {
                    log.warn("[Connect] 최초 동기화 트리거 실패 — 응답에는 영향 없음 userId={} org={}", userId, organization, e);
                }
            }
        }

        return response;
    }

    // 연동 해지 — CODEF deleteAccount 호출 후 is_active=false
    @Transactional
    public void disconnect(Long userId, Long connectionId) throws Exception {
        CodefConnectedAccount connection = connectedAccountRepository
                .findByIdAndUserId(connectionId, userId)
                .orElseThrow(() -> new AccessDeniedException("해당 연동 정보에 접근할 수 없습니다."));

        String businessType = connection.getAccountType() == AccountType.CARD ? "CD" : "ST";

        HashMap<String, Object> accountMap = new HashMap<>();
        accountMap.put("connectedId", connection.getConnectedId());
        accountMap.put("countryCode", "KR");
        accountMap.put("businessType", businessType);
        accountMap.put("clientType", resolveClientType(businessType));
        accountMap.put("organization", connection.getOrganizationCode());

        ArrayList<HashMap<String, Object>> accountList = new ArrayList<>();
        accountList.add(accountMap);

        HashMap<String, Object> params = new HashMap<>();
        params.put("accountList", accountList);

        try {
            String response = codefApiClient.deleteAccount(params);
            JsonNode root = objectMapper.readTree(response);
            String resultCode = root.path("result").path("code").asText();
            if (!"CF-00000".equals(resultCode)) {
                log.warn("[Disconnect] CODEF 해지 응답 비정상 code={} connectionId={}", resultCode, connectionId);
            }
        } catch (Exception e) {
            log.warn("[Disconnect] CODEF deleteAccount 실패 connectionId={}", connectionId, e);
        }

        connection.deactivate();

        if (connection.getAccountType() == AccountType.CARD) {
            boolean hasOtherActive = connectedAccountRepository
                    .existsByUserIdAndOrganizationCodeAndAccountTypeAndIsActiveTrueAndIdNot(
                            userId, connection.getOrganizationCode(), AccountType.CARD, connectionId);
            if (!hasOtherActive) {
                expenseRepository.deleteAllByUserIdAndCardCompany(userId, connection.getOrganizationCode());
                expenseStatsCacheManager.evictAllForUser(userId);
                log.info("[Disconnect] 카드 지출 삭제 완료 userId={} org={}", userId, connection.getOrganizationCode());
            } else {
                log.info("[Disconnect] 같은 카드사 다른 활성 연동 존재 — 지출 유지 userId={} org={}", userId, connection.getOrganizationCode());
            }
        } else if (connection.getAccountType() == AccountType.STOCK) {
            assetAccountRepository.findByUserIdAndBrokerCode(userId, connection.getOrganizationCode())
                    .ifPresent(account -> {
                        assetItemRepository.deleteByAccountId(account.getId());
                        assetAccountRepository.delete(account);
                        log.info("[Disconnect] 증권 자산 삭제 완료 userId={} org={}", userId, connection.getOrganizationCode());
                    });
        }

        log.info("[Disconnect] connectionId={} userId={} deactivated", connectionId, userId);
    }

    @Transactional(readOnly = true)
    public List<CodefConnectedAccount> getConnections(Long userId) {
        return connectedAccountRepository.findAllByUserIdAndIsActiveTrue(userId);
    }

    /**
     * 최초 연동 직후 CODEF API를 호출하여 카드 청구 내역 또는 증권 자산을 즉시 수집한다.
     * @Async — Spring 프록시를 통해 호출되어야 비동기 동작 (self 필드로 호출)
     *
     * createAccount 완료 직후 금융기관(특히 신한카드) 서버에 세션이 남아 있으면
     * CF-12201(중복 로그인)이 발생하므로, 5초 대기 후 동기화를 시작한다.
     * CF-12201 재발 시 추가 5초 대기 후 1회 재시도한다.
     */
    @Async("codefExecutor")
    public void triggerInitialSync(Long userId, CodefConnectedAccount connection) {
        log.info("[InitialSync] 최초 동기화 시작 userId={} org={} type={}",
                userId, connection.getOrganizationCode(), connection.getAccountType());
        String type = connection.getAccountType() == AccountType.CARD ? "CARD" : "STOCK";
        syncStatusService.setSyncing(userId, type);
        sleepQuietly(5_000);
        if (connection.getAccountType() == AccountType.CARD) {
            fetchAndSaveCardBilling(userId, connection);
        } else if (connection.getAccountType() == AccountType.STOCK) {
            fetchAndSaveStockAsset(userId, connection);
        }
    }

    private void fetchAndSaveCardBilling(Long userId, CodefConnectedAccount connection) {
        log.info("[InitialSync] CARD 최초 동기화 시작 userId={} org={}", userId, connection.getOrganizationCode());
        try {
            codefSyncService.syncConnectionInitial(connection);
        } catch (Exception e) {
            if (isDuplicateLoginException(e)) {
                log.warn("[InitialSync] CF-12201 중복 로그인 — 5초 후 재시도 userId={} org={}",
                        userId, connection.getOrganizationCode());
                sleepQuietly(5_000);
                try {
                    codefSyncService.syncConnectionInitial(connection);
                } catch (Exception retry) {
                    log.warn("[InitialSync] CARD 재시도 실패 userId={} org={}",
                            userId, connection.getOrganizationCode(), retry);
                    syncStatusService.setFailed(userId, "CARD");
                    return;
                }
            } else {
                log.warn("[InitialSync] CARD 최초 동기화 실패 userId={} org={}", userId, connection.getOrganizationCode(), e);
                syncStatusService.setFailed(userId, "CARD");
                return;
            }
        }
        syncStatusService.setDone(userId, "CARD");
        log.info("[InitialSync] CARD 최초 동기화 완료 userId={} org={}", userId, connection.getOrganizationCode());
    }

    private void fetchAndSaveStockAsset(Long userId, CodefConnectedAccount connection) {
        log.info("[InitialSync] STOCK 최초 동기화 시작 userId={} org={}", userId, connection.getOrganizationCode());
        try {
            codefSyncService.syncConnection(connection);
        } catch (Exception e) {
            if (isDuplicateLoginException(e)) {
                log.warn("[InitialSync] CF-12201 중복 로그인 — 5초 후 재시도 userId={} org={}",
                        userId, connection.getOrganizationCode());
                sleepQuietly(5_000);
                try {
                    codefSyncService.syncConnection(connection);
                } catch (Exception retry) {
                    log.warn("[InitialSync] STOCK 재시도 실패 userId={} org={}",
                            userId, connection.getOrganizationCode(), retry);
                    syncStatusService.setFailed(userId, "STOCK");
                    return;
                }
            } else {
                log.warn("[InitialSync] STOCK 최초 동기화 실패 userId={} org={}", userId, connection.getOrganizationCode(), e);
                syncStatusService.setFailed(userId, "STOCK");
                return;
            }
        }
        syncStatusService.setDone(userId, "STOCK");
        log.info("[InitialSync] STOCK 최초 동기화 완료 userId={} org={}", userId, connection.getOrganizationCode());
    }

    private boolean isDuplicateLoginException(Exception e) {
        Throwable cause = e instanceof RuntimeException && e.getCause() != null ? e.getCause() : e;
        return cause.getMessage() != null && cause.getMessage().contains("CF-12201");
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // 증권 계좌번호 등록 (연결 후 별도 등록)
    @Transactional
    public void registerStockAccountNumber(Long userId, String organization, String accountNumber) {
        CodefConnectedAccount account = connectedAccountRepository
                .findByUserIdAndOrganizationCodeAndAccountType(userId, organization, AccountType.STOCK)
                .orElseThrow(() -> new CodefAccountNotFoundException(
                        "연동된 증권 계정이 없습니다. organization=" + organization));
        account.updateAccountNumber(accountNumber);
        log.info("[Connect] accountNumber registered for org={} userId={}", organization, userId);
    }

    // 카드 청구 내역 조회
    public String getCardBillingList(CodefCardRequest request) throws Exception {
        HashMap<String, Object> params = new HashMap<>();
        params.put("connectedId", request.getConnectedId());
        params.put("organization", request.getOrganization());

        String startDate = hasValue(request.getStartDate())
                ? request.getStartDate()
                : LocalDate.now().minusMonths(3).format(BILLING_DATE_FMT);
        params.put("startDate", startDate);
        if (hasValue(request.getBirthDate()))           params.put("birthDate", request.getBirthDate());
        if (hasValue(request.getInquiryType()))         params.put("inquiryType", request.getInquiryType());
        if (hasValue(request.getMemberStoreInfoType())) params.put("memberStoreInfoType", request.getMemberStoreInfoType());
        if (hasValue(request.getCardNo()))              params.put("cardNo", request.getCardNo());
        if (hasValue(request.getCardPassword()))        params.put("cardPassword", codefApiClient.encryptRSA(request.getCardPassword()));

        return codefApiClient.requestProduct("/v1/kr/card/p/account/billing-list", params);
    }

    // 증권 종합자산 조회
    public String getStockAssets(CodefStockRequest request) throws Exception {
        HashMap<String, Object> params = new HashMap<>();
        params.put("connectedId", request.getConnectedId());
        params.put("organization", request.getOrganization());
        params.put("account", request.getAccount());

        if (hasValue(request.getAccountPassword())) params.put("accountPassword", codefApiClient.encryptRSA(request.getAccountPassword()));

        return codefApiClient.requestProduct("/v1/kr/stock/a/account/financial-assets", params);
    }

    // 인증서 파일을 읽어 Base64 인코딩 후 request DTO에 설정
    public void attachCertFiles(CodefConnectRequest request, MultipartFile derFile, MultipartFile keyFile) throws Exception {
        validateCertFileExtension(derFile, ".der");
        validateCertFileExtension(keyFile, ".key");
        // getMimeEncoder() 사용 금지 — 76자마다 \r\n 삽입으로 CODEF CF-11204 유발
        request.setDerFileBase64(Base64.getEncoder().encodeToString(derFile.getBytes()));
        request.setKeyFileBase64(Base64.getEncoder().encodeToString(keyFile.getBytes()));
    }

    private void validateCertFileExtension(MultipartFile file, String expectedExt) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("인증서 파일이 비어 있습니다: " + expectedExt);
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(expectedExt)) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식입니다: " + originalName + " (허용: " + expectedExt + ")");
        }
    }

    // CD=카드 → "P"(개인), ST=증권 → "A"(통합)
    private String resolveClientType(String businessType) {
        return "ST".equals(businessType) ? "A" : "P";
    }

    private boolean hasValue(String s) {
        return s != null && !s.isBlank();
    }

}
