package com.project.flowfinserver.repository;

import com.project.flowfinserver.domain.AccountType;
import com.project.flowfinserver.domain.CodefConnectedAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodefConnectedAccountRepository extends JpaRepository<CodefConnectedAccount, Long> {

    List<CodefConnectedAccount> findByUserIdAndAccountTypeAndIsActiveTrue(Long userId, AccountType accountType);

    boolean existsByUserIdAndOrganizationCodeAndAccountType(Long userId, String organizationCode, AccountType accountType);
}
