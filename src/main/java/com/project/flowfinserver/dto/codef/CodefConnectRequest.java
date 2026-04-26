package com.project.flowfinserver.dto.codef;

import lombok.Getter;

@Getter
public class CodefConnectRequest {

    private String organization;  // 금융기관 코드 (예: 0301=신한카드, 0004=KB국민은행)
    private String businessType;  // CD=카드, BK=은행, ST=증권
    private String loginType;     // 1=인터넷뱅킹, 2=카드번호
    private String id;            // 금융기관 로그인 ID
    private String password;      // 금융기관 로그인 PW
}
