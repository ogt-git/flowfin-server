package com.project.flowfinserver.codef;

import io.codef.api.EasyCodef;
import io.codef.api.EasyCodefServiceType;
import io.codef.api.EasyCodefUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;

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
        return easyCodef.createAccount(resolveServiceType(), accountMap);
    }

    // 데이터 조회
    public String requestProduct(String productUrl, HashMap<String, Object> params) throws Exception {
        return easyCodef.requestProduct(productUrl, resolveServiceType(), params);
    }
}
