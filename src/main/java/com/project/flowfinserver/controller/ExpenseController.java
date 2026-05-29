package com.project.flowfinserver.controller;

import com.project.flowfinserver.domain.CategoryType;
import com.project.flowfinserver.dto.ApiResponse;
import com.project.flowfinserver.dto.PageResponse;
import com.project.flowfinserver.dto.expense.CategoryUpdateRequest;
import com.project.flowfinserver.dto.expense.ExpenseListItemDto;
import com.project.flowfinserver.dto.expense.ExpenseResponse;
import com.project.flowfinserver.dto.expense.MonthlyStatsResponse;
import com.project.flowfinserver.service.ExpenseQueryService;
import com.project.flowfinserver.service.ExpenseService;
import com.project.flowfinserver.service.ExpenseStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.regex.Pattern;


@Tag(name = "Expense", description = "지출 내역 조회 및 카테고리 수정 API")
@Validated
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {    

    private static final Pattern MONTH_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ExpenseService expenseService;
    private final ExpenseQueryService expenseQueryService;
    private final ExpenseStatsService expenseStatsService;

    @Operation(summary = "월별 지출 통계 조회", description = "월별 총액·카테고리별 집계. month 미입력 시 당월 기본값.")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<MonthlyStatsResponse>> getMonthlyStats(
            @RequestParam(required = false) String month,
            Authentication authentication) {

        Long userId = (Long) authentication.getPrincipal();

        if (month == null || month.isBlank()) {
            month = YearMonth.now().format(MONTH_FMT);
        }

        if (!MONTH_PATTERN.matcher(month).matches()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("month 형식은 YYYY-MM 이어야 합니다.", "INVALID_MONTH_FORMAT"));
        }
        try {
            YearMonth.parse(month, MONTH_FMT);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("month 형식은 YYYY-MM 이어야 합니다.", "INVALID_MONTH_FORMAT"));
        }

        MonthlyStatsResponse result = expenseStatsService.getMonthlyStats(userId, month);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "지출 목록 조회", description = "월별·카테고리 필터 + 페이지네이션. is_excluded=false 건만 반환. month 또는 startDate/endDate는 YYYY-MM 형식.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ExpenseListItemDto>>> getExpenses(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String categoryType,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "페이지는 0 이상이어야 합니다") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다") @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다") int size,
            Authentication authentication) {

        Long userId = (Long) authentication.getPrincipal();

        if (month != null && !month.isBlank()) {
            if (!MONTH_PATTERN.matcher(month).matches()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("month 형식은 YYYY-MM 이어야 합니다.", "INVALID_DATE_FORMAT"));
            }
            startDate = month;
            endDate = month;
        }

        if (startDate != null && !MONTH_PATTERN.matcher(startDate).matches()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("startDate 형식은 YYYY-MM 이어야 합니다.", "INVALID_DATE_FORMAT"));
        }
        if (endDate != null && !MONTH_PATTERN.matcher(endDate).matches()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("endDate 형식은 YYYY-MM 이어야 합니다.", "INVALID_DATE_FORMAT"));
        }

        if (categoryId != null && (categoryId < 1 || categoryId > 11)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("카테고리 ID는 1~11 사이여야 합니다.", "INVALID_CATEGORY_ID"));
        }

        CategoryType parsedCategoryType = null;
        if (categoryType != null && !categoryType.isBlank()) {
            try {
                parsedCategoryType = CategoryType.valueOf(categoryType.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("categoryType은 FIXED, VARIABLE, ETC 중 하나여야 합니다.", "INVALID_CATEGORY_TYPE"));
            }
        }

        LocalDate start;
        LocalDate end;
        try {
            start = startDate != null
                    ? YearMonth.parse(startDate, MONTH_FMT).atDay(1)
                    : LocalDate.now().withDayOfMonth(1);
            end = endDate != null
                    ? YearMonth.parse(endDate, MONTH_FMT).atEndOfMonth()
                    : LocalDate.now();
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("날짜 형식이 올바르지 않습니다 (YYYY-MM)", "INVALID_DATE_FORMAT"));
        }

        PageResponse<ExpenseListItemDto> result = expenseQueryService.getExpenses(
                userId, start, end, categoryId, parsedCategoryType, PageRequest.of(page, size));

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "지출 상세 조회", description = "지출 단건 상세 정보를 반환합니다. 본인 지출만 조회 가능합니다.")
    @GetMapping("/details/{id}")
    public ResponseEntity<ApiResponse<ExpenseResponse>> getDetail(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        ExpenseResponse response = expenseService.getDetail(id, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "카테고리 수동 수정", description = "지출 카테고리를 사용자가 직접 수정합니다. 이후 AI/배치 재분류 덮어쓰기가 영구 차단됩니다.")
    @PutMapping("/category/{id}")
    public ResponseEntity<ApiResponse<ExpenseResponse>> updateCategory(
            @PathVariable Long id,
            @RequestBody @Valid CategoryUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        ExpenseResponse response = expenseService.updateCategory(id, userId, request.getCategoryId());
        return ResponseEntity.ok(ApiResponse.success(response, "카테고리가 수정되었습니다."));
    }

    @Operation(summary = "지출 제외 처리", description = "지출을 제외 처리합니다. DB 삭제 없이 is_excluded=true 소프트 처리.")
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<ApiResponse<Void>> excludeExpense(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        expenseService.exclude(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "지출이 제외 처리되었습니다."));
    }

    @Operation(summary = "검토 필요 건수 조회", description = "AI 분류 신뢰도 60 미만이고 사용자가 아직 수정하지 않은 지출 건수를 반환합니다.")
    @GetMapping("/review-count")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getReviewCount(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        int count = expenseService.getLowConfidenceCount(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }

    @Operation(summary = "신규 지출 건수 조회", description = "오늘 00:00:00 이후 생성된 신규 지출 건수를 반환합니다. 배치 및 수동 새로고침으로 추가된 건 모두 포함합니다.")
    @GetMapping("/new-count")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getNewCount(
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        int count = expenseService.getNewExpenseCount(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }
}
