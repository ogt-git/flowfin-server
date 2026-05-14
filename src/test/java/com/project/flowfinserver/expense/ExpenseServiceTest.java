package com.project.flowfinserver.expense;

import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.domain.Expense;
import com.project.flowfinserver.dto.codef.CardBillingDto;
import com.project.flowfinserver.repository.ExpenseRepository;
import com.project.flowfinserver.service.ExpenseClassificationService;
import com.project.flowfinserver.service.ExpenseService;
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

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    ExpenseRepository expenseRepository;

    @Mock
    ExpenseClassificationService classificationService;

    @InjectMocks
    ExpenseService expenseService;

    private static final Long USER_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2025, 5, 1, 12, 0);

    private CardBillingDto dto(String merchantName, Long amount, String paymentType) {
        return new CardBillingDto("신한카드", amount, merchantName, NOW, paymentType);
    }

    @Test
    @DisplayName("신규 거래는 PENDING 상태로 저장된다")
    void saveFromCodef_신규거래_정상저장() {
        // given
        given(expenseRepository.existsByUserIdAndExpenseDateAndMerchantNameAndAmount(
                USER_ID, NOW, "스타벅스", 5000L)).willReturn(false);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);

        // when
        expenseService.saveFromCodef(USER_ID, dto("스타벅스", 5000L, "1"));

        // then
        then(expenseRepository).should().save(captor.capture());
        Expense saved = captor.getValue();
        assertThat(saved.getClassifiedBy()).isEqualTo(ClassifiedBy.PENDING);
        assertThat(saved.isExcluded()).isFalse();
    }

    @Test
    @DisplayName("중복 거래는 save를 호출하지 않고 조용히 스킵된다")
    void saveFromCodef_중복거래_저장스킵() {
        // given
        given(expenseRepository.existsByUserIdAndExpenseDateAndMerchantNameAndAmount(
                USER_ID, NOW, "스타벅스", 5000L)).willReturn(true);

        // when
        expenseService.saveFromCodef(USER_ID, dto("스타벅스", 5000L, "1"));

        // then
        then(expenseRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("DataIntegrityViolationException이 발생해도 상위로 전파되지 않는다")
    void saveFromCodef_DataIntegrityViolation_무시() {
        // given
        given(expenseRepository.existsByUserIdAndExpenseDateAndMerchantNameAndAmount(
                USER_ID, NOW, "스타벅스", 5000L)).willReturn(false);
        given(expenseRepository.save(any())).willThrow(DataIntegrityViolationException.class);

        // when / then — 예외 전파 없이 정상 종료
        assertThatNoException().isThrownBy(
                () -> expenseService.saveFromCodef(USER_ID, dto("스타벅스", 5000L, "1")));
    }

    @Test
    @DisplayName("paymentType '4'(단기카드대출)이면 is_excluded=true로 저장된다")
    void saveFromCodef_카드론_제외처리() {
        // given
        given(expenseRepository.existsByUserIdAndExpenseDateAndMerchantNameAndAmount(
                USER_ID, NOW, "카드론", 1000000L)).willReturn(false);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);

        // when
        expenseService.saveFromCodef(USER_ID, dto("카드론", 1000000L, "4"));

        // then
        then(expenseRepository).should().save(captor.capture());
        assertThat(captor.getValue().isExcluded()).isTrue();
    }

    @Test
    @DisplayName("paymentType '5'(장기카드대출)이면 is_excluded=true로 저장된다")
    void saveFromCodef_장기카드론_제외처리() {
        // given
        given(expenseRepository.existsByUserIdAndExpenseDateAndMerchantNameAndAmount(
                USER_ID, NOW, "장기론", 2000000L)).willReturn(false);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);

        // when
        expenseService.saveFromCodef(USER_ID, dto("장기론", 2000000L, "5"));

        // then
        then(expenseRepository).should().save(captor.capture());
        assertThat(captor.getValue().isExcluded()).isTrue();
    }
}
