package com.project.flowfinserver.dto.codef;

import lombok.Getter;

@Getter
public class CodefCardRequest {

    private String connectedId;  // CODEF 계정 연결 ID
    private String organization; // 카드사 코드
    private String startDate;    // 조회 시작일 (yyyyMMdd)
    private String endDate;      // 조회 종료일 (yyyyMMdd)
}
