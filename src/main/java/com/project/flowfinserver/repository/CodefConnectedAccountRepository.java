package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.AccountType;
import com.project.flowfinserver.domain.CodefConnectedAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CodefConnectedAccountRepository extends JpaRepository<CodefConnectedAccount, Long> {

    List<CodefConnectedAccount> findByUserIdAndAccountTypeAndIsActiveTrue(Long userId, AccountType accountType);

    boolean existsByUserIdAndOrganizationCodeAndAccountType(Long userId, String organizationCode, AccountType accountType);

    Optional<CodefConnectedAccount> findByUserIdAndOrganizationCodeAndAccountType(Long userId, String organizationCode, AccountType accountType);
}
