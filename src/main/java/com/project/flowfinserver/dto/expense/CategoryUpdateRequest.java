package com.project.flowfinserver.dto.expense;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CategoryUpdateRequest {

    @NotNull(message = "카테고리 ID는 필수입니다.")
    private Long categoryId;
}
