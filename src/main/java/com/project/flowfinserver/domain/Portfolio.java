package com.project.flowfinserver.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "portfolio")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommended_assets", columnDefinition = "JSON")
    private Map<String, Object> recommendedAssets;

    @Column(name = "investable_amount")
    private Long investableAmount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public static Portfolio create(Long userId, String recommendedAssets, Long investableAmount) {
        Portfolio portfolio = new Portfolio();
        portfolio.userId = userId;
        portfolio.recommendedAssets = recommendedAssets;
        portfolio.investableAmount = Math.max(0L, investableAmount);
        return portfolio;
    }

    public void updateInvestableAmount(Long amount) {
        this.investableAmount = Math.max(0L, amount);
    @Builder
    public Portfolio(Long userId, Map<String, Object> recommendedAssets, Long investableAmount) {
        this.userId = userId;
        this.recommendedAssets = recommendedAssets;
        this.investableAmount = investableAmount;
    }
}
