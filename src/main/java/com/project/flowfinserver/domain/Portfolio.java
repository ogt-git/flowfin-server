package com.project.flowfinserver.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PortfolioStatus status;

    @Column(name = "investable_amount")
    private Long investableAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommended_assets", columnDefinition = "JSON")
    private RecommendedAssetsVo recommendedAssets;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "ai_diagnosis", columnDefinition = "TEXT")
    private String aiDiagnosis;

    @Column(name = "recommend_input_hash", length = 64)
    private String recommendInputHash;

    @Column(name = "failed_reason")
    private String failedReason;
  
    @Column(name = "portfolio_risk_type", length = 50)
    private String portfolioRiskType;


    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static Portfolio createPending(Long userId, String portfolioRiskType,
                                          Long investableAmount, String recommendInputHash) {
        Portfolio portfolio = new Portfolio();
        portfolio.userId = userId;
        portfolio.status = PortfolioStatus.PENDING;
        portfolio.portfolioRiskType = portfolioRiskType;
        portfolio.investableAmount = Math.max(0L, investableAmount);
        portfolio.recommendInputHash = recommendInputHash;
        return portfolio;
    }

    public void complete(RecommendedAssetsVo recommendedAssets, String summary, String aiDiagnosis) {
        this.status = PortfolioStatus.COMPLETED;
        this.recommendedAssets = recommendedAssets;
        this.summary = summary;
        this.aiDiagnosis = aiDiagnosis;
    }

    public void fail(String failedReason) {
        this.status = PortfolioStatus.FAILED;
        this.failedReason = failedReason;
    }
}
