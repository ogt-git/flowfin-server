package com.project.flowfinserver.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {
    private Long userId; //  X-User-Id 헤더
    private String token;
    private String name;
}