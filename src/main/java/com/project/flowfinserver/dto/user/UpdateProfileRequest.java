package com.project.flowfinserver.dto.user;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {
    private String name;
    private String riskType;
    private String currentPassword;
    private String newPassword;
}
