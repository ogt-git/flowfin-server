package com.project.flowfinserver.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Rule-based 분류를 위한 가맹점 키워드-카테고리 매핑 테이블
@Entity
@Table(name = "merchant_category_rule",
        indexes = @Index(name = "idx_rule_match_type_priority", columnList = "match_type, priority DESC"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantCategoryRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String keyword;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 10)
    private MatchType matchType;

    @Column(nullable = false)
    private int priority; // 높을수록 우선 적용

    @Builder
    public MerchantCategoryRule(String keyword, Long categoryId,
                                 MatchType matchType, int priority) {
        this.keyword = keyword;
        this.categoryId = categoryId;
        this.matchType = matchType;
        this.priority = priority;
    }
}
