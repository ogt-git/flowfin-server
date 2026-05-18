package com.project.flowfinserver.dto.codef;

import java.time.LocalDateTime;

public record CardBillingDto(
        String cardCompany,
        Long amount,
        String merchantName,
        LocalDateTime expenseDate,
        // resPaymentType: "1"=일시불 "2"=할부 "3"=그외 "4"=단기카드대출 "5"=장기카드대출
        // "4","5"이면 is_excluded=true 처리 (대출성 거래는 지출 제외)
        String paymentType,
        // resCancelYn: "Y"=취소 거래, is_excluded=true 처리
        boolean cancelled,
        // resOverseasYn: "Y"=해외 결제, is_excluded=true 처리
        boolean overseas
) {}
