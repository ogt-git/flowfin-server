package com.project.flowfinserver.dto;

import com.project.flowfinserver.domain.RiskType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class SignupRequest {

    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "이메일 형식이 올바르지 않습니다")
    @Size(max = 100, message = "이메일은 100자 이내여야 합니다")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 8, max = 20, message = "비밀번호는 8~20자여야 합니다")
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).+$",
        message = "비밀번호는 영문, 숫자, 특수문자를 각 1개 이상 포함해야 합니다"
    )
    private String password;

    @NotBlank(message = "이름은 필수입니다")
    @Size(max = 30, message = "이름은 30자 이내여야 합니다")
    private String name;

    @NotNull(message = "투자 성향은 필수입니다")
    private RiskType riskType;
}
