package com.project.flowfinserver.service;

// 테스트 대상: ExpenseKeywordClassifier.classify(String merchantName)
// 두 관점을 @Nested로 분리:
//   입력정규화_및_공백처리 — null/빈 문자열, 공백 포함 EXACT 불일치, 대소문자 구분
//   매칭로직_및_규칙       — EXACT/CONTAINS 매칭, EXACT 우선순위, CONTAINS priority 정렬

import com.project.flowfinserver.domain.MatchType;
import com.project.flowfinserver.domain.MerchantCategoryRule;
import com.project.flowfinserver.repository.MerchantCategoryRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
     * 통합 픽스처 (두 파일의 합집합):
     * EXACT   "이마트24"   → 5L  — 공백 포함 EXACT 불일치 / CONTAINS fallback 검증용
     * EXACT   "KFC"        → 5L  — 대소문자 구분 검증용
     * EXACT   "스타벅스"    → 5L
     * EXACT   "넷플릭스"    → 9L  — EXACT vs CONTAINS 동일 키워드 충돌 검증용
     * CONTAINS "이마트"     → 6L  p=90
     * CONTAINS "강남병원"   → 10L p=90
     * CONTAINS "넷플릭스"   → 5L  p=90  ← EXACT(9L)와 충돌
     * CONTAINS "카페"       → 5L  p=85
     * CONTAINS "마트"       → 7L  p=80  ← "이마트 왕십리점" priority 정렬 검증용
     */
    @BeforeEach
    void setUp() {
        given(ruleRepository.findAllByOrderByPriorityDesc()).willReturn(List.of(
                rule("이마트24",  5L, MatchType.EXACT,    100),
                rule("KFC",       5L, MatchType.EXACT,    100),
                rule("스타벅스",   5L, MatchType.EXACT,    100),
                rule("넷플릭스",   9L, MatchType.EXACT,    100),
                rule("이마트",     6L, MatchType.CONTAINS,  90),
                rule("강남병원",  10L, MatchType.CONTAINS,  90),
                rule("넷플릭스",   5L, MatchType.CONTAINS,  90),
                rule("카페",       5L, MatchType.CONTAINS,  85),
                rule("마트",       7L, MatchType.CONTAINS,  80)
        ));
        // @PostConstruct는 Mockito가 호출하지 않으므로 수동으로 캐시를 초기화한다
        classifier.reloadRules();
    }

    private MerchantCategoryRule rule(String keyword, Long categoryId, MatchType matchType, int priority) {
        return MerchantCategoryRule.builder()
                .keyword(keyword)
                .categoryId(categoryId)
                .matchType(matchType)
                .priority(priority)
                .build();
    }

    // ================================================================
    @Nested
    @DisplayName("입력 정규화 및 공백 처리")
    class 입력정규화_및_공백처리 {

        @Test
        @DisplayName("merchantName이 null이면 null을 반환한다")
        void merchantName이_null이면_null을_반환한다() {
            assertThat(classifier.classify(null))
                    .as("null 입력 시 NPE 없이 null을 반환해야 한다")
                    .isNull();
        }

        @Test
        @DisplayName("merchantName이 빈 문자열 또는 공백 문자열이면 null을 반환한다")
        void merchantName이_빈_문자열이면_null을_반환한다() {
            assertThat(classifier.classify(""))
                    .as("빈 문자열 입력 시 null을 반환해야 한다")
                    .isNull();

            assertThat(classifier.classify("   "))
                    .as("공백만 있는 문자열 입력 시 null을 반환해야 한다")
                    .isNull();
        }

        @Test
        @DisplayName("앞뒤 공백이 있어도 normalize trim으로 제거되어 EXACT 매칭된다")
        void 앞뒤_공백은_normalize_trim으로_제거되어_EXACT_매칭된다() {
            // normalize: " 이마트24".trim() = "이마트24" → EXACT "이마트24" 매칭
            MerchantCategoryRule result = classifier.classify(" 이마트24");

            assertThat(result).isNotNull();
            assertThat(result.getCategoryId())
                    .as("앞에 공백이 있어도 normalize trim 후 EXACT '이마트24'(식비=5)에 매칭되어야 한다")
                    .isEqualTo(5L);
        }

        @Test
        @DisplayName("대소문자가 달라도 normalize toLowerCase로 EXACT 매칭된다")
        void 대소문자가_달라도_normalize_toLowerCase로_EXACT_매칭된다() {
            // normalize: "kfc".toLowerCase() = "kfc", 규칙 "KFC" 키도 "kfc" → 매칭
            MerchantCategoryRule result = classifier.classify("kfc");

            assertThat(result).isNotNull();
            assertThat(result.getCategoryId())
                    .as("소문자 'kfc'는 normalize 후 EXACT 규칙 'KFC'(식비=5)에 매칭되어야 한다")
                    .isEqualTo(5L);
        }
    }

    // ================================================================
    @Nested
    @DisplayName("normalize 강화 — 법인표기·괄호·언더스코어 처리")
    class normalize_강화 {

        @Test
        @DisplayName("(주) 법인 표기가 앞에 붙은 경우 제거된다")
        void 법인표기_앞에_붙은_경우_제거된다() {
            assertThat(classifier.normalize("(주)교보문고")).isEqualTo("교보문고");
        }

        @Test
        @DisplayName("지점명은 제거하지 않고 원형 보존한다")
        void 지점명은_제거하지_않는다() {
            assertThat(classifier.normalize("투썸플레이스 응암역점")).isEqualTo("투썸플레이스 응암역점");
        }

        @Test
        @DisplayName("영문+언더스코어+(주)+한글법인명 혼합 가맹점명을 정규화한다")
        void 영문_언더스코어_법인명_혼합을_정규화한다() {
            assertThat(classifier.normalize("KFC KOREA_(주)케이에프씨코리아"))
                    .isEqualTo("kfc korea 케이에프씨코리아");
        }

        @Test
        @DisplayName("중간에 (주)가 끼인 경우 제거된다")
        void 중간에_끼인_법인표기가_제거된다() {
            assertThat(classifier.normalize("롯데쇼핑(주)롯데마트 은평점"))
                    .isEqualTo("롯데쇼핑롯데마트 은평점");
        }

        @Test
        @DisplayName("괄호 안 영문 약어가 있는 경우 제거된다")
        void 괄호_안_영문_약어가_제거된다() {
            assertThat(classifier.normalize("지에스(GS)25 파주방축점"))
                    .isEqualTo("지에스25 파주방축점");
        }

        @Test
        @DisplayName("언더스코어와 괄호 내용이 모두 정규화된다")
        void 언더스코어와_괄호_내용이_모두_정규화된다() {
            assertThat(classifier.normalize("코페이_키오스크(영세)_빠삭튀김"))
                    .isEqualTo("코페이 키오스크 빠삭튀김");
        }

        @Test
        @DisplayName("괄호 없는 일반 상호명은 변경되지 않는다")
        void 일반_상호명은_변경되지_않는다() {
            assertThat(classifier.normalize("롯데백화점")).isEqualTo("롯데백화점");
        }

        @Test
        @DisplayName("짧은 일반 명사 상호명은 변경되지 않는다")
        void 짧은_일반_명사_상호명은_변경되지_않는다() {
            assertThat(classifier.normalize("문구점")).isEqualTo("문구점");
        }
    }

    // ================================================================
    @Nested
    @DisplayName("매칭 로직 및 규칙 우선순위")
    class 매칭로직_및_규칙 {

        @Test
        @DisplayName("EXACT 등록 브랜드명을 완전 일치로 입력하면 해당 categoryId를 반환한다")
        void EXACT로_등록된_브랜드명을_정확히_입력하면_해당_categoryId를_반환한다() {
            MerchantCategoryRule result = classifier.classify("KFC");

            assertThat(result).isNotNull();
            assertThat(result.getCategoryId())
                    .as("EXACT 키워드 'KFC'는 식비(5)로 분류되어야 한다")
                    .isEqualTo(5L);
        }

        @Test
        @DisplayName("이마트24(EXACT=식비)와 이마트(CONTAINS=생활비)가 모두 있을 때 EXACT가 우선 적용된다")
        void 이마트24는_EXACT가_CONTAINS보다_우선_적용된다() {
            MerchantCategoryRule result = classifier.classify("이마트24");

            assertThat(result).isNotNull();
            assertThat(result.getCategoryId())
                    .as("EXACT '이마트24'(식비=5)가 CONTAINS '이마트'(생활비=6)보다 우선 적용되어야 한다")
                    .isEqualTo(5L);
        }

        @Test
        @DisplayName("동일 키워드에 EXACT와 CONTAINS 규칙이 모두 있을 때 EXACT가 우선 적용된다")
        void EXACT와_CONTAINS가_모두_매칭될_때_EXACT가_우선_적용된다() {
            // 픽스처: EXACT "넷플릭스"→9L, CONTAINS "넷플릭스"→5L
            MerchantCategoryRule result = classifier.classify("넷플릭스");

            assertThat(result).isNotNull();
            assertThat(result.getCategoryId())
                    .as("EXACT '넷플릭스'(9L)가 CONTAINS '넷플릭스'(5L)보다 우선 적용되어야 한다")
                    .isEqualTo(9L);
        }

        @Test
        @DisplayName("EXACT 실패 후 CONTAINS 매칭 성공 시 해당 categoryId를 반환한다")
        void EXACT_실패_후_CONTAINS_매칭된_categoryId를_반환한다() {
            MerchantCategoryRule result = classifier.classify("이마트 성수점");

            assertThat(result).isNotNull();
            assertThat(result.getCategoryId())
                    .as("EXACT 없는 '이마트 성수점'은 CONTAINS '이마트'(생활비=6)로 매칭되어야 한다")
                    .isEqualTo(6L);
        }

        @Test
        @DisplayName("가맹점명에 CONTAINS 키워드가 포함되면 해당 categoryId를 반환한다")
        void CONTAINS_키워드가_가맹점명에_포함되면_해당_categoryId를_반환한다() {
            MerchantCategoryRule result = classifier.classify("강남병원");

            assertThat(result).isNotNull();
            assertThat(result.getCategoryId())
                    .as("'강남병원'은 CONTAINS 키워드 '강남병원'에 매칭되어 의료비(10)로 분류되어야 한다")
                    .isEqualTo(10L);
        }

        @Test
        @DisplayName("지점명이 앞뒤에 붙어 있어도 CONTAINS 키워드가 포함되면 매칭된다")
        void 지점명이_붙어있어도_CONTAINS_키워드가_포함되면_매칭된다() {
            MerchantCategoryRule result = classifier.classify("할리스카페 홍대점");

            assertThat(result).isNotNull();
            assertThat(result.getCategoryId())
                    .as("'할리스카페 홍대점'은 CONTAINS 키워드 '카페'를 포함하므로 식비(5)로 분류되어야 한다")
                    .isEqualTo(5L);
        }

        @Test
        @DisplayName("priority가 높은 CONTAINS 규칙이 낮은 규칙보다 먼저 적용된다")
        void CONTAINS_priority_높은_규칙이_우선_적용된다() {
            // "이마트 왕십리점"은 "이마트"(priority=90)와 "마트"(priority=80) 모두 CONTAINS 매칭
            MerchantCategoryRule result = classifier.classify("이마트 왕십리점");

            assertThat(result).isNotNull();
            assertThat(result.getCategoryId())
                    .as("'이마트 왕십리점'은 '이마트'(priority=90)가 '마트'(priority=80)보다 먼저 적용되어 생활비(6)이어야 한다")
                    .isEqualTo(6L);
        }

        @Test
        @DisplayName("등록되지 않은 가맹점명은 null을 반환한다")
        void 등록되지_않은_가맹점명은_null을_반환한다() {
            MerchantCategoryRule result = classifier.classify("알수없는가맹점xyz");

            assertThat(result)
                    .as("규칙에 없는 가맹점명은 null을 반환해야 한다 — AI 분류 대상")
                    .isNull();
        }
    }
}
