package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    // fetchedAt 기준 최신값 — Spring Data 파생 쿼리 (JPQL LIMIT 없음, 런타임 안전)
    Optional<ExchangeRate> findFirstByCurrencyCodeOrderByFetchedAtDesc(String currencyCode);

    // 중복 저장 방지용 — 같은 통화/같은 기준일 존재 여부 확인
    Optional<ExchangeRate> findByCurrencyCodeAndBaseDate(String currencyCode, LocalDate baseDate);
}
