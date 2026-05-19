package com.project.flowfinserver.expense;

// 테스트 대상: ExpenseClassificationService.classify(String merchantName, Long amount)
// — Rule 분류 성공 시 RULE 반환, 실패 시 GptClassificationService 위임

import com.project.flowfinserver.domain.Category;
import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.dto.ClassificationResult;
import com.project.flowfinserver.repository.CategoryRepository;
import com.project.flowfinserver.service.ExpenseClassificationService;
import com.project.flowfinserver.service.ExpenseKeywordClassifier;
import com.project.flowfinserver.service.GptClassificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ExpenseClassificationServiceTest {

    @Mock
    ExpenseKeywordClassifier keywordClassifier;

    @Mock
    GptClassificationService gptClassificationService;

    @Mock
    CategoryRepository categoryRepository;

    @InjectMocks
    ExpenseClassificationService classificationService;

    private static final String MERCHANT = "스타벅스 강남점";
    private static final Long AMOUNT = 6500L;

    // ==================== Rule-based 분류 성공 ====================

    @Test
    @DisplayName("키워드 매핑 성공 시 classifiedBy가 RULE이다")
    void 키워드_매핑_성공_시_classifiedBy가_RULE이다() {
        Category cat5 = mock(Category.class);
        given(keywordClassifier.classify(MERCHANT)).willReturn(5L);
        given(categoryRepository.findById(5L)).willReturn(Optional.of(cat5));

        ClassificationResult result = classificationService.classify(MERCHANT, AMOUNT);

        assertThat(result.getClassifiedBy())
                .as("Rule 매핑 성공 시 classifiedBy는 RULE이어야 한다")
                .isEqualTo(ClassifiedBy.RULE);
    }

    @Test
    @DisplayName("키워드 매핑 성공 시 confidence가 100이다")
    void 키워드_매핑_성공_시_confidence가_100이다() {
        Category cat5 = mock(Category.class);
        given(keywordClassifier.classify(MERCHANT)).willReturn(5L);
        given(categoryRepository.findById(5L)).willReturn(Optional.of(cat5));

        ClassificationResult result = classificationService.classify(MERCHANT, AMOUNT);

        assertThat(result.getConfidence())
                .as("Rule 분류 결과의 confidence는 항상 100이어야 한다")
                .isEqualTo(100);
    }

    @Test
    @DisplayName("키워드 매핑 성공 시 반환된 Category 객체가 DB 조회 결과와 동일하다")
    void 키워드_매핑_성공_시_Category가_DB조회_결과와_일치한다() {
        Category cat5 = mock(Category.class);
        given(keywordClassifier.classify(MERCHANT)).willReturn(5L);
        given(categoryRepository.findById(5L)).willReturn(Optional.of(cat5));

        ClassificationResult result = classificationService.classify(MERCHANT, AMOUNT);

        assertThat(result.getCategory())
                .as("Rule 매핑된 Category는 categoryRepository.findById(5L) 결과와 같아야 한다")
                .isSameAs(cat5);
    }

    @Test
    @DisplayName("키워드 매핑 성공 시 GptClassificationService는 호출되지 않는다")
    void 키워드_매핑_성공_시_GPT는_호출되지_않는다() {
        Category cat5 = mock(Category.class);
        given(keywordClassifier.classify(MERCHANT)).willReturn(5L);
        given(categoryRepository.findById(5L)).willReturn(Optional.of(cat5));

        classificationService.classify(MERCHANT, AMOUNT);

        then(gptClassificationService).should(never()).classify(anyString(), anyLong());
    }

    // ==================== GPT fallback 위임 ====================

    @Test
    @DisplayName("키워드 매핑 실패(null) 시 GptClassificationService.classify()가 1회 호출된다")
    void 키워드_매핑_실패_시_GPT가_1회_호출된다() {
        Category fallback = mock(Category.class);
        given(keywordClassifier.classify(MERCHANT)).willReturn(null);
        given(gptClassificationService.classify(eq(MERCHANT), eq(AMOUNT)))
                .willReturn(ClassificationResult.ofFallback(fallback));

        classificationService.classify(MERCHANT, AMOUNT);

        then(gptClassificationService).should().classify(MERCHANT, AMOUNT);
    }

    @Test
    @DisplayName("키워드 매핑 실패 시 GptClassificationService의 반환값이 그대로 전달된다")
    void 키워드_매핑_실패_시_GPT_반환값이_그대로_전달된다() {
        Category cat9 = mock(Category.class);
        ClassificationResult gptResult = ClassificationResult.ofAi(cat9, 75);
        given(keywordClassifier.classify(MERCHANT)).willReturn(null);
        given(gptClassificationService.classify(eq(MERCHANT), eq(AMOUNT))).willReturn(gptResult);

        ClassificationResult result = classificationService.classify(MERCHANT, AMOUNT);

        assertThat(result)
                .as("GPT 반환값이 그대로 전달되어야 한다")
                .isSameAs(gptResult);
        assertThat(result.getCategory())
                .as("GPT가 반환한 Category가 유지되어야 한다")
                .isSameAs(cat9);
        assertThat(result.getClassifiedBy())
                .as("GPT가 반환한 classifiedBy(AI)가 유지되어야 한다")
                .isEqualTo(ClassifiedBy.AI);
        assertThat(result.getConfidence())
                .as("GPT가 반환한 confidence가 유지되어야 한다")
                .isEqualTo(75);
    }

    // ==================== ClassificationResult 팩토리 메서드 검증 ====================

    @Test
    @DisplayName("ClassificationResult.ofRule() — Category·classifiedBy·confidence가 올바르다")
    void ofRule_결과의_필드가_올바르다() {
        Category cat5 = mock(Category.class);
        given(cat5.getId()).willReturn(5L);

        ClassificationResult result = ClassificationResult.ofRule(cat5);

        assertThat(result.getCategory().getId())
                .as("ofRule()의 categoryId는 입력 Category의 id와 동일해야 한다")
                .isEqualTo(5L);
        assertThat(result.getClassifiedBy())
                .as("ofRule()의 classifiedBy는 RULE이어야 한다")
                .isEqualTo(ClassifiedBy.RULE);
        assertThat(result.getConfidence())
                .as("ofRule()의 confidence는 100이어야 한다")
                .isEqualTo(100);
    }

    @Test
    @DisplayName("ClassificationResult.ofFallback() — classifiedBy=AI, confidence=0")
    void ofFallback_결과의_필드가_올바르다() {
        Category fallbackCat = mock(Category.class);

        ClassificationResult result = ClassificationResult.ofFallback(fallbackCat);

        assertThat(result.getCategory())
                .as("ofFallback()의 category는 전달된 fallbackCategory이어야 한다")
                .isSameAs(fallbackCat);
        assertThat(result.getClassifiedBy())
                .as("ofFallback()의 classifiedBy는 AI이어야 한다")
                .isEqualTo(ClassifiedBy.AI);
        assertThat(result.getConfidence())
                .as("ofFallback()의 confidence는 0이어야 한다")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("ClassificationResult.ofAi() — category·confidence가 입력값과 일치한다")
    void ofAi_결과의_필드가_올바르다() {
        Category cat7 = mock(Category.class);
        given(cat7.getId()).willReturn(7L);

        ClassificationResult result = ClassificationResult.ofAi(cat7, 82);

        assertThat(result.getCategory().getId())
                .as("ofAi()의 categoryId는 입력 Category의 id와 동일해야 한다")
                .isEqualTo(7L);
        assertThat(result.getClassifiedBy())
                .as("ofAi()의 classifiedBy는 AI이어야 한다")
                .isEqualTo(ClassifiedBy.AI);
        assertThat(result.getConfidence())
                .as("ofAi()의 confidence는 입력값과 동일해야 한다")
                .isEqualTo(82);
    }
}
