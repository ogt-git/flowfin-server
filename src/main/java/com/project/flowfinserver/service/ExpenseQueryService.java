package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.CategoryType;
import com.project.flowfinserver.domain.Expense;
import com.project.flowfinserver.dto.PageResponse;
import com.project.flowfinserver.dto.expense.ExpenseListItemDto;
import com.project.flowfinserver.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenseQueryService {

    private final ExpenseRepository expenseRepository;

    public PageResponse<ExpenseListItemDto> getExpenses(
            Long userId,
            LocalDate startDate,
            LocalDate endDate,
            Integer categoryId,
            CategoryType categoryType,
            Pageable pageable) {

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("조회 시작일은 종료일보다 이전이어야 합니다.");
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);
        Long categoryIdLong = categoryId != null ? categoryId.longValue() : null;

        Page<Expense> expensePage = expenseRepository.findExpenses(
                userId, start, end, categoryIdLong, categoryType, pageable);

        Long totalAmount = expenseRepository.sumAmounts(userId, start, end, categoryIdLong, categoryType);

        List<ExpenseListItemDto> items = expensePage.getContent().stream()
                .map(e -> ExpenseListItemDto.from(e, e.getCategory()))
                .collect(Collectors.toList());

        return PageResponse.of(expensePage, items, totalAmount);
    }
}
