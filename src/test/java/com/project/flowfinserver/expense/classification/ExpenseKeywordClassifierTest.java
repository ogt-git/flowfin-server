package com.project.flowfinserver.expense.classification;

// 테스트 대상: ExpenseKeywordClassifier.classify(String merchantName)
// — EXACT 매칭 우선, CONTAINS priority 정렬, 매칭 실패 시 null, 정규화 동작

import com.project.flowfinserver.domain.MatchType;
import com.project.flowfinserver.domain.MerchantCategoryRule;
import com.project.flowfinserver.repository.MerchantCategoryRuleRepository;
import com.project.flowfinserver.service.ExpenseKeywordClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ExpenseKeywordClassifierTest {

    @Mock
    MerchantCategoryRuleRepository ruleRepository;

    @InjectMocks
    ExpenseKeywordClassifier classifier;

    private MerchantCategoryRule exactMart;
    private MerchantCategoryRule containsMart;
    private MerchantCategoryRule containsStarbucks;

    @BeforeEach
    void setUp() {
        // EXACT: "이마트24" → 식비(5)
        exactMart = MerchantCategoryRule.builder()
                .keyword("이마트24").categoryId(5L).matchType(MatchType.EXACT).priority(100).build();

        // CONTAINS: "이마트" → 생활비(6), priority=90
        containsMart = MerchantCategoryRule.builder()
                .keyword("이마트").categoryId(6L).matchType(MatchType.CONTAINS).priority(90).build();

        // CONTAINS: "스타벅스" → 식비(5), priority=80
        containsStarbucks = MerchantCategoryRule.builder()
                .keyword("스타벅스").categoryId(5L).matchType(MatchType.CONTAINS).priority(80).build();

        given(ruleRepository.findAllByOrderByPriorityDesc())
                .willReturn(List.of(exactMart, containsMart, containsStarbucks));
        classifier.reloadRules();
    }

    // ==================== EXACT 매칭 우선순위 ====================

    @Test
    @DisplayName("EXACT 매칭 성공 시 해당 categoryId를 반환한다")
    void EXACT_매칭_성공_시_categoryId를_반환한다() {
        Long result = classifier.classify("이마트24");

        assertThat(result)
                .as("EXACT 매칭된 카테고리 ID는 5(식비)이어야 한다")
                .isEqualTo(5L);
    }

    @Test
    @DisplayName("이마트24(EXACT=식비)와 이마트(CONTAINS=생활비)가 모두 있을 때 EXACT가 우선 적용된다")
    void 이마트24는_EXACT가_CONTAINS보다_우선_적용된다() {
        // "이마트24"는 EXACT(식비=5)와 CONTAINS(생활비=6) 모두 매칭될 수 있으나 EXACT 우선
        Long result = classifier.classify("이마트24");

        assertThat(result)
                .as("EXACT 매칭이 CONTAINS보다 우선해야 한다 — 이마트24 → 식비(5)")
                .isEqualTo(5L);
    }

    // ==================== CONTAINS priority 정렬 ====================

    @Test
    @DisplayName("EXACT 실패 후 CONTAINS 매칭 성공 시 해당 categoryId를 반환한다")
    void EXACT_실패_후_CONTAINS_매칭된_categoryId를_반환한다() {
        Long result = classifier.classify("이마트 성수점");

        assertThat(result)
                .as("이마트 CONTAINS 매칭 — 생활비(6)이어야 한다")
                .isEqualTo(6L);
    }

    @Test
    @DisplayName("CONTAINS 규칙이 여러 개 매칭될 때 priority 높은 규칙이 먼저 적용된다")
    void CONTAINS_priority_높은_규칙이_우선_적용된다() {
        // priority=90(이마트=6)과 priority=80(스타벅스=5) 중 "이마트 스타벅스"라는 가맹점은 이마트(90)가 먼저
        MerchantCategoryRule highPriority = MerchantCategoryRule.builder()
                .keyword("이마트").categoryId(6L).matchType(MatchType.CONTAINS).priority(90).build();
        MerchantCategoryRule lowPriority = MerchantCategoryRule.builder()
                .keyword("스타벅스").categoryId(5L).matchType(MatchType.CONTAINS).priority(80).build();

        given(ruleRepository.findAllByOrderByPriorityDesc())
                .willReturn(List.of(highPriority, lowPriority));
        classifier.reloadRules();

        Long result = classifier.classify("이마트 스타벅스");

        assertThat(result)
                .as("priority가 더 높은 이마트(90) 규칙이 먼저 적용되어야 한다")
                .isEqualTo(6L);
    }

    // ==================== 매칭 실패 ====================

    @Test
    @DisplayName("EXACT·CONTAINS 모두 실패 시 null을 반환한다")
    void 매칭_실패_시_null을_반환한다() {
        Long result = classifier.classify("알수없는가맹점XYZ");

        assertThat(result)
                .as("매칭 실패 시 null이 반환되어야 한다 — AI 분류 대상")
                .isNull();
    }

    @Test
    @DisplayName("merchantName이 null일 때 null을 반환한다")
    void merchantName이_null이면_null을_반환한다() {
        assertThat(classifier.classify(null)).isNull();
    }

    @Test
    @DisplayName("merchantName이 공백일 때 null을 반환한다")
    void merchantName이_공백이면_null을_반환한다() {
        assertThat(classifier.classify("   ")).isNull();
    }

    // ==================== 정규화 ====================

    @Test
    @DisplayName("EXACT 매칭은 공백 정규화 없이 완전 일치만 허용한다")
    void EXACT_매칭은_완전_일치만_허용한다() {
        // "이마트24" 앞에 공백이 붙으면 EXACT 불일치 → CONTAINS 시도
        Long result = classifier.classify(" 이마트24");

        assertThat(result)
                .as("앞에 공백이 있으면 EXACT 불일치 — CONTAINS로 fallback (이마트=생활비=6)")
                .isEqualTo(6L);
    }
}
