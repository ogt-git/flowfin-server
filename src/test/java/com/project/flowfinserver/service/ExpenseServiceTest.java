package com.project.flowfinserver.service;

import com.project.flowfinserver.cache.ExpenseStatsCacheManager;
import com.project.flowfinserver.domain.Category;
import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.domain.Expense;
import com.project.flowfinserver.dto.expense.ExpenseResponse;
import com.project.flowfinserver.repository.CategoryRepository;
import com.project.flowfinserver.repository.ExpenseRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    ExpenseRepository expenseRepository;

    @Mock
    CategoryRepository categoryRepository;

    @Mock
    ExpenseStatsCacheManager expenseStatsCacheManager;

    @InjectMocks
    ExpenseService expenseService;

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long EXPENSE_ID = 10L;
    private static final Long CATEGORY_ID = 5L;

    private Expense makeExpense(Long userId) {
        return Expense.create(userId, "0301", 5000L, "스타벅스",
                LocalDateTime.of(2024, 5, 1, 10, 0), null, ClassifiedBy.RULE, 100);
    }

    // ==================== updateCategory ====================

    @Test
    @DisplayName("updateCategory — 정상: is_user_modified=true, classified_by=USER 로 업데이트")
    void updateCategory_정상_카테고리_수정() {
        Expense expense = makeExpense(USER_ID);
        Category category = mock(Category.class);
        given(expenseRepository.findById(EXPENSE_ID)).willReturn(Optional.of(expense));
        given(categoryRepository.findById(CATEGORY_ID)).willReturn(Optional.of(category));

        ExpenseResponse response = expenseService.updateCategory(EXPENSE_ID, USER_ID, CATEGORY_ID);

        assertThat(expense.isUserModified()).isTrue();
        assertThat(expense.getClassifiedBy()).isEqualTo(ClassifiedBy.USER);
    }

    @Test
    @DisplayName("updateCategory — 타인 지출 수정 시 AccessDeniedException")
    void updateCategory_타인_지출_수정_접근_거부() {
        Expense expense = makeExpense(OTHER_USER_ID);
        given(expenseRepository.findById(EXPENSE_ID)).willReturn(Optional.of(expense));

        assertThatThrownBy(() -> expenseService.updateCategory(EXPENSE_ID, USER_ID, CATEGORY_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("updateCategory — 제외된 지출 수정 시 IllegalStateException")
    void updateCategory_제외된_지출_수정_불가() {
        Expense expense = makeExpense(USER_ID);
        expense.exclude();
        given(expenseRepository.findById(EXPENSE_ID)).willReturn(Optional.of(expense));

        assertThatThrownBy(() -> expenseService.updateCategory(EXPENSE_ID, USER_ID, CATEGORY_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("updateCategory — 존재하지 않는 지출 조회 시 EntityNotFoundException")
    void updateCategory_존재하지_않는_지출_예외() {
        given(expenseRepository.findById(EXPENSE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.updateCategory(EXPENSE_ID, USER_ID, CATEGORY_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ==================== exclude ====================

    @Test
    @DisplayName("exclude — 정상: is_excluded=true 소프트 처리")
    void exclude_정상_소프트삭제() {
        Expense expense = makeExpense(USER_ID);
        given(expenseRepository.findById(EXPENSE_ID)).willReturn(Optional.of(expense));

        expenseService.exclude(EXPENSE_ID, USER_ID);

        assertThat(expense.isExcluded()).isTrue();
    }

    @Test
    @DisplayName("exclude — 타인 지출 제외 처리 시 AccessDeniedException")
    void exclude_타인_지출_접근_거부() {
        Expense expense = makeExpense(OTHER_USER_ID);
        given(expenseRepository.findById(EXPENSE_ID)).willReturn(Optional.of(expense));

        assertThatThrownBy(() -> expenseService.exclude(EXPENSE_ID, USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("exclude — 이미 제외된 지출 재처리 시 IllegalStateException")
    void exclude_이미_제외된_지출_예외() {
        Expense expense = makeExpense(USER_ID);
        expense.exclude();
        given(expenseRepository.findById(EXPENSE_ID)).willReturn(Optional.of(expense));

        assertThatThrownBy(() -> expenseService.exclude(EXPENSE_ID, USER_ID))
                .isInstanceOf(IllegalStateException.class);
    }
}
