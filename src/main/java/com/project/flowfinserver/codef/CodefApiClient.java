package com.project.flowfinserver.codef;

import io.codef.api.EasyCodef;
import io.codef.api.EasyCodefServiceType;
import io.codef.api.EasyCodefUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class CodefApiClient {

    private final EasyCodef easyCodef;

    @Value("${codef.public-key}")
    private String publicKey;

    @Value("${codef.service-type}")
    private int serviceType;

    public String encryptRSA(String plainText) throws Exception {
        return EasyCodefUtil.encryptRSA(plainText, publicKey);
    }

    private EasyCodefServiceType resolveServiceType() {
        return (serviceType == 0) ? EasyCodefServiceType.API : EasyCodefServiceType.DEMO;
    }

    // 계정 연결 (connectedId 발급)
    public String createAccount(HashMap<String, Object> accountMap) throws Exception {
        log.info("[CODEF] createAccount params={}", accountMap);
        String response = easyCodef.createAccount(resolveServiceType(), accountMap);
        log.info("[CODEF] createAccount response={}", response);
        return response;
    }

    // 계정 연결 해지
    public String deleteAccount(HashMap<String, Object> accountMap) throws Exception {
        log.info("[CODEF] deleteAccount params={}", accountMap);
        String response = easyCodef.deleteAccount(resolveServiceType(), accountMap);
        log.info("[CODEF] deleteAccount response={}", response);
        return response;
    }

    // 계정 정보 업데이트 (인증 재시도용)
    public String updateAccount(HashMap<String, Object> accountMap) throws Exception {
        log.info("[CODEF] updateAccount params={}", accountMap);
        String response = easyCodef.updateAccount(resolveServiceType(), accountMap);
        log.info("[CODEF] updateAccount response={}", response);
        return response;
    }

    // 데이터 조회
    public String requestProduct(String productUrl, HashMap<String, Object> params) throws Exception {
        log.info("[CODEF] url={} params={}", productUrl, params);
        String response = easyCodef.requestProduct(productUrl, resolveServiceType(), params);
        log.info("[CODEF] response={}", response);
        return response;
    }
}
