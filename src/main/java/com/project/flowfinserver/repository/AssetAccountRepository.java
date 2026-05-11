package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.AssetAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetAccountRepository extends JpaRepository<AssetAccount, Long> {

    Optional<AssetAccount> findByUserIdAndBrokerCode(Long userId, String brokerCode);
}
