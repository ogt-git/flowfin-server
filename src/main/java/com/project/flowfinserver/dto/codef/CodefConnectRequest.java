package com.project.flowfinserver.dto.codef;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CodefConnectRequest {

    private String organization;  // 금융기관 코드
    private String businessType;  // CD=카드, ST=증권

    // "0": 인증서 방식, "1": 아이디/패스워드 방식
    private String loginType;

    // 인증서 방식 전용 (loginType "0")
    private String derFile;   // BASE64 인코딩된 .der 파일
    private String keyFile;   // BASE64 인코딩된 .key 파일

    // 아이디/패스워드 방식 전용 (loginType "1")
    private String id;

    // 공통 필드
    private String password;   // 인증서 방식: 인증서 비밀번호 / 아이디 방식: 계정 비밀번호
    private String birthDate;  // 선택 (아이디 방식 일부 기관)
}
