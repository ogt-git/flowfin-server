package com.project.flowfinserver.controller;

import com.project.flowfinserver.dto.ApiResponse;
import com.project.flowfinserver.dto.expense.ExpenseItemResponse;
import com.project.flowfinserver.dto.expense.ExpenseStatsResponse;
import com.project.flowfinserver.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Tag(name = "Expense", description = "지출 내역 API")
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;
    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

    @Operation(summary = "월별 지출 통계", description = "month=YYYYMM 형식. Redis 1시간 캐싱 적용.")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ExpenseStatsResponse>> getMonthlyStats(
            @RequestParam String month,
            Authentication authentication) {
        YearMonth ym = YearMonth.parse(month, YYYYMM);
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getMonthlyStats(userId, ym.getYear(), ym.getMonthValue())));
    }

    @Operation(summary = "지출 목록 조회", description = "month=YYYYMM 또는 from/to(yyyy-MM-dd) 필터 + 페이지네이션.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ExpenseItemResponse>>> getExpenseList(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long categoryId,
            @Parameter(hidden = true) @PageableDefault(size = 20, sort = "expenseDate", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        if (month != null) {
            YearMonth ym = YearMonth.parse(month, YYYYMM);
            from = ym.atDay(1);
            to = ym.atEndOfMonth();
        }
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getExpenseList(userId, from, to, categoryId, pageable)));
    }
}
