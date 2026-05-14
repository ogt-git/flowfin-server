package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.domain.Expense;
import com.project.flowfinserver.dto.codef.CardBillingDto;
import com.project.flowfinserver.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    ExpenseRepository expenseRepository;

    @Mock
    ExpenseClassificationService classificationService;

    @InjectMocks
    ExpenseService expenseService;

    private static final Long USER_ID = 1L;
    private CardBillingDto dto;

    @BeforeEach
    void setUp() {
        dto = new CardBillingDto("0301", 6500L, "스타벅스", LocalDateTime.of(2024, 5, 1, 10, 0), "1");
    }

    @Test
    @DisplayName("saveFromCodef — 신규 거래: repository.save() 1회 호출, classifiedBy=PENDING")
    void saveFromCodef_신규거래_정상저장() {
        given(expenseRepository.existsByUserIdAndExpenseDateAndMerchantNameAndAmount(
                USER_ID, dto.expenseDate(), dto.merchantName(), dto.amount()))
                .willReturn(false);
        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);

        expenseService.saveFromCodef(USER_ID, dto);

        then(expenseRepository).should(times(1)).save(captor.capture());
        assertThat(captor.getValue().getClassifiedBy()).isEqualTo(ClassifiedBy.PENDING);
        assertThat(captor.getValue().getCategoryId()).isNull();
        assertThat(captor.getValue().isUserModified()).isFalse();
        assertThat(captor.getValue().isExcluded()).isFalse();
    }

    @Test
    @DisplayName("saveFromCodef — 중복 거래: repository.save() 호출 안 됨, 예외 미전파")
    void saveFromCodef_중복거래_저장스킵() {
        given(expenseRepository.existsByUserIdAndExpenseDateAndMerchantNameAndAmount(
                USER_ID, dto.expenseDate(), dto.merchantName(), dto.amount()))
                .willReturn(true);

        assertThatNoException().isThrownBy(() -> expenseService.saveFromCodef(USER_ID, dto));

        then(expenseRepository).should(never()).save(any(Expense.class));
    }

    @Test
    @DisplayName("saveFromCodef — DataIntegrityViolationException 발생 시 예외 미전파")
    void saveFromCodef_DataIntegrityViolation_무시() {
        given(expenseRepository.existsByUserIdAndExpenseDateAndMerchantNameAndAmount(
                USER_ID, dto.expenseDate(), dto.merchantName(), dto.amount()))
                .willReturn(false);
        given(expenseRepository.save(any(Expense.class)))
                .willThrow(new DataIntegrityViolationException("Duplicate entry"));

        assertThatNoException().isThrownBy(() -> expenseService.saveFromCodef(USER_ID, dto));
    }
}
