package com.project.flowfinserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.flowfinserver.codef.CodefApiClient;
import com.project.flowfinserver.domain.AccountType;
import com.project.flowfinserver.domain.CodefConnectedAccount;
import com.project.flowfinserver.dto.codef.CodefCardRequest;
import com.project.flowfinserver.dto.codef.CodefConnectRequest;
import com.project.flowfinserver.dto.codef.CodefStockRequest;
import com.project.flowfinserver.exception.CodefAccountNotFoundException;
import com.project.flowfinserver.exception.CodefApiException;
import com.project.flowfinserver.repository.CodefConnectedAccountRepository;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodefService {

    private static final DateTimeFormatter BILLING_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMM");

    private final CodefApiClient codefApiClient;
    private final CodefConnectedAccountRepository connectedAccountRepository;
    private final CodefSyncService codefSyncService;
    private final ObjectMapper objectMapper;

    // self-injection: @Async는 Spring 프록시를 통해야 동작 — 동일 클래스 내 직접 호출 시 비동기 미적용 방지
    @Lazy
    @Autowired
    private CodefService self;

    // 카드/증권 계정 연결 (connectedId 발급)
    public String connectAccount(Long userId, CodefConnectRequest request) throws Exception {
        String businessType = request.getBusinessType().toUpperCase();
        String loginType    = request.getLoginType();

        if (!"CD".equals(businessType) && !"ST".equals(businessType)) {
            throw new IllegalArgumentException("businessType은 CD(카드) 또는 ST(증권)만 허용됩니다.");
        }
        if (!"0".equals(loginType) && !"1".equals(loginType)) {
            throw new IllegalArgumentException("loginType은 '0'(인증서) 또는 '1'(아이디/패스워드)만 허용됩니다.");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("password는 필수입니다.");
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

            for (JsonNode account : successList) {
                String organization = account.path("organization").asText();
                if (!connectedAccountRepository.existsByUserIdAndOrganizationCodeAndAccountType(
                        userId, organization, accountType)) {
                    CodefConnectedAccount conn =
                            CodefConnectedAccount.create(userId, connectedId, organization, accountType);
                    // STOCK 타입이고 계좌번호가 제공된 경우 즉시 저장 (AesEncryptConverter 자동 암호화)
                    if (accountType == AccountType.STOCK && hasValue(request.getAccountNumber())) {
                        conn.updateAccountNumber(request.getAccountNumber());
                    }
                    CodefConnectedAccount saved = connectedAccountRepository.save(conn);
                    log.info("[Connect] saved connectedId for org={} type={}", organization, accountType);
                    // 최초 동기화 비동기 트리거 — 즉시 200 OK 반환 후 별도 스레드에서 실행
                    // try-catch: @Async 실패 시에도 connectAccount 응답에 영향 없도록 격리
                    try {
                        self.triggerInitialSync(userId, saved);
                    } catch (Exception e) {
                        log.warn("[Connect] 최초 동기화 트리거 실패 — 응답에는 영향 없음 userId={} org={}", userId, organization, e);
                    }
                } else {
                    log.info("[Connect] already exists for org={} type={}", organization, accountType);
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
    @Async
    public void triggerInitialSync(Long userId, CodefConnectedAccount connection) {
        log.info("[InitialSync] 최초 동기화 시작 userId={} org={} type={}",
                userId, connection.getOrganizationCode(), connection.getAccountType());
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
            codefSyncService.syncConnection(connection);
        } catch (Exception e) {
            if (isDuplicateLoginException(e)) {
                log.warn("[InitialSync] CF-12201 중복 로그인 — 5초 후 재시도 userId={} org={}",
                        userId, connection.getOrganizationCode());
                sleepQuietly(5_000);
                try {
                    codefSyncService.syncConnection(connection);
                } catch (Exception retry) {
                    log.warn("[InitialSync] CARD 재시도 실패 userId={} org={}",
                            userId, connection.getOrganizationCode(), retry);
                    return;
                }
            } else {
                log.warn("[InitialSync] CARD 최초 동기화 실패 userId={} org={}", userId, connection.getOrganizationCode(), e);
                return;
            }
        }
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
                    return;
                }
            } else {
                log.warn("[InitialSync] STOCK 최초 동기화 실패 userId={} org={}", userId, connection.getOrganizationCode(), e);
                return;
            }
        }
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
