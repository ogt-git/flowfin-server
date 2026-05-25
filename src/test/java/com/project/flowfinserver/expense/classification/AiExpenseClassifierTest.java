package com.project.flowfinserver.expense.classification;

// 테스트 대상: AiExpenseClassifier.classifyAndUpdate(Long expenseId)
// — 캐시 히트·미스, isUserModified 보호, 신뢰도·범위 fallback, 재시도 실패 처리

import com.project.flowfinserver.cache.ExpenseStatsCacheManager;
import com.project.flowfinserver.domain.Category;
import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.domain.Expense;
import com.project.flowfinserver.dto.ClassificationResult;
import com.project.flowfinserver.exception.OpenAiClassificationException;
import com.project.flowfinserver.openai.AiExpenseClassifier;
import com.project.flowfinserver.openai.OpenAiClassificationClient;
import com.project.flowfinserver.repository.CategoryRepository;
import com.project.flowfinserver.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiExpenseClassifierTest {

    @Mock ExpenseRepository expenseRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock OpenAiClassificationClient openAiClient;
    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock ExpenseStatsCacheManager expenseStatsCacheManager;

    @InjectMocks
    AiExpenseClassifier classifier;

    private static final Long EXPENSE_ID = 1L;
    private static final Long FALLBACK_ID = 11L;

    private Expense pendingExpense;
    private Category cat5;
    private Category fallbackCat;

    @BeforeEach
    void setUp() {
        pendingExpense = Expense.create(1L, "0301", 6500L, "스타벅스",
                LocalDateTime.of(2024, 4, 1, 0, 0), null, ClassifiedBy.PENDING, null);

        cat5 = mock(Category.class);
        given(cat5.getId()).willReturn(5L);

        fallbackCat = mock(Category.class);
        given(fallbackCat.getId()).willReturn(FALLBACK_ID);

        given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
    }

    // ==================== isUserModified 보호 ====================

    @Test
    @DisplayName("isUserModified=true인 Expense는 AI 분류를 차단한다")
    void isUserModified_true이면_AI_분류를_차단한다() {
        Category any = mock(Category.class);
        Expense userModified = Expense.create(1L, "0301", 6500L, "스타벅스",
                LocalDateTime.of(2024, 4, 1, 0, 0), any, ClassifiedBy.RULE, 100);
        userModified.updateCategoryByUser(any);

        given(expenseRepository.findById(EXPENSE_ID)).willReturn(Optional.of(userModified));

        classifier.classifyAndUpdate(EXPENSE_ID);

        then(openAiClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("classifiedBy가 PENDING이 아닌 Expense는 AI 분류를 차단한다")
    void classifiedBy가_PENDING_아니면_AI_분류를_차단한다() {
        Category any = mock(Category.class);
        Expense alreadyClassified = Expense.create(1L, "0301", 6500L, "스타벅스",
                LocalDateTime.of(2024, 4, 1, 0, 0), any, ClassifiedBy.RULE, 100);

        given(expenseRepository.findById(EXPENSE_ID)).willReturn(Optional.of(alreadyClassified));

        classifier.classifyAndUpdate(EXPENSE_ID);

        then(openAiClient).shouldHaveNoInteractions();
    }

    // ==================== Redis 캐시 히트 ====================

    @Test
    @DisplayName("캐시 히트 시 OpenAI 호출 없이 cached categoryId로 업데이트한다")
    void 캐시_히트_시_OpenAI_호출_없이_업데이트한다() {
        given(expenseRepository.findById(EXPENSE_ID))
                .willReturn(Optional.of(pendingExpense))
                .willReturn(Optional.of(pendingExpense));
        given(valueOps.get(anyString())).willReturn("5:85");
        given(categoryRepository.findById(5L)).willReturn(Optional.of(cat5));

        classifier.classifyAndUpdate(EXPENSE_ID);

        then(openAiClient).shouldHaveNoInteractions();
        assertThat(pendingExpense.getClassifiedBy()).isEqualTo(ClassifiedBy.AI);
        assertThat(pendingExpense.getCategoryConfidence()).isEqualTo(85);
    }

    // ==================== OpenAI 호출 ====================

    @Test
    @DisplayName("캐시 미스 시 OpenAI를 호출한다")
    void 캐시_미스_시_OpenAI를_호출한다() {
        given(expenseRepository.findById(EXPENSE_ID))
                .willReturn(Optional.of(pendingExpense))
                .willReturn(Optional.of(pendingExpense));
        given(valueOps.get(anyString())).willReturn(null);
        given(openAiClient.classify(anyString(), anyLong()))
                .willReturn(ClassificationResult.ofAi(cat5, 85));

        classifier.classifyAndUpdate(EXPENSE_ID);

        then(openAiClient).should().classify(anyString(), anyLong());
        assertThat(pendingExpense.getClassifiedBy()).isEqualTo(ClassifiedBy.AI);
        assertThat(pendingExpense.getCategoryConfidence()).isEqualTo(85);
    }

    @Test
    @DisplayName("취소거래(음수 amount)는 Math.abs로 양수 변환 후 OpenAI에 전달한다")
    void 취소거래_음수_amount는_양수로_변환해서_전달한다() {
        Expense cancelExpense = Expense.create(1L, "0301", -6500L, "스타벅스",
                LocalDateTime.of(2024, 4, 1, 0, 0), null, ClassifiedBy.PENDING, null);

        given(expenseRepository.findById(EXPENSE_ID))
                .willReturn(Optional.of(cancelExpense))
                .willReturn(Optional.of(cancelExpense));
        given(valueOps.get(anyString())).willReturn(null);
        given(openAiClient.classify(anyString(), eq(6500L)))
                .willReturn(ClassificationResult.ofAi(cat5, 85));

        classifier.classifyAndUpdate(EXPENSE_ID);

        then(openAiClient).should().classify(anyString(), eq(6500L));
    }

    // ==================== 재시도 실패 fallback ====================

    @Test
    @DisplayName("OpenAI 3회 재시도 후 실패 시 기타지출(11)·confidence=0으로 저장한다")
    void OpenAI_최종_실패_시_기타지출로_저장한다() {
        given(expenseRepository.findById(EXPENSE_ID))
                .willReturn(Optional.of(pendingExpense))
                .willReturn(Optional.of(pendingExpense));
        given(valueOps.get(anyString())).willReturn(null);
        given(openAiClient.classify(anyString(), anyLong()))
                .willThrow(new OpenAiClassificationException("OpenAI 최종 실패"));
        given(categoryRepository.findById(FALLBACK_ID)).willReturn(Optional.of(fallbackCat));

        classifier.classifyAndUpdate(EXPENSE_ID);

        assertThat(pendingExpense.getClassifiedBy())
                .as("최종 실패 시 classifiedBy는 AI이어야 한다")
                .isEqualTo(ClassifiedBy.AI);
        assertThat(pendingExpense.getCategoryConfidence())
                .as("최종 실패 시 confidence는 0이어야 한다")
                .isEqualTo(0);
        assertThat(pendingExpense.getCategory())
                .as("최종 실패 시 카테고리는 기타지출(11)이어야 한다")
                .isSameAs(fallbackCat);
    }

    // ==================== race condition 최종 방어 ====================

    @Test
    @DisplayName("AI 응답 적용 직전 isUserModified=true로 변경되면 UPDATE를 차단한다")
    void AI응답_적용_직전_isUserModified_변경_시_UPDATE_차단한다() {
        Category any = mock(Category.class);
        // 첫 조회: PENDING (AI 분류 진행)
        Expense firstLoad = Expense.create(1L, "0301", 6500L, "스타벅스",
                LocalDateTime.of(2024, 4, 1, 0, 0), null, ClassifiedBy.PENDING, null);
        // 두 번째 조회: 사용자가 그 사이 수정 → isUserModified=true
        Expense secondLoad = Expense.create(1L, "0301", 6500L, "스타벅스",
                LocalDateTime.of(2024, 4, 1, 0, 0), any, ClassifiedBy.RULE, 100);
        secondLoad.updateCategoryByUser(any);

        given(expenseRepository.findById(EXPENSE_ID))
                .willReturn(Optional.of(firstLoad))
                .willReturn(Optional.of(secondLoad));
        given(valueOps.get(anyString())).willReturn(null);
        given(openAiClient.classify(anyString(), anyLong()))
                .willReturn(ClassificationResult.ofAi(cat5, 85));

        classifier.classifyAndUpdate(EXPENSE_ID);

        // 첫 번째 로드(firstLoad)는 PENDING → 진행
        // 두 번째 로드(secondLoad)는 isUserModified=true → UPDATE 차단
        assertThat(secondLoad.getClassifiedBy())
                .as("race condition: 두 번째 조회 시 USER로 유지되어야 한다")
                .isEqualTo(ClassifiedBy.USER);
    }
}
