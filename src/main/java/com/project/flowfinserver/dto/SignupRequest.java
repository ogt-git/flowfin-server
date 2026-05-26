package com.project.flowfinserver.dto;

import com.project.flowfinserver.domain.RiskType;
import lombok.Getter;

@Getter
public class SignupRequest {
    private String email;
    private String password;
    private String name;
    private RiskType riskType;
}
