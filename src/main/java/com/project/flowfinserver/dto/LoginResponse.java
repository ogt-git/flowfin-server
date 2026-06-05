package com.project.flowfinserver.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.project.flowfinserver.domain.RiskType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {
    private Long userId;
    private String accessToken;
    @JsonIgnore
    private String refreshToken;
    private String name;
    private String email;
    private RiskType riskType;
}
