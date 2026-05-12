package com.project.flowfinserver.dto.codef;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CodefCardRequest {

    @NotBlank(message = "connectedId는 필수입니다.")
    private String connectedId;       // CODEF 계정 연결 ID (필수)

    @NotBlank(message = "기관 코드는 필수입니다.")
    private String organization;      // 카드사 코드 (필수)

    private String startDate;         // 청구년월 (YYYYMM), 미입력 시 최근 명세서 조회
    private String endDate;           // 청구년월 종료 (YYYYMM), 선택

    private String birthDate;         // 생년월일 (YYYYMMDD), 일부 기관 필수

    // KB카드 카드소지확인 인증용 (필수)
    private String cardNo;            // 카드번호 전체
    private String cardPassword;      // 카드비밀번호 앞 2자리 (평문 입력 → 서버에서 RSA 암호화)

    private String inquiryType;        // 조회구분 "0":카드별(기본) "1":전체조회
    private String memberStoreInfoType; // 가맹점정보 포함여부 "0":미포함(기본) "1":포함
}
