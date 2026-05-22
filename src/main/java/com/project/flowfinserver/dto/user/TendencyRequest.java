package com.project.flowfinserver.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class TendencyRequest {

    @NotBlank(message = "투자 성향은 필수입니다.")
    @Size(max = 50)
    private String riskType;
}
