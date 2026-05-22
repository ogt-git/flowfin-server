package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.Portfolio;
import com.project.flowfinserver.domain.PortfolioStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Integer> {

    Optional<Portfolio> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Portfolio> findTopByUserIdAndStatusOrderByCreatedAtDesc(Long userId, PortfolioStatus status);

    List<Portfolio> findByUserIdOrderByCreatedAtDesc(Long userId);
}
