package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.AssetAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetAccountRepository extends JpaRepository<AssetAccount, Integer> {

    Optional<AssetAccount> findByUserIdAndBrokerCodeAndAccountNo(Long userId, String brokerCode, String accountNo);

    Optional<AssetAccount> findByUserIdAndBrokerCode(Long userId, String brokerCode);

    List<AssetAccount> findAllByUserId(Long userId);

    void deleteAllByUserId(Long userId);
}
