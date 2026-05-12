package com.project.flowfinserver.expense;

// 테스트 대상: ExpenseSaveService.saveExpenses(Long userId, List<CardBillingDto> items)
// — 분류 후 저장, 중복(DataIntegrityViolationException) skip, is_user_modified 보호

import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.domain.Expense;
import com.project.flowfinserver.dto.ClassificationResult;
import com.project.flowfinserver.dto.codef.CardBillingDto;
import com.project.flowfinserver.repository.ExpenseRepository;
import com.project.flowfinserver.service.ExpenseClassificationService;
import com.project.flowfinserver.service.ExpenseSaveService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ExpenseSaveServiceTest {

    @Mock
    ExpenseRepository expenseRepository;

    @Mock
    ExpenseClassificationService classificationService;

    @InjectMocks
    ExpenseSaveService expenseSaveService;

    private static final Long USER_ID = 1L;
    private static final ClassificationResult RULE_RESULT = ClassificationResult.ofRule(5L);

    private CardBillingDto starbucks;
    private CardBillingDto coupang;
    private CardBillingDto netflix;

    @BeforeEach
    void setUp() {
        starbucks = new CardBillingDto("0301", 6500L,  "스타벅스",  LocalDateTime.of(2024, 4, 1, 0, 0));
        coupang   = new CardBillingDto("0301", 35000L, "쿠팡",      LocalDateTime.of(2024, 4, 2, 0, 0));
        netflix   = new CardBillingDto("0301", 13500L, "넷플릭스",  LocalDateTime.of(2024, 4, 3, 0, 0));
    }

    // ==================== 정상 저장 ====================

    @Test
    @DisplayName("1건 입력 시 expenseRepository.save()가 1회 호출된다")
    void 단건_입력_시_save가_1회_호출된다() {
        // given
        given(classificationService.classify(anyString(), anyLong())).willReturn(RULE_RESULT);

        // when
        int count = expenseSaveService.saveExpenses(USER_ID, List.of(starbucks));

        // then
        then(expenseRepository).should(times(1)).save(any(Expense.class));
        assertThat(count)
                .as("저장 성공 건수는 1이어야 한다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("저장된 Expense의 classifiedBy가 ClassificationResult의 값과 일치한다")
    void 저장된_Expense의_classifiedBy가_ClassificationResult와_일치한다() {
        // given
        given(classificationService.classify(anyString(), anyLong())).willReturn(RULE_RESULT);
        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);

        // when
        expenseSaveService.saveExpenses(USER_ID, List.of(starbucks));

        // then
        then(expenseRepository).should().save(captor.capture());
        assertThat(captor.getValue().getClassifiedBy())
                .as("Expense의 classifiedBy는 ClassificationResult.RULE이어야 한다")
                .isEqualTo(ClassifiedBy.RULE);
    }

    @Test
    @DisplayName("저장된 Expense의 isUserModified는 항상 false이다")
    void 저장된_Expense의_isUserModified는_항상_false이다() {
        // given
        given(classificationService.classify(anyString(), anyLong())).willReturn(RULE_RESULT);
        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);

        // when
        expenseSaveService.saveExpenses(USER_ID, List.of(starbucks));

        // then
        then(expenseRepository).should().save(captor.capture());
        assertThat(captor.getValue().isUserModified())
                .as("신규 저장 Expense는 is_user_modified=false이어야 한다 (CRITICAL — 자동 덮어쓰기 방지)")
                .isFalse();
    }

    @Test
    @DisplayName("저장된 Expense의 isExcluded는 항상 false이다")
    void 저장된_Expense의_isExcluded는_항상_false이다() {
        // given
        given(classificationService.classify(anyString(), anyLong())).willReturn(RULE_RESULT);
        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);

        // when
        expenseSaveService.saveExpenses(USER_ID, List.of(starbucks));

        // then
        then(expenseRepository).should().save(captor.capture());
        assertThat(captor.getValue().isExcluded())
                .as("신규 저장 Expense는 is_excluded=false이어야 한다 (물리 삭제 금지 — 소프트 처리만 허용)")
                .isFalse();
    }

    @Test
    @DisplayName("저장된 Expense의 userId가 입력값과 일치한다")
    void 저장된_Expense의_userId가_입력값과_일치한다() {
        // given
        given(classificationService.classify(anyString(), anyLong())).willReturn(RULE_RESULT);
        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);

        // when
        expenseSaveService.saveExpenses(USER_ID, List.of(starbucks));

        // then
        then(expenseRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserId())
                .as("Expense의 userId는 saveExpenses() 입력 userId와 같아야 한다")
                .isEqualTo(USER_ID);
    }

    // ==================== 복수 항목 ====================

    @Test
    @DisplayName("3건 입력 시 expenseRepository.save()가 3회 호출된다")
    void 복수건_입력_시_save가_항목_수만큼_호출된다() {
        // given
        given(classificationService.classify(anyString(), anyLong())).willReturn(RULE_RESULT);

        // when
        int count = expenseSaveService.saveExpenses(USER_ID, List.of(starbucks, coupang, netflix));

        // then
        then(expenseRepository).should(times(3)).save(any(Expense.class));
        assertThat(count)
                .as("3건 모두 저장 성공 시 반환값은 3이어야 한다")
                .isEqualTo(3);
    }

    // ==================== 중복 거래 skip ====================

    @Test
    @DisplayName("DataIntegrityViolationException 발생 시 예외가 외부로 전파되지 않는다")
    void 중복_예외_발생_시_외부로_전파되지_않는다() {
        // given
        given(classificationService.classify(anyString(), anyLong())).willReturn(RULE_RESULT);
        given(expenseRepository.save(any(Expense.class)))
                .willThrow(new DataIntegrityViolationException("Duplicate entry"));

        // when / then
        assertThatNoException()
                .as("DataIntegrityViolationException은 서비스 내부에서 catch되어 외부로 전파되지 않아야 한다")
                .isThrownBy(() -> expenseSaveService.saveExpenses(USER_ID, List.of(starbucks)));
    }

    @Test
    @DisplayName("3건 중 2번째만 중복 — save() 3회 시도, 성공 2회, 반환값 2")
    void 중복_발생한_건을_제외한_나머지는_정상_저장된다() {
        // given
        given(classificationService.classify(anyString(), anyLong())).willReturn(RULE_RESULT);
        given(expenseRepository.save(any(Expense.class)))
                .willReturn(null)                                                  // 1번째 성공
                .willThrow(new DataIntegrityViolationException("Duplicate entry")) // 2번째 중복
                .willReturn(null);                                                 // 3번째 성공

        // when
        int savedCount = expenseSaveService.saveExpenses(USER_ID, List.of(starbucks, coupang, netflix));

        // then
        then(expenseRepository).should(times(3)).save(any(Expense.class));
        assertThat(savedCount)
                .as("3건 중 1건 중복(skip) 시 저장 성공 건수는 2이어야 한다")
                .isEqualTo(2);
    }

    // ==================== is_user_modified 보호 (CRITICAL) ====================

    @Test
    @DisplayName("신규 Expense는 반드시 isUserModified=false로 생성된다 (자동 분류 덮어쓰기 방지 불변 조건)")
    void 신규_Expense는_isUserModified가_false이다() {
        // given
        given(classificationService.classify(anyString(), anyLong())).willReturn(RULE_RESULT);
        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);

        // when
        expenseSaveService.saveExpenses(USER_ID, List.of(starbucks, coupang, netflix));

        // then
        then(expenseRepository).should(times(3)).save(captor.capture());
        captor.getAllValues().forEach(expense ->
                assertThat(expense.isUserModified())
                        .as("모든 신규 Expense는 is_user_modified=false이어야 한다 — Expense.updateCategory()의 보호 조건 전제")
                        .isFalse()
        );
    }

    @Test
    @DisplayName("isUserModified=true인 Expense는 updateCategory() 호출 시 categoryId가 변경되지 않는다")
    void isUserModified_true인_Expense는_카테고리가_변경되지_않는다() {
        // ExpenseSaveService는 신규 저장만 수행하며 기존 Expense를 조회하여 덮어쓰지 않는다.
        // 이 테스트는 Expense 도메인의 불변 조건을 직접 검증한다.

        // given
        Expense userModifiedExpense = Expense.create(
                USER_ID, "0301", 6500L, "스타벅스",
                LocalDateTime.of(2024, 4, 1, 0, 0),
                5L, ClassifiedBy.RULE, 100
        );
        userModifiedExpense.updateCategoryByUser(9L); // 사용자가 문화/여가비(9)로 수정

        // when: 자동 재분류 시도 (is_user_modified=true이면 변경 불가)
        userModifiedExpense.updateCategory(1L, ClassifiedBy.AI, 80);

        // then
        assertThat(userModifiedExpense.getCategoryId())
                .as("isUserModified=true인 Expense는 updateCategory() 호출에도 categoryId가 변경되지 않아야 한다")
                .isEqualTo(9L);
        assertThat(userModifiedExpense.getClassifiedBy())
                .as("isUserModified=true인 Expense는 classifiedBy가 USER로 유지되어야 한다")
                .isEqualTo(ClassifiedBy.USER);
    }
}
