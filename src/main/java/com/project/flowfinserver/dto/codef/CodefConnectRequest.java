package com.project.flowfinserver.dto.codef;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CodefConnectRequest {

    private String organization;  // 금융기관 코드 (예: 0301=국민카드)
    private String businessType;  // CD=카드, BK=은행, ST=증권
    private String loginType;     // 인증서 = 0, 아이디/패스워드 = 1
    private String id;            // 금융기관 로그인 ID
    private String password;      // 금융기관 로그인 PW
}
