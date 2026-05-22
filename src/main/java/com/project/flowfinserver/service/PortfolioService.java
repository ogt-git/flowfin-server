package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.Portfolio;
import com.project.flowfinserver.dto.PortfolioResponse;
import com.project.flowfinserver.repository.PortfolioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;

    @Transactional(readOnly = true)
    public PortfolioResponse getLatest(Long userId) {
        Portfolio portfolio = portfolioRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new EntityNotFoundException("포트폴리오 추천 내역이 없습니다."));
        return new PortfolioResponse(portfolio);
    }

    @Transactional(readOnly = true)
    public List<PortfolioResponse> getHistory(Long userId) {
        return portfolioRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(PortfolioResponse::new)
                .toList();
    }
}
