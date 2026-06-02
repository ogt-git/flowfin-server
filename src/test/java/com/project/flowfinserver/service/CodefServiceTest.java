package com.project.flowfinserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.flowfinserver.cache.ExpenseStatsCacheManager;
import com.project.flowfinserver.codef.CodefApiClient;
import com.project.flowfinserver.domain.AccountType;
import com.project.flowfinserver.domain.CodefConnectedAccount;
import com.project.flowfinserver.repository.AssetAccountRepository;
import com.project.flowfinserver.repository.AssetItemRepository;
import com.project.flowfinserver.repository.CodefConnectedAccountRepository;
import com.project.flowfinserver.repository.ExpenseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CodefServiceTest {

    @Mock CodefApiClient codefApiClient;
    @Mock CodefConnectedAccountRepository connectedAccountRepository;
    @Mock ExpenseRepository expenseRepository;
    @Mock ExpenseStatsCacheManager expenseStatsCacheManager;
    @Mock AssetAccountRepository assetAccountRepository;
    @Mock AssetItemRepository assetItemRepository;
    @Spy  ObjectMapper objectMapper;

    @InjectMocks
    CodefService codefService;

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long CONNECTION_ID = 10L;

    private static final String DELETE_SUCCESS_RESPONSE = """
            {"result": {"code": "CF-00000", "message": "성공"}, "data": {}}
            """;

    @Test
    @DisplayName("disconnect — 본인 연동 해지: deactivate() 호출")
    void disconnect_deactivatesOwnConnection() throws Exception {
        CodefConnectedAccount conn = CodefConnectedAccount.create(OWNER_ID, "conn-id", "0301", AccountType.CARD, null, null);
        given(connectedAccountRepository.findByIdAndUserId(CONNECTION_ID, OWNER_ID))
                .willReturn(Optional.of(conn));
        given(codefApiClient.deleteAccount(any())).willReturn(DELETE_SUCCESS_RESPONSE);
        given(connectedAccountRepository.existsByUserIdAndOrganizationCodeAndAccountTypeAndIsActiveTrueAndIdNot(
                eq(OWNER_ID), anyString(), eq(AccountType.CARD), eq(CONNECTION_ID))).willReturn(false);

        codefService.disconnect(OWNER_ID, CONNECTION_ID);

        assertThat(conn.isActive()).isFalse();
    }

    @Test
    @DisplayName("disconnect — 타인 연동 접근: AccessDeniedException 발생")
    void disconnect_throwsForOtherUsersConnection() {
        given(connectedAccountRepository.findByIdAndUserId(CONNECTION_ID, OTHER_USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> codefService.disconnect(OTHER_USER_ID, CONNECTION_ID))
                .isInstanceOf(AccessDeniedException.class);

        then(codefApiClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("disconnect — CODEF API 실패해도 로컬 비활성화는 완료")
    void disconnect_deactivatesEvenIfCodefApiFails() throws Exception {
        CodefConnectedAccount conn = CodefConnectedAccount.create(OWNER_ID, "conn-id", "0301", AccountType.CARD, null, null);
        given(connectedAccountRepository.findByIdAndUserId(CONNECTION_ID, OWNER_ID))
                .willReturn(Optional.of(conn));
        given(codefApiClient.deleteAccount(any())).willThrow(new RuntimeException("CODEF 통신 오류"));
        given(connectedAccountRepository.existsByUserIdAndOrganizationCodeAndAccountTypeAndIsActiveTrueAndIdNot(
                eq(OWNER_ID), anyString(), eq(AccountType.CARD), eq(CONNECTION_ID))).willReturn(false);

        codefService.disconnect(OWNER_ID, CONNECTION_ID);

        assertThat(conn.isActive()).isFalse();
    }
}
