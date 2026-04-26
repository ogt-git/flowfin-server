package com.project.flowfinserver.service;

import com.project.flowfinserver.codef.CodefApiClient;
import com.project.flowfinserver.dto.codef.CodefCardRequest;
import com.project.flowfinserver.dto.codef.CodefConnectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class CodefService {

    private final CodefApiClient codefApiClient;

    // 금융기관 계정 연결 → connectedId 반환
    public String connectAccount(CodefConnectRequest request) throws Exception {
        String encryptedId = codefApiClient.encryptRSA(request.getId());
        String encryptedPw = codefApiClient.encryptRSA(request.getPassword());

        HashMap<String, Object> accountMap = new HashMap<>();
        accountMap.put("countryCode", "KR");
        accountMap.put("businessType", request.getBusinessType());
        accountMap.put("clientType", "P");
        accountMap.put("organization", request.getOrganization());
        accountMap.put("loginType", request.getLoginType());
        accountMap.put("id", encryptedId);
        accountMap.put("password", encryptedPw);

        return codefApiClient.createAccount(accountMap);
    }

    // 카드 청구 내역 조회
    public String getCardBillingList(CodefCardRequest request) throws Exception {
        HashMap<String, Object> params = new HashMap<>();
        params.put("connectedId", request.getConnectedId());
        params.put("organization", request.getOrganization());
        params.put("startDate", request.getStartDate());
        params.put("endDate", request.getEndDate());
        params.put("orderBy", "0");     // 0=최신순

        return codefApiClient.requestProduct(
                "/v1/kr/card/p/account/card-summary",
                params
        );
    }
}
