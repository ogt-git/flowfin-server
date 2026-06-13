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

    // ID/PW 방식 (loginIdHash 존재) — 중복 활성 연동 확인
    boolean existsByUserIdAndOrganizationCodeAndAccountTypeAndLoginIdHashAndIsActiveTrue(
            Long userId, String organizationCode, AccountType accountType, String loginIdHash);

    // 인증서 방식 (loginIdHash IS NULL) — 중복 활성 연동 확인
    boolean existsByUserIdAndOrganizationCodeAndAccountTypeAndLoginIdHashIsNullAndIsActiveTrue(
            Long userId, String organizationCode, AccountType accountType);

    // ID/PW 방식 — 활성 레코드 조회 (accountNumber 업데이트용)
    Optional<CodefConnectedAccount> findByUserIdAndOrganizationCodeAndAccountTypeAndLoginIdHashAndIsActiveTrue(
            Long userId, String organizationCode, AccountType accountType, String loginIdHash);

    // 인증서 방식 — 활성 레코드 조회 (accountNumber 업데이트용)
    Optional<CodefConnectedAccount> findByUserIdAndOrganizationCodeAndAccountTypeAndLoginIdHashIsNullAndIsActiveTrue(
            Long userId, String organizationCode, AccountType accountType);

    // ID/PW 방식 — 비활성 레코드 조회 (재활성화용)
    Optional<CodefConnectedAccount> findByUserIdAndOrganizationCodeAndAccountTypeAndLoginIdHashAndIsActiveFalse(
            Long userId, String organizationCode, AccountType accountType, String loginIdHash);

    // 인증서 방식 — 비활성 레코드 조회 (재활성화용)
    Optional<CodefConnectedAccount> findByUserIdAndOrganizationCodeAndAccountTypeAndLoginIdHashIsNullAndIsActiveFalse(
            Long userId, String organizationCode, AccountType accountType);

    Optional<CodefConnectedAccount> findByUserIdAndOrganizationCodeAndAccountType(Long userId, String organizationCode, AccountType accountType);

    boolean existsByUserIdAndOrganizationCodeAndAccountTypeAndIsActiveTrueAndIdNot(
            Long userId, String organizationCode, AccountType accountType, Long excludeId);

    void deleteAllByUserId(Long userId);
}
