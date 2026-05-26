package com.project.flowfinserver.dto.user;

import com.project.flowfinserver.domain.RiskType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class TendencyRequest {

    @NotNull(message = "투자 성향은 필수입니다.")
    private RiskType riskType;
}
