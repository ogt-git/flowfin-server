package com.project.flowfinserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class CommunityRequest {

    @NotBlank(message = "제목은 필수입니다")
    @Size(max = 255, message = "제목은 255자 이내여야 합니다")
    private String title;

    @NotBlank(message = "내용은 필수입니다")
    @Size(max = 10000, message = "내용은 10000자 이내여야 합니다")
    private String content;

    @NotBlank(message = "카테고리는 필수입니다")
    @Size(max = 20, message = "카테고리는 20자 이내여야 합니다")
    private String category;
}
