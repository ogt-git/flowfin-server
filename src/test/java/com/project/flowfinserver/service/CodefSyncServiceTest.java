package com.project.flowfinserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.flowfinserver.codef.CodefApiClient;
import com.project.flowfinserver.domain.*;
import com.project.flowfinserver.dto.codef.CodefSyncResultDto;
import com.project.flowfinserver.exception.CodefAccountNotFoundException;
import com.project.flowfinserver.repository.CodefConnectedAccountRepository;
import com.project.flowfinserver.repository.ExpenseRepository;
import com.project.flowfinserver.repository.StockAccountSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CodefSyncServiceTest {

    @Mock CodefApiClient codefApiClient;
    @Mock CodefConnectedAccountRepository connectedAccountRepository;
    @Mock ExpenseRepository expenseRepository;
    @Mock StockAccountSnapshotRepository stockSnapshotRepository;
    @Mock CategoryClassificationService classificationService;
    @Spy  ObjectMapper objectMapper; // 실제 ObjectMapper 사용 (JSON 파싱 검증)

    @InjectMocks
    CodefSyncService codefSyncService;

    private static final Long TEST_USER_ID = 1L;

    // CODEF 카드 승인내역 응답 샘플 JSON
    private static final String CARD_SUCCESS_RESPONSE = """
            {
              "result": {"code": "CF-00000", "message": "성공"},
              "data": {
                "resTotalCnt": "3",
                "resChargeHistoryList": [
                  {
                    "resUsedDate": "20240401",
                    "resMemberStoreName": "스타벅스 강남점",
                    "resUsedAmount": "6500"
                  },
                  {
                    "resUsedDate": "20240402",
                    "resMemberStoreName": "쿠팡",
                    "resUsedAmount": "35000"
                  },
                  {
                    "resUsedDate": "20240403",
                    "resMemberStoreName": "넷플릭스",
                    "resUsedAmount": "13500"
                  }
                ]
              }
            }
            """;

    private static final String CODEF_ERROR_RESPONSE = """
            {
              "result": {"code": "CF-10002", "message": "connectedId 오류"},
              "data": {}
            }
            """;

    private CodefConnectedAccount testAccount;

    @BeforeEach
    void setUp() {
        testAccount = CodefConnectedAccount.builder()
                .userId(TEST_USER_ID)
                .connectedId("test-connected-id-encrypted")
                .organizationCode("0301")  // 신한카드
                .accountType(AccountType.CARD)
                .build();
    }

    // ==================== syncCard 테스트 ====================

    @Test
    @DisplayName("syncCard — 정상 응답: 3건 중 신규 2건 저장, 1건 중복 스킵")
    void syncCard_savesNewAndSkipsDuplicate() throws Exception {
        // given
        given(connectedAccountRepository.findByUserIdAndAccountTypeAndIsActiveTrue(TEST_USER_ID, AccountType.CARD))
                .willReturn(List.of(testAccount));
        given(codefApiClient.requestProduct(anyString(), any())).willReturn(CARD_SUCCESS_RESPONSE);
        given(classificationService.classifyByRule(anyString())).willReturn(Optional.empty());
        given(classificationService.resolveClassifiedBy(null)).willReturn(null);
        given(classificationService.resolveExpenseType(isNull())).willReturn(ExpenseType.VARIABLE);

        // 스타벅스는 이미 저장된 상태 (중복)
        given(expenseRepository.existsByUserIdAndTransactedAtAndMerchantNameAndAmount(
                eq(TEST_USER_ID), eq(LocalDate.of(2024, 4, 1)), eq("스타벅스 강남점"), eq(6500L)))
                .willReturn(true);
        given(expenseRepository.existsByUserIdAndTransactedAtAndMerchantNameAndAmount(
                eq(TEST_USER_ID), eq(LocalDate.of(2024, 4, 2)), eq("쿠팡"), eq(35000L)))
                .willReturn(false);
        given(expenseRepository.existsByUserIdAndTransactedAtAndMerchantNameAndAmount(
                eq(TEST_USER_ID), eq(LocalDate.of(2024, 4, 3)), eq("넷플릭스"), eq(13500L)))
                .willReturn(false);

        // when
        CodefSyncResultDto result = codefSyncService.syncCard(TEST_USER_ID);

        // then
        assertThat(result.getSavedCount()).isEqualTo(2);
        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getFailedAccounts()).isEmpty();
        assertThat(result.getSyncedAt()).isNotNull();

        // 저장된 Expense 내용 검증
        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        then(expenseRepository).should(times(2)).save(captor.capture());

        List<Expense> saved = captor.getAllValues();
        assertThat(saved).extracting(Expense::getMerchantName)
                .containsExactlyInAnyOrder("쿠팡", "넷플릭스");
        assertThat(saved).extracting(Expense::getAmount)
                .containsExactlyInAnyOrder(35000L, 13500L);
        assertThat(saved).allMatch(e -> e.getUserId().equals(TEST_USER_ID));
    }

    @Test
    @DisplayName("syncCard — 연동 계정 없음: CodefAccountNotFoundException 발생")
    void syncCard_throwsWhenNoAccount() {
        given(connectedAccountRepository.findByUserIdAndAccountTypeAndIsActiveTrue(TEST_USER_ID, AccountType.CARD))
                .willReturn(List.of());

        assertThatThrownBy(() -> codefSyncService.syncCard(TEST_USER_ID))
                .isInstanceOf(CodefAccountNotFoundException.class)
                .hasMessageContaining("연동된 카드 계정이 없습니다");
    }

    @Test
    @DisplayName("syncCard — CODEF 오류 코드: failedAccounts에 기록, 예외 미전파")
    void syncCard_recordsFailedAccountOnCodefError() throws Exception {
        given(connectedAccountRepository.findByUserIdAndAccountTypeAndIsActiveTrue(TEST_USER_ID, AccountType.CARD))
                .willReturn(List.of(testAccount));
        given(codefApiClient.requestProduct(anyString(), any())).willReturn(CODEF_ERROR_RESPONSE);

        CodefSyncResultDto result = codefSyncService.syncCard(TEST_USER_ID);

        assertThat(result.getSavedCount()).isZero();
        assertThat(result.getFailedAccounts()).hasSize(1);
        assertThat(result.getFailedAccounts().get(0)).contains("0301").contains("CF-10002");
        then(expenseRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("syncCard — Rule 분류 성공: categoryId와 RULE 태그 저장")
    void syncCard_appliesRuleClassification() throws Exception {
        given(connectedAccountRepository.findByUserIdAndAccountTypeAndIsActiveTrue(TEST_USER_ID, AccountType.CARD))
                .willReturn(List.of(testAccount));
        given(codefApiClient.requestProduct(anyString(), any())).willReturn(CARD_SUCCESS_RESPONSE);

        // Mockito 스텁 순서: generic 먼저, specific 나중에 (specific이 generic을 덮어씀)
        given(classificationService.classifyByRule(anyString())).willReturn(Optional.empty());
        given(classificationService.resolveClassifiedBy(isNull())).willReturn(null);
        given(classificationService.resolveExpenseType(isNull())).willReturn(ExpenseType.VARIABLE);
        // 넷플릭스만 문화/여가비(9)로 분류
        given(classificationService.classifyByRule("넷플릭스")).willReturn(Optional.of(9L));
        given(classificationService.resolveClassifiedBy(9L)).willReturn(ClassifiedBy.RULE);
        given(classificationService.resolveExpenseType(9L)).willReturn(ExpenseType.VARIABLE);

        given(expenseRepository.existsByUserIdAndTransactedAtAndMerchantNameAndAmount(any(), any(), any(), any()))
                .willReturn(false);

        codefSyncService.syncCard(TEST_USER_ID);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        then(expenseRepository).should(times(3)).save(captor.capture());

        Expense netflix = captor.getAllValues().stream()
                .filter(e -> "넷플릭스".equals(e.getMerchantName()))
                .findFirst().orElseThrow();

        assertThat(netflix.getCategoryId()).isEqualTo(9L);
        assertThat(netflix.getClassifiedBy()).isEqualTo(ClassifiedBy.RULE);
    }

    // ==================== syncStock 테스트 ====================

    // 실제 CODEF 증권 종합자산 API 응답 형식 — resItemList 배열 기반
    private static final String STOCK_SUCCESS_RESPONSE = """
            {
              "result": {"code": "CF-00000", "message": "성공"},
              "data": {
                "resAccount": "12345678901",
                "resDepositReceived": "500000",
                "resResultCode": "1",
                "resResultDesc": "정상",
                "resItemList": [
                  {
                    "resProductTypeCd": "01",
                    "resProductType": "주식",
                    "resItemName": "삼성전자",
                    "resItemCode": "005930",
                    "resValuationAmt": "1100000",
                    "resPurchaseAmount": "1000000",
                    "resValuationPL": "100000",
                    "resEarningsRate": "10.00",
                    "resQuantity": "10",
                    "resAvgPresentAmt": "100000",
                    "resPresentAmt": "110000",
                    "resAccountCurrency": "KRW"
                  },
                  {
                    "resProductTypeCd": "01",
                    "resProductType": "주식",
                    "resItemName": "SK하이닉스",
                    "resItemCode": "000660",
                    "resValuationAmt": "880000",
                    "resPurchaseAmount": "800000",
                    "resValuationPL": "80000",
                    "resEarningsRate": "10.00",
                    "resQuantity": "5",
                    "resAvgPresentAmt": "160000",
                    "resPresentAmt": "176000",
                    "resAccountCurrency": "KRW"
                  }
                ]
              }
            }
            """;

    @Test
    @DisplayName("syncStock — 정상 응답: resItemList 합산 후 스냅샷 저장")
    void syncStock_savesSnapshotWithAggregatedAmounts() throws Exception {
        // given
        CodefConnectedAccount stockAccount = CodefConnectedAccount.builder()
                .userId(TEST_USER_ID)
                .connectedId("stock-connected-id")
                .organizationCode("0240")  // KB증권
                .accountType(AccountType.STOCK)
                .build();

        given(connectedAccountRepository.findByUserIdAndAccountTypeAndIsActiveTrue(TEST_USER_ID, AccountType.STOCK))
                .willReturn(List.of(stockAccount));
        given(codefApiClient.requestProduct(anyString(), any())).willReturn(STOCK_SUCCESS_RESPONSE);
        given(stockSnapshotRepository.existsByUserIdAndSnapshotDateAndBrokerName(any(), any(), any()))
                .willReturn(false);

        // when
        CodefSyncResultDto result = codefSyncService.syncStock(TEST_USER_ID);

        // then
        assertThat(result.getSavedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isZero();
        assertThat(result.getFailedAccounts()).isEmpty();

        ArgumentCaptor<StockAccountSnapshot> captor = ArgumentCaptor.forClass(StockAccountSnapshot.class);
        then(stockSnapshotRepository).should(times(1)).save(captor.capture());

        StockAccountSnapshot saved = captor.getValue();
        // 삼성전자(1,100,000) + SK하이닉스(880,000) = 1,980,000
        assertThat(saved.getTotalEvalAmount()).isEqualTo(1_980_000L);
        // 1,000,000 + 800,000 = 1,800,000
        assertThat(saved.getTotalPurchaseAmount()).isEqualTo(1_800_000L);
        // 100,000 + 80,000 = 180,000
        assertThat(saved.getProfitLoss()).isEqualTo(180_000L);
        assertThat(saved.getDepositReceived()).isEqualTo(500_000L);
        assertThat(saved.getUserId()).isEqualTo(TEST_USER_ID);
    }

    @Test
    @DisplayName("syncStock — 연동 증권 계좌 없음: CodefAccountNotFoundException 발생")
    void syncStock_throwsWhenNoAccount() {
        given(connectedAccountRepository.findByUserIdAndAccountTypeAndIsActiveTrue(TEST_USER_ID, AccountType.STOCK))
                .willReturn(List.of());

        assertThatThrownBy(() -> codefSyncService.syncStock(TEST_USER_ID))
                .isInstanceOf(CodefAccountNotFoundException.class)
                .hasMessageContaining("연동된 증권 계좌가 없습니다");
    }
}
