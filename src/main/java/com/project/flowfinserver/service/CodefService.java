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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodefService {

    private final CodefApiClient codefApiClient;
    private final CodefConnectedAccountRepository connectedAccountRepository;
    private final ObjectMapper objectMapper;

    // 카드/증권 계정 연결 (connectedId 발급)
    public String connectAccount(Long userId, CodefConnectRequest request) throws Exception {
        log.info("[Connect] userId={} organization={} businessType={} loginType={} id={}",
                userId, request.getOrganization(), request.getBusinessType(),
                request.getLoginType(), request.getId());

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("password는 필수입니다.");
        }

        String encryptedPw = codefApiClient.encryptRSA(request.getPassword());

        HashMap<String, Object> accountMap = new HashMap<>();
        accountMap.put("countryCode", "KR");
        accountMap.put("businessType", request.getBusinessType().toUpperCase());
        accountMap.put("clientType", "P");
        accountMap.put("organization", request.getOrganization());
        accountMap.put("loginType", request.getLoginType());
        accountMap.put("id", request.getId());
        accountMap.put("password", encryptedPw);

        ArrayList<HashMap<String, Object>> accountList = new ArrayList<>();
        accountList.add(accountMap);

        HashMap<String, Object> parameterMap = new HashMap<>();
        parameterMap.put("accountList", accountList);

        String response = codefApiClient.createAccount(parameterMap);

        JsonNode root = objectMapper.readTree(response);
        String resultCode = root.path("result").path("code").asText();
        String resultMessage = root.path("result").path("message").asText();

        log.info("[Connect] CODEF result code={} message={}", resultCode, resultMessage);

        if (!"CF-00000".equals(resultCode)) {
            throw new CodefApiException(resultCode, resultMessage);
        }

        JsonNode successList = root.path("data").path("successList");
        if (successList.isArray()) {
            for (JsonNode account : successList) {
                String connectedId = root.path("data").path("connectedId").asText();
                String organization = account.path("organization").asText();
                String businessType = account.path("businessType").asText();
                AccountType accountType = "ST".equals(businessType) ? AccountType.STOCK : AccountType.CARD;

                if (!connectedAccountRepository.existsByUserIdAndOrganizationCodeAndAccountType(
                        userId, organization, accountType)) {
                    connectedAccountRepository.save(CodefConnectedAccount.builder()
                            .userId(userId)
                            .connectedId(connectedId)
                            .organizationCode(organization)
                            .accountType(accountType)
                            .build());
                    log.info("[Connect] saved connectedId for org={} type={}", organization, accountType);
                } else {
                    log.info("[Connect] already exists for org={} type={}", organization, accountType);
                }
            }
        }

        return response;
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

        if (hasValue(request.getStartDate()))         params.put("startDate", request.getStartDate());
        if (hasValue(request.getEndDate()))           params.put("endDate", request.getEndDate());
        if (hasValue(request.getBirthDate()))         params.put("birthDate", request.getBirthDate());
        if (hasValue(request.getMemberStoreInfoYN())) params.put("memberStoreInfoYN", request.getMemberStoreInfoYN());

        if (hasValue(request.getCardNo()))       params.put("cardNo", request.getCardNo());
        if (hasValue(request.getCardPassword())) params.put("cardPassword", codefApiClient.encryptRSA(request.getCardPassword()));

        return codefApiClient.requestProduct("/v1/kr/card/p/account/billing-list", params);
    }

    // 증권 종합자산 조회
    public String getStockAssets(CodefStockRequest request) throws Exception {
        HashMap<String, Object> params = new HashMap<>();
        params.put("connectedId", request.getConnectedId());
        params.put("organization", request.getOrganization());
        params.put("account", request.getAccount());

        if (hasValue(request.getAccountPassword())) params.put("accountPassword", codefApiClient.encryptRSA(request.getAccountPassword()));
        if (hasValue(request.getInquiryType()))     params.put("inquiryType", request.getInquiryType());
        if (hasValue(request.getId()))              params.put("id", request.getId());
        if (hasValue(request.getAddPassword()))     params.put("add_password", codefApiClient.encryptRSA(request.getAddPassword()));

        return codefApiClient.requestProduct("/v1/kr/stock/a/account/financial-assets", params);
    }

    private boolean hasValue(String s) {
        return s != null && !s.isBlank();
    }
}
