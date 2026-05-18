package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.AccountType;
import com.project.flowfinserver.domain.CodefConnectedAccount;
import com.project.flowfinserver.exception.CodefApiException;
import com.project.flowfinserver.exception.CodefRetryableException;
import com.project.flowfinserver.repository.CodefConnectedAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CodefBatchServiceTest {

    @Mock CodefConnectedAccountRepository connectedAccountRepository;
    @Mock CodefSyncService codefSyncService;

    @InjectMocks
    CodefBatchService codefBatchService;

    private CodefConnectedAccount cardConn;

    @BeforeEach
    void setUp() {
        cardConn = CodefConnectedAccount.create(1L, "conn-id-001", "0301", AccountType.CARD);
        ReflectionTestUtils.setField(cardConn, "id", 10L);
    }

    @Test
    @DisplayName("동기화 성공 — 첫 번째 시도에서 성공: deactivateConnection 호출 없음")
    void success_firstAttempt_noDeactivation() throws Exception {
        given(connectedAccountRepository.findAllByIsActiveTrue()).willReturn(List.of(cardConn));
        willDoNothing().given(codefSyncService).syncConnection(cardConn);

        codefBatchService.syncAllActiveConnections();

        then(codefSyncService).should(times(1)).syncConnection(cardConn);
        then(codefSyncService).should(never()).deactivateConnection(anyLong());
    }

    @Test
    @DisplayName("일시 오류 3회 모두 실패 → deactivateConnection 호출")
    void retryableError_3times_deactivates() throws Exception {
        given(connectedAccountRepository.findAllByIsActiveTrue()).willReturn(List.of(cardConn));
        willThrow(new CodefRetryableException("CF-01007", "네트워크 오류"))
                .given(codefSyncService).syncConnection(cardConn);

        codefBatchService.syncAllActiveConnections();

        then(codefSyncService).should(times(3)).syncConnection(cardConn);
        then(codefSyncService).should(times(1)).deactivateConnection(10L);
    }

    @Test
    @DisplayName("일시 오류 2회 후 성공 → deactivateConnection 호출 없음")
    void retryableError_2times_then_success() throws Exception {
        given(connectedAccountRepository.findAllByIsActiveTrue()).willReturn(List.of(cardConn));
        willThrow(new CodefRetryableException("CF-01007", "네트워크 오류"))
                .willThrow(new CodefRetryableException("CF-01007", "네트워크 오류"))
                .willDoNothing()
                .given(codefSyncService).syncConnection(cardConn);

        codefBatchService.syncAllActiveConnections();

        then(codefSyncService).should(times(3)).syncConnection(cardConn);
        then(codefSyncService).should(never()).deactivateConnection(anyLong());
    }

    @Test
    @DisplayName("비재시도 오류(CodefApiException) 첫 번째 발생 → 즉시 중단, deactivateConnection 호출")
    void nonRetryableError_immediateBreak_deactivates() throws Exception {
        given(connectedAccountRepository.findAllByIsActiveTrue()).willReturn(List.of(cardConn));
        willThrow(new CodefApiException("CF-04000", "계정 등록 실패"))
                .given(codefSyncService).syncConnection(cardConn);

        codefBatchService.syncAllActiveConnections();

        then(codefSyncService).should(times(1)).syncConnection(cardConn);
        then(codefSyncService).should(times(1)).deactivateConnection(10L);
    }
}
