package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.AccountType;
import com.project.flowfinserver.domain.CodefConnectedAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CodefConnectedAccountRepository extends JpaRepository<CodefConnectedAccount, Long> {

    List<CodefConnectedAccount> findByUserIdAndAccountTypeAndIsActiveTrue(Long userId, AccountType accountType);

    List<CodefConnectedAccount> findAllByUserIdAndIsActiveTrue(Long userId);

    Optional<CodefConnectedAccount> findByIdAndUserId(Long id, Long userId);

    List<CodefConnectedAccount> findAllByIsActiveTrue();

    boolean existsByUserIdAndOrganizationCodeAndAccountTypeAndIsActiveTrue(Long userId, String organizationCode, AccountType accountType);

    Optional<CodefConnectedAccount> findByUserIdAndOrganizationCodeAndAccountType(Long userId, String organizationCode, AccountType accountType);

    Optional<CodefConnectedAccount> findByUserIdAndOrganizationCodeAndAccountTypeAndIsActiveFalse(Long userId, String organizationCode, AccountType accountType);

    void deleteAllByUserId(Long userId);
}
