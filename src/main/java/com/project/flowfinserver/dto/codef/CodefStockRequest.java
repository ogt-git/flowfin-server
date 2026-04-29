package com.project.flowfinserver.dto.codef;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CodefStockRequest {

    private String connectedId;      // 커넥티드 아이디 (필수)
    private String organization;     // 기관코드 (필수)
    private String account;          // 계좌번호 - 숫자만 (필수)

    private String accountPassword;  // 계좌비밀번호 (평문 입력 → 서버에서 RSA 암호화)
    private String inquiryType;      // 조회구분 "0":결제기준(기본) "1":계좌비번검증 "2":체결기준

    // 키움증권 복수 아이디 보유 고객용
    private String id;               // 복수계정 아이디
    private String addPassword;      // 복수계정 패스워드 (평문 입력 → 서버에서 RSA 암호화)
}
