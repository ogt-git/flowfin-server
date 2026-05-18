package com.project.flowfinserver.dto.expense;

public record CategoryStatDto(
        int categoryId,
        String name,
        String icon,
        String color,
        long amount,
        double ratio
) {}
