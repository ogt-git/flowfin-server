package com.project.flowfinserver.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class TendencyRequest {

    // 허용값:
    // CONSERVATIVE(안정형) | MODERATELY_CONSERVATIVE(안정추구형) | MODERATE(위험중립형)
    // MODERATELY_AGGRESSIVE(적극투자형) | AGGRESSIVE(공격투자형)
    @NotBlank(message = "투자 성향은 필수입니다.")
    @Size(max = 50)
    private String riskType;
}
