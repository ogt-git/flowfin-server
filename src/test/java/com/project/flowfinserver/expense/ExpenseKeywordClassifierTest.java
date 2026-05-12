package com.project.flowfinserver.expense;

// 테스트 대상: ExpenseKeywordClassifier.classify(String merchantName)
// — @PostConstruct 호출 없이 reloadRules()로 수동 초기화 후 in-memory 분류 로직 검증

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

    /**
     * 픽스처 규칙:
     * EXACT   "KFC"      → 5L (식비)
     * EXACT   "스타벅스"  → 5L (식비)
     * EXACT   "넷플릭스"  → 9L (문화/여가비)  ← CONTAINS "넷플릭스"(5L)와 충돌 테스트용
     * CONTAINS "이마트"   → 6L (생활비) priority=90
     * CONTAINS "병원"     → 10L(의료비) priority=90
     * CONTAINS "카페"     → 5L (식비)   priority=85
     * CONTAINS "넷플릭스" → 5L (식비)   priority=90  ← EXACT가 우선해야 함
     * CONTAINS "마트"     → 7L (교통비 — 테스트용 임의값) priority=80
     *
     * "이마트 서울점"은 "이마트"(90)와 "마트"(80) 모두 CONTAINS 매칭되므로 priority 우선순위 검증에 사용
     */
    @BeforeEach
    void setUp() {
        // given
        given(ruleRepository.findAllByOrderByPriorityDesc()).willReturn(buildTestRules());
        // @PostConstruct는 Mockito가 호출하지 않으므로 수동으로 캐시를 초기화한다
        classifier.reloadRules();
    }

    private List<MerchantCategoryRule> buildTestRules() {
        // findAllByOrderByPriorityDesc() 반환값이므로 CONTAINS 규칙은 priority 내림차순으로 배치
        return List.of(
                rule("KFC",       5L, MatchType.EXACT,    100),
                rule("스타벅스",   5L, MatchType.EXACT,    100),
                rule("넷플릭스",   9L, MatchType.EXACT,    100),
                rule("이마트",     6L, MatchType.CONTAINS,  90),
                rule("병원",      10L, MatchType.CONTAINS,  90),
                rule("넷플릭스",   5L, MatchType.CONTAINS,  90), // EXACT 충돌 테스트용
                rule("카페",       5L, MatchType.CONTAINS,  85),
                rule("마트",       7L, MatchType.CONTAINS,  80)
        );
    }

    private MerchantCategoryRule rule(String keyword, Long categoryId, MatchType matchType, int priority) {
        return MerchantCategoryRule.builder()
                .keyword(keyword)
                .categoryId(categoryId)
                .matchType(matchType)
                .priority(priority)
                .build();
    }

    // ==================== EXACT 매칭 ====================

    @Test
    @DisplayName("EXACT 등록 브랜드명을 완전 일치로 입력하면 해당 categoryId를 반환한다")
    void EXACT로_등록된_브랜드명을_정확히_입력하면_해당_categoryId를_반환한다() {
        // when
        Long result = classifier.classify("KFC");

        // then
        assertThat(result)
                .as("EXACT 키워드 'KFC'는 식비(5)로 분류되어야 한다")
                .isEqualTo(5L);
    }

    @Test
    @DisplayName("EXACT 매칭은 대소문자를 구분한다 — 소문자 입력은 매칭 실패")
    void EXACT_매칭은_대소문자를_구분한다() {
        // 구현 확인: HashMap.get()은 대소문자를 구분하므로 "kfc" ≠ "KFC"

        // when
        Long result = classifier.classify("kfc");

        // then
        assertThat(result)
                .as("'kfc'(소문자)는 EXACT 규칙 'KFC'에 매칭되지 않아야 한다 — 구현이 case-sensitive임을 문서화")
                .isNull();
    }

    // ==================== CONTAINS 매칭 ====================

    @Test
    @DisplayName("가맹점명에 CONTAINS 키워드가 포함되면 해당 categoryId를 반환한다")
    void CONTAINS_키워드가_가맹점명에_포함되면_해당_categoryId를_반환한다() {
        // when
        Long result = classifier.classify("강남병원");

        // then
        assertThat(result)
                .as("'강남병원'은 CONTAINS 키워드 '병원'에 매칭되어 의료비(10)로 분류되어야 한다")
                .isEqualTo(10L);
    }

    @Test
    @DisplayName("지점명이 앞뒤에 붙어 있어도 CONTAINS 키워드가 포함되면 매칭된다")
    void 지점명이_붙어있어도_CONTAINS_키워드가_포함되면_매칭된다() {
        // '스타벅스 강남점' — EXACT "스타벅스"가 없는 경우를 재현하기 위해
        // 스타벅스는 픽스처에서 EXACT로 등록되어 있으므로, "카페"(CONTAINS/85) 규칙으로 테스트

        // when
        Long result = classifier.classify("할리스카페 홍대점");

        // then
        assertThat(result)
                .as("'할리스카페 홍대점'은 CONTAINS 키워드 '카페'를 포함하므로 식비(5)로 분류되어야 한다")
                .isEqualTo(5L);
    }

    // ==================== 우선순위 ====================

    @Test
    @DisplayName("동일 가맹점명에 EXACT와 CONTAINS가 모두 매칭될 때 EXACT가 우선 적용된다")
    void EXACT와_CONTAINS가_모두_매칭될_때_EXACT가_우선_적용된다() {
        // 픽스처: EXACT "넷플릭스"→9L, CONTAINS "넷플릭스"→5L
        // "넷플릭스"는 양쪽 모두 매칭되지만 EXACT Map 조회가 먼저 수행된다

        // when
        Long result = classifier.classify("넷플릭스");

        // then
        assertThat(result)
                .as("EXACT '넷플릭스'(9L)가 CONTAINS '넷플릭스'(5L)보다 우선 적용되어야 한다")
                .isEqualTo(9L);
    }

    @Test
    @DisplayName("priority가 높은 CONTAINS 규칙이 낮은 규칙보다 먼저 적용된다")
    void priority가_높은_CONTAINS_규칙이_먼저_적용된다() {
        // "이마트 왕십리점"은 "이마트"(priority=90)와 "마트"(priority=80) 두 키워드 모두 포함한다
        // 올바른 동작: priority=90인 "이마트"가 먼저 매칭되어 6L(생활비) 반환
        // 잘못된 동작: priority=80인 "마트"가 먼저 매칭되면 7L 반환

        // when
        Long result = classifier.classify("이마트 왕십리점");

        // then
        assertThat(result)
                .as("'이마트 왕십리점'은 '이마트'(priority=90)가 '마트'(priority=80)보다 먼저 적용되어 6L이어야 한다")
                .isEqualTo(6L);
    }

    // ==================== 매칭 실패 ====================

    @Test
    @DisplayName("등록되지 않은 가맹점명은 null을 반환한다")
    void 등록되지_않은_가맹점명은_null을_반환한다() {
        // when
        Long result = classifier.classify("알수없는가맹점xyz");

        // then
        assertThat(result)
                .as("규칙에 없는 가맹점명은 null을 반환해야 한다")
                .isNull();
    }

    @Test
    @DisplayName("merchantName이 null이면 null을 반환한다 — NPE 없이 처리")
    void merchantName이_null이면_null을_반환한다() {
        // when / then
        assertThat(classifier.classify(null))
                .as("null 입력 시 NPE 없이 null을 반환해야 한다")
                .isNull();
    }

    @Test
    @DisplayName("merchantName이 빈 문자열이면 null을 반환한다")
    void merchantName이_빈_문자열이면_null을_반환한다() {
        // when / then
        assertThat(classifier.classify(""))
                .as("빈 문자열 입력 시 null을 반환해야 한다")
                .isNull();

        assertThat(classifier.classify("   "))
                .as("공백만 있는 문자열 입력 시 null을 반환해야 한다")
                .isNull();
    }
}
