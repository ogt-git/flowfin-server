package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.StockAccountSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface

StockAccountSnapshotRepository extends JpaRepository<StockAccountSnapshot, Long> {

    Optional<StockAccountSnapshot> findTopByUserIdOrderBySnapshotDateDesc(Long userId);

    boolean existsByUserIdAndSnapshotDateAndBrokerName(Long userId, LocalDate snapshotDate, String brokerName);
}
