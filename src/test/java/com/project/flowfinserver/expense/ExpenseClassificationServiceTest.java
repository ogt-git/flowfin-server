package com.project.flowfinserver.expense;

// 테스트 대상: ExpenseClassificationService.classify(String merchantName)
// — Rule 분류 성공 시 RULE 반환, 실패 시 PENDING 반환

import com.project.flowfinserver.domain.Category;
import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.dto.ClassificationResult;
import com.project.flowfinserver.repository.CategoryRepository;
import com.project.flowfinserver.service.ExpenseClassificationService;
import com.project.flowfinserver.service.ExpenseKeywordClassifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ExpenseClassificationServiceTest {

    @Mock
    ExpenseKeywordClassifier keywordClassifier;

    @Mock
    CategoryRepository categoryRepository;

    @InjectMocks
    ExpenseClassificationService classificationService;

    private static final String MERCHANT = "스타벅스 강남점";

    // ==================== Rule-based 분류 성공 ====================

    @Test
    @DisplayName("키워드 매핑 성공 시 classifiedBy가 RULE이다")
    void 키워드_매핑_성공_시_classifiedBy가_RULE이다() {
        Category cat5 = mock(Category.class);
        given(keywordClassifier.classify(MERCHANT)).willReturn(5L);
        given(categoryRepository.findById(5L)).willReturn(Optional.of(cat5));

        ClassificationResult result = classificationService.classify(MERCHANT);

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

        ClassificationResult result = classificationService.classify(MERCHANT);

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

        ClassificationResult result = classificationService.classify(MERCHANT);

        assertThat(result.getCategory())
                .as("Rule 매핑된 Category는 categoryRepository.findById(5L) 결과와 같아야 한다")
                .isSameAs(cat5);
    }

    // ==================== PENDING fallback ====================

    @Test
    @DisplayName("키워드 매핑 실패 시 classifiedBy가 PENDING이다")
    void 키워드_매핑_실패_시_classifiedBy가_PENDING이다() {
        given(keywordClassifier.classify(MERCHANT)).willReturn(null);

        ClassificationResult result = classificationService.classify(MERCHANT);

        assertThat(result.getClassifiedBy())
                .as("Rule 실패 시 classifiedBy는 PENDING이어야 한다 — 비동기 AI 분류 대기")
                .isEqualTo(ClassifiedBy.PENDING);
    }

    @Test
    @DisplayName("키워드 매핑 실패 시 category가 null이다")
    void 키워드_매핑_실패_시_category가_null이다() {
        given(keywordClassifier.classify(MERCHANT)).willReturn(null);

        ClassificationResult result = classificationService.classify(MERCHANT);

        assertThat(result.getCategory())
                .as("Rule 실패 시 category는 null이어야 한다 — 비동기 AI 분류로 채워짐")
                .isNull();
    }

    @Test
    @DisplayName("키워드 매핑 실패 시 confidence가 null이다")
    void 키워드_매핑_실패_시_confidence가_null이다() {
        given(keywordClassifier.classify(MERCHANT)).willReturn(null);

        ClassificationResult result = classificationService.classify(MERCHANT);

        assertThat(result.getConfidence())
                .as("Rule 실패 시 confidence는 null이어야 한다")
                .isNull();
    }

    // ==================== ClassificationResult 팩토리 메서드 검증 ====================

    @Test
    @DisplayName("ClassificationResult.ofRule() — Category·classifiedBy·confidence가 올바르다")
    void ofRule_결과의_필드가_올바르다() {
        Category cat5 = mock(Category.class);
        given(cat5.getId()).willReturn(5L);

        ClassificationResult result = ClassificationResult.ofRule(cat5);

        assertThat(result.getCategory().getId()).isEqualTo(5L);
        assertThat(result.getClassifiedBy()).isEqualTo(ClassifiedBy.RULE);
        assertThat(result.getConfidence()).isEqualTo(100);
    }

    @Test
    @DisplayName("ClassificationResult.pending() — classifiedBy=PENDING, category=null, confidence=null")
    void pending_결과의_필드가_올바르다() {
        ClassificationResult result = ClassificationResult.pending();

        assertThat(result.getClassifiedBy()).isEqualTo(ClassifiedBy.PENDING);
        assertThat(result.getCategory()).isNull();
        assertThat(result.getConfidence()).isNull();
    }

    @Test
    @DisplayName("ClassificationResult.ofFallback() — classifiedBy=AI, confidence=0")
    void ofFallback_결과의_필드가_올바르다() {
        Category fallbackCat = mock(Category.class);

        ClassificationResult result = ClassificationResult.ofFallback(fallbackCat);

        assertThat(result.getCategory()).isSameAs(fallbackCat);
        assertThat(result.getClassifiedBy()).isEqualTo(ClassifiedBy.AI);
        assertThat(result.getConfidence()).isEqualTo(0);
    }

    @Test
    @DisplayName("ClassificationResult.ofAi() — category·confidence가 입력값과 일치한다")
    void ofAi_결과의_필드가_올바르다() {
        Category cat7 = mock(Category.class);
        given(cat7.getId()).willReturn(7L);

        ClassificationResult result = ClassificationResult.ofAi(cat7, 82);

        assertThat(result.getCategory().getId()).isEqualTo(7L);
        assertThat(result.getClassifiedBy()).isEqualTo(ClassifiedBy.AI);
        assertThat(result.getConfidence()).isEqualTo(82);
    }
}
