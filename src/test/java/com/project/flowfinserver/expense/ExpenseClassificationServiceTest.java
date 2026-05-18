package com.project.flowfinserver.expense;

// 테스트 대상: ExpenseClassificationService.classify(String merchantName, Long amount)
// — Rule 분류 성공 시 RULE 반환, 실패 시 GptClassificationService 위임

import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.dto.ClassificationResult;
import com.project.flowfinserver.service.ExpenseClassificationService;
import com.project.flowfinserver.service.ExpenseKeywordClassifier;
import com.project.flowfinserver.service.GptClassificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ExpenseClassificationServiceTest {

    @Mock
    ExpenseKeywordClassifier keywordClassifier;

    @Mock
    GptClassificationService gptClassificationService;

    @InjectMocks
    ExpenseClassificationService classificationService;

    private static final String MERCHANT = "스타벅스 강남점";
    private static final Long AMOUNT = 6500L;

    // ==================== Rule-based 분류 성공 ====================

    @Test
    @DisplayName("키워드 매핑 성공 시 classifiedBy가 RULE이다")
    void 키워드_매핑_성공_시_classifiedBy가_RULE이다() {
        // given
        given(keywordClassifier.classify(MERCHANT)).willReturn(5L);

        // when
        ClassificationResult result = classificationService.classify(MERCHANT, AMOUNT);

        // then
        assertThat(result.getClassifiedBy())
                .as("Rule 매핑 성공 시 classifiedBy는 RULE이어야 한다")
                .isEqualTo(ClassifiedBy.RULE);
    }

    @Test
    @DisplayName("키워드 매핑 성공 시 confidence가 100이다")
    void 키워드_매핑_성공_시_confidence가_100이다() {
        // given
        given(keywordClassifier.classify(MERCHANT)).willReturn(5L);

        // when
        ClassificationResult result = classificationService.classify(MERCHANT, AMOUNT);

        // then
        assertThat(result.getConfidence())
                .as("Rule 분류 결과의 confidence는 항상 100이어야 한다")
                .isEqualTo(100);
    }

    @Test
    @DisplayName("키워드 매핑 성공 시 categoryId가 분류기 반환값과 일치한다")
    void 키워드_매핑_성공_시_categoryId가_분류기_반환값과_일치한다() {
        // given
        given(keywordClassifier.classify(MERCHANT)).willReturn(5L);

        // when
        ClassificationResult result = classificationService.classify(MERCHANT, AMOUNT);

        // then
        assertThat(result.getCategoryId())
                .as("Rule 매핑된 categoryId가 그대로 반환되어야 한다")
                .isEqualTo(5L);
    }

    @Test
    @DisplayName("키워드 매핑 성공 시 GptClassificationService는 호출되지 않는다")
    void 키워드_매핑_성공_시_GPT는_호출되지_않는다() {
        // given
        given(keywordClassifier.classify(MERCHANT)).willReturn(5L);

        // when
        classificationService.classify(MERCHANT, AMOUNT);

        // then
        then(gptClassificationService).should(never()).classify(anyString(), anyLong());
    }

    // ==================== GPT fallback 위임 ====================

    @Test
    @DisplayName("키워드 매핑 실패(null) 시 GptClassificationService.classify()가 1회 호출된다")
    void 키워드_매핑_실패_시_GPT가_1회_호출된다() {
        // given
        given(keywordClassifier.classify(MERCHANT)).willReturn(null);
        given(gptClassificationService.classify(eq(MERCHANT), eq(AMOUNT)))
                .willReturn(ClassificationResult.ofFallback());

        // when
        classificationService.classify(MERCHANT, AMOUNT);

        // then
        then(gptClassificationService).should().classify(MERCHANT, AMOUNT);
    }

    @Test
    @DisplayName("키워드 매핑 실패 시 GptClassificationService의 반환값이 그대로 전달된다")
    void 키워드_매핑_실패_시_GPT_반환값이_그대로_전달된다() {
        // given
        ClassificationResult gptResult = ClassificationResult.ofAi(9L, 75);
        given(keywordClassifier.classify(MERCHANT)).willReturn(null);
        given(gptClassificationService.classify(eq(MERCHANT), eq(AMOUNT))).willReturn(gptResult);

        // when
        ClassificationResult result = classificationService.classify(MERCHANT, AMOUNT);

        // then
        assertThat(result)
                .as("GPT 반환값이 그대로 전달되어야 한다")
                .isSameAs(gptResult);
        assertThat(result.getCategoryId())
                .as("GPT가 반환한 categoryId가 유지되어야 한다")
                .isEqualTo(9L);
        assertThat(result.getClassifiedBy())
                .as("GPT가 반환한 classifiedBy(AI)가 유지되어야 한다")
                .isEqualTo(ClassifiedBy.AI);
        assertThat(result.getConfidence())
                .as("GPT가 반환한 confidence가 유지되어야 한다")
                .isEqualTo(75);
    }

    // ==================== ClassificationResult 팩토리 메서드 검증 ====================

    @Test
    @DisplayName("ClassificationResult.ofRule() — categoryId·classifiedBy·confidence가 올바르다")
    void ofRule_결과의_필드가_올바르다() {
        // when
        ClassificationResult result = ClassificationResult.ofRule(5L);

        // then
        assertThat(result.getCategoryId())
                .as("ofRule()의 categoryId는 입력값과 동일해야 한다")
                .isEqualTo(5L);
        assertThat(result.getClassifiedBy())
                .as("ofRule()의 classifiedBy는 RULE이어야 한다")
                .isEqualTo(ClassifiedBy.RULE);
        assertThat(result.getConfidence())
                .as("ofRule()의 confidence는 100이어야 한다")
                .isEqualTo(100);
    }

    @Test
    @DisplayName("ClassificationResult.ofFallback() — categoryId=11, classifiedBy=AI, confidence=0")
    void ofFallback_결과의_필드가_올바르다() {
        // when
        ClassificationResult result = ClassificationResult.ofFallback();

        // then
        assertThat(result.getCategoryId())
                .as("ofFallback()의 categoryId는 기타지출(11)이어야 한다")
                .isEqualTo(11L);
        assertThat(result.getClassifiedBy())
                .as("ofFallback()의 classifiedBy는 AI이어야 한다")
                .isEqualTo(ClassifiedBy.AI);
        assertThat(result.getConfidence())
                .as("ofFallback()의 confidence는 0이어야 한다")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("ClassificationResult.ofAi() — categoryId·confidence가 입력값과 일치한다")
    void ofAi_결과의_필드가_올바르다() {
        // when
        ClassificationResult result = ClassificationResult.ofAi(7L, 82);

        // then
        assertThat(result.getCategoryId())
                .as("ofAi()의 categoryId는 입력값과 동일해야 한다")
                .isEqualTo(7L);
        assertThat(result.getClassifiedBy())
                .as("ofAi()의 classifiedBy는 AI이어야 한다")
                .isEqualTo(ClassifiedBy.AI);
        assertThat(result.getConfidence())
                .as("ofAi()의 confidence는 입력값과 동일해야 한다")
                .isEqualTo(82);
    }
}
