package com.project.flowfinserver.dto.codef;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CodefStockRequest {

    private String connectedId;      // 커넥티드 아이디 (필수)

    @JsonSetter("connectedId")
    public void setConnectedId(String connectedId) {
        this.connectedId = connectedId != null ? connectedId.replaceAll("[\\r\\n\\s]", "") : null;
    }
    private String organization;     // 기관코드 (필수)
    private String account;          // 계좌번호 - 숫자만 (필수)

    private String accountPassword;  // 계좌비밀번호 (평문 입력 → 서버에서 RSA 암호화)
}
