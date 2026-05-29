package com.project.flowfinserver.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class TokenRefreshRequest {

    @NotBlank(message = "리프레시 토큰은 필수입니다")
    private String refreshToken;
}
