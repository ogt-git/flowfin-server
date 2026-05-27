package com.project.flowfinserver.dto.user;

import com.project.flowfinserver.domain.RiskType;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {

    @Size(max = 30, message = "이름은 30자 이내여야 합니다")
    private String name;

    private RiskType riskType;
    private String currentPassword;

    @Size(min = 8, max = 20, message = "비밀번호는 8~20자여야 합니다")
    private String newPassword;
}
