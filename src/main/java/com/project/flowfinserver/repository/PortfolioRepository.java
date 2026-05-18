package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Integer> {

    Optional<Portfolio> findTopByUserIdOrderByCreatedAtDesc(Long userId);
}
