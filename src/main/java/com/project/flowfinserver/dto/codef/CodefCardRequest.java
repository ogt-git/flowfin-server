package com.project.flowfinserver.dto.codef;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CodefCardRequest {

    private String connectedId;       // CODEF 계정 연결 ID (필수)
    private String organization;      // 카드사 코드 (필수)

    private String startDate;         // 청구년월 (YYYYMM), 미입력 시 최근 명세서
    private String endDate;           // 종료년월 (YYYYMM)

    private String birthDate;         // 생년월일 (YYYYMMDD), 일부 기관 필수

    // KB카드 카드소지확인 인증용 (필수)
    private String cardNo;            // 카드번호 전체
    private String cardPassword;      // 카드비밀번호 앞 2자리 (평문 입력 → 서버에서 RSA 암호화)

    private String memberStoreInfoYN; // 가맹점정보 포함여부 "0":미포함(기본) "1":포함
}
