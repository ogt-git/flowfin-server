package com.project.flowfinserver.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

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

    @Column(name = "recommended_assets", columnDefinition = "JSON")
    private String recommendedAssets;

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
    }
}
