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
    private Long id;

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

    @Builder
    public Portfolio(Long userId, Map<String, Object> recommendedAssets, Long investableAmount) {
        this.userId = userId;
        this.recommendedAssets = recommendedAssets;
        this.investableAmount = investableAmount;
    }
}
