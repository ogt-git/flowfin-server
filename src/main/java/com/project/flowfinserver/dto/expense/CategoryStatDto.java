package com.project.flowfinserver.dto.expense;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryStatDto {
    private Long categoryId;
    private String name;
    private String icon;
    private String color;
    private long amount;
    private double ratio;
}
public record CategoryStatDto(
        int categoryId,
        String name,
        String icon,
        String color,
        long amount,
        double ratio
) {}
