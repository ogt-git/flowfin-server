package com.project.flowfinserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.flowfinserver.codef.CodefApiClient;
import com.project.flowfinserver.domain.AccountType;
import com.project.flowfinserver.domain.CodefConnectedAccount;
import com.project.flowfinserver.dto.ExpenseSaveResult;
import com.project.flowfinserver.dto.codef.CodefSyncResultDto;
import com.project.flowfinserver.dto.codef.StockAssetDto;
import com.project.flowfinserver.dto.codef.StockItemDto;
import com.project.flowfinserver.exception.CodefAccountNotFoundException;
import com.project.flowfinserver.exception.TooManyRequestsException;
import com.project.flowfinserver.openai.AiExpenseClassifier;
import com.project.flowfinserver.repository.CodefConnectedAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CodefSyncServiceTest {

    @Mock CodefApiClient codefApiClient;
    @Mock CodefConnectedAccountRepository connectedAccountRepository;
    @Mock ExpenseSaveService expenseSaveService;
    @Mock AiExpenseClassifier aiExpenseClassifier;
    @Mock AssetService assetService;
    @Spy  ObjectMapper objectMapper;
    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks
    CodefSyncService codefSyncService;

    private static final Long TEST_USER_ID = 1L;

    private static final String CARD_SUCCESS_RESPONSE = """
            {
              "result": {"code": "CF-00000", "message": "성공"},
              "data": {
                "resTotalCnt": "3",
                "resChargeHistoryList": [
                  {"resUsedDate": "20240401", "resMemberStoreName": "스타벅스 강남점", "resUsedAmount": "6500"},
                  {"resUsedDate": "20240402", "resMemberStoreName": "쿠팡", "resUsedAmount": "35000"},
                  {"resUsedDate": "20240403", "resMemberStoreName": "넷플릭스", "resUsedAmount": "13500"}
                ]
              }
            }
            """;

    private static final String CARD_ERROR_RESPONSE = """
            {
              "result": {"code": "CF-10002", "message": "connectedId 오류"},
              "data": {}
            }
            """;

    private static final String STOCK_SUCCESS_RESPONSE = """
            {
              "result": {"code": "CF-00000", "message": "성공"},
              "data": {
                "resAccount": "12345678901",
                "resDepositReceived": "500000",
                "resItemList": [
                  {
                    "resValuationAmt": "1100000", "resPurchaseAmount": "1000000",
                    "resValuationPL": "100000", "resItemName": "삼성전자", "resItemCode": "005930"
                  }
                ]
              }
            }
            """;

    private static final String STOCK_USD_MISSING_AMOUNTS_RESPONSE = """
            {
              "result": {"code": "CF-00000", "message": "success"},
              "data": {
                "resAccount": "12345678901",
                "resDepositReceived": "500000",
                "resItemList": [
                  {
                    "resProductType": "외화증권",
                    "resItemName": "MARSH & MCLENNAN COMPANIES INC",
                    "resItemCode": "MRSH",
                    "resQuantity": "1",
                    "resPresentAmt": "165.78",
                    "resAvgPresentAmt": "161.04",
                    "resPurchaseAmount": "",
                    "resValuationAmt": "252053",
                    "resValuationPL": "",
                    "resEarningsRate": "3.56",
                    "resAccountCurrency": "USD"
                  }
                ]
              }
            }
            """;

    private static final String STOCK_ZERO_EARNINGS_RATE_RESPONSE = """
            {
              "result": {"code": "CF-00000", "message": "success"},
              "data": {
                "resAccount": "12345678901",
                "resDepositReceived": "500000",
                "resItemList": [
                  {
                    "resProductType": "외화증권",
                    "resItemName": "SOME STOCK",
                    "resItemCode": "SOME",
                    "resQuantity": "10",
                    "resPresentAmt": "10000",
                    "resAvgPresentAmt": "10000",
                    "resPurchaseAmount": "",
                    "resValuationAmt": "100000",
                    "resValuationPL": "",
                    "resEarningsRate": "0",
                    "resAccountCurrency": "KRW"
                  }
                ]
              }
            }
            """;

    private static final String STOCK_FULLY_SOLD_RESPONSE = """
            {
              "result": {"code": "CF-00000", "message": "성공"},
              "data": {
                "resAccount": "12345678901",
                "resDepositReceived": "500000",
                "resItemList": [
                  {
                    "resItemName": "삼성전자", "resItemCode": "005930",
                    "resQuantity": "0", "resValuationAmt": "0",
                    "resPurchaseAmount": "0", "resValuationPL": "0"
                  }
                ]
              }
            }
            """;

    private CodefConnectedAccount cardAccount;
    private CodefConnectedAccount stockAccount;

    @BeforeEach
    void setUp() {
        cardAccount  = CodefConnectedAccount.create(TEST_USER_ID, "card-connected-id", "0301", AccountType.CARD, null, null);
        stockAccount = CodefConnectedAccount.create(TEST_USER_ID, "stock-connected-id", "0240", AccountType.STOCK, null, null);
        stockAccount.updateAccountNumber("12345678901");
    }

    // ==================== manualSyncCard 쿨다운 테스트 ====================

    @Test
    @DisplayName("manualSyncCard — 쿨다운 중: TooManyRequestsException 발생")
    void manualSyncCard_throwsWhenCooldownActive() {
        String key = "codef:refresh:cooldown:" + TEST_USER_ID + ":CARD";
        given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(eq(key), eq("1"), eq(5L), eq(TimeUnit.MINUTES))).willReturn(false);

        assertThatThrownBy(() -> codefSyncService.manualSyncCard(TEST_USER_ID))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("5분");
    }

    @Test
    @DisplayName("manualSyncCard — 쿨다운 없음: Redis 키 설정 후 동기화 실행")
    void manualSyncCard_setsCooldownKeyAndSyncs() {
        String key = "codef:refresh:cooldown:" + TEST_USER_ID + ":CARD";
        given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(eq(key), eq("1"), eq(5L), eq(TimeUnit.MINUTES))).willReturn(true);
        given(connectedAccountRepository.findByUserIdAndAccountTypeAndIsActiveTrue(TEST_USER_ID, AccountType.CARD))
                .willReturn(List.of());

        CodefSyncResultDto result = codefSyncService.manualSyncCard(TEST_USER_ID);

        then(valueOps).should().setIfAbsent(eq(key), eq("1"), eq(5L), eq(TimeUnit.MINUTES));
        assertThat(result.getAccountType()).isEqualTo("CARD");
        assertThat(result.getSavedCount()).isZero();
    }

    @Test
    @DisplayName("manualSyncStock — 쿨다운 중: TooManyRequestsException 발생")
    void manualSyncStock_throwsWhenCooldownActive() {
        String key = "codef:refresh:cooldown:" + TEST_USER_ID + ":STOCK";
        given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(eq(key), eq("1"), eq(5L), eq(TimeUnit.MINUTES))).willReturn(false);

        assertThatThrownBy(() -> codefSyncService.manualSyncStock(TEST_USER_ID))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("5분");
    }

    // ==================== syncCard 테스트 ====================

    @Test
    @DisplayName("syncCard — 정상 응답: ExpenseSaveService에 위임하여 결과 반환")
    void syncCard_delegatesToExpenseSaveService() throws Exception {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), eq("1"), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(connectedAccountRepository.findByUserIdAndAccountTypeAndIsActiveTrue(TEST_USER_ID, AccountType.CARD))
                .willReturn(List.of(cardAccount));
        given(codefApiClient.requestProduct(anyString(), any())).willReturn(CARD_SUCCESS_RESPONSE);
        given(expenseSaveService.saveExpenses(eq(TEST_USER_ID), any()))
                .willReturn(new ExpenseSaveResult(3, List.of()));

        CodefSyncResultDto result = codefSyncService.syncCard(TEST_USER_ID);

        // BATCH_CARD_MONTHS=2: 이전 달 + 현재 달 2회 호출, 각 3건 → 합계 6건
        assertThat(result.getSavedCount()).isEqualTo(6);
        assertThat(result.getFailedAccounts()).isEmpty();
        then(expenseSaveService).should(times(2)).saveExpenses(eq(TEST_USER_ID), any());
    }

    @Test
    @DisplayName("syncCard — 연동 계정 없음: CodefAccountNotFoundException 발생")
    void syncCard_throwsWhenNoAccount() {
        given(connectedAccountRepository.findByUserIdAndAccountTypeAndIsActiveTrue(TEST_USER_ID, AccountType.CARD))
                .willReturn(List.of());

        assertThatThrownBy(() -> codefSyncService.syncCard(TEST_USER_ID))
                .isInstanceOf(CodefAccountNotFoundException.class);
    }

    @Test
    @DisplayName("syncCard — CODEF 오류 코드: failedAccounts에 오류 코드 포함하여 기록")
    void syncCard_recordsFailedAccountOnCodefError() throws Exception {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), eq("1"), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(connectedAccountRepository.findByUserIdAndAccountTypeAndIsActiveTrue(TEST_USER_ID, AccountType.CARD))
                .willReturn(List.of(cardAccount));
        given(codefApiClient.requestProduct(anyString(), any())).willReturn(CARD_ERROR_RESPONSE);

        CodefSyncResultDto result = codefSyncService.syncCard(TEST_USER_ID);

        assertThat(result.getSavedCount()).isZero();
        assertThat(result.getFailedAccounts()).hasSize(1);
        assertThat(result.getFailedAccounts().get(0)).contains("0301").contains("CF-10002");
        then(expenseSaveService).should(never()).saveExpenses(any(), any());
    }

    // ==================== syncStock 테스트 ====================

    @Test
    @DisplayName("syncStock — 정상 응답: AssetService에 위임하여 계좌·종목 upsert")
    void syncStock_delegatesToAssetService() throws Exception {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), eq("1"), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(connectedAccountRepository.findByUserIdAndAccountTypeAndIsActiveTrue(TEST_USER_ID, AccountType.STOCK))
                .willReturn(List.of(stockAccount));
        given(codefApiClient.requestProduct(anyString(), any())).willReturn(STOCK_SUCCESS_RESPONSE);

        com.project.flowfinserver.domain.AssetAccount mockAccount =
                com.project.flowfinserver.domain.AssetAccount.create(TEST_USER_ID, "0240", "12345678901", 1_100_000L, 500_000L);
        given(assetService.saveOrUpdateAccount(eq(TEST_USER_ID), any(StockAssetDto.class))).willReturn(mockAccount);

        CodefSyncResultDto result = codefSyncService.syncStock(TEST_USER_ID);

        assertThat(result.getSavedCount()).isEqualTo(1);
        assertThat(result.getFailedAccounts()).isEmpty();
        then(assetService).should(times(1)).saveOrUpdateAccount(eq(TEST_USER_ID), any(StockAssetDto.class));
        then(assetService).should(times(1)).reconcileAndUpsertItems(eq(mockAccount), anyList(), anySet());
    }

    @Test
    @DisplayName("syncStock derives missing purchase amount and valuation P/L")
    void syncStock_derivesMissingAmountsFromEarningsRate() throws Exception {
        CodefConnectedAccount kbStockAccount = CodefConnectedAccount.create(
                TEST_USER_ID, "kb-stock-connected-id", "0218", AccountType.STOCK, null, null);
        kbStockAccount.updateAccountNumber("12345678901");

        given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), eq("1"), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(connectedAccountRepository.findByUserIdAndAccountTypeAndIsActiveTrue(TEST_USER_ID, AccountType.STOCK))
                .willReturn(List.of(kbStockAccount));
        given(codefApiClient.requestProduct(anyString(), any())).willReturn(STOCK_USD_MISSING_AMOUNTS_RESPONSE);

        com.project.flowfinserver.domain.AssetAccount mockAccount =
                com.project.flowfinserver.domain.AssetAccount.create(TEST_USER_ID, "0218", "12345678901", 252_053L, 500_000L);
        given(assetService.saveOrUpdateAccount(eq(TEST_USER_ID), any(StockAssetDto.class))).willReturn(mockAccount);

        CodefSyncResultDto result = codefSyncService.syncStock(TEST_USER_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StockItemDto>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        then(assetService).should().reconcileAndUpsertItems(eq(mockAccount), itemsCaptor.capture(), anySet());

        assertThat(result.getSavedCount()).isEqualTo(1);
        assertThat(itemsCaptor.getValue()).hasSize(1);
        StockItemDto item = itemsCaptor.getValue().get(0);
        assertThat(item.purchaseAmount()).isEqualTo(243_388L);
        assertThat(item.valuationAmt()).isEqualTo(252_053L);
        assertThat(item.valuationPl()).isEqualTo(8_665L);
    }

    @Test
    @DisplayName("수익률 0%일 때 매입금액이 없으면 평가금액으로 보정하고 평가손익은 0으로 설정한다")
    void syncStock_zeroEarningsRate_setsPurchaseAmountToValuationAmt() throws Exception {
        CodefConnectedAccount kbStockAccount = CodefConnectedAccount.create(
                TEST_USER_ID, "kb-stock-connected-id", "0218", AccountType.STOCK, null, null);
        kbStockAccount.updateAccountNumber("12345678901");

        given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), eq("1"), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(connectedAccountRepository.findByUserIdAndAccountTypeAndIsActiveTrue(TEST_USER_ID, AccountType.STOCK))
                .willReturn(List.of(kbStockAccount));
        given(codefApiClient.requestProduct(anyString(), any())).willReturn(STOCK_ZERO_EARNINGS_RATE_RESPONSE);

        com.project.flowfinserver.domain.AssetAccount mockAccount =
                com.project.flowfinserver.domain.AssetAccount.create(TEST_USER_ID, "0218", "12345678901", 100_000L, 500_000L);
        given(assetService.saveOrUpdateAccount(eq(TEST_USER_ID), any(StockAssetDto.class))).willReturn(mockAccount);

        codefSyncService.syncStock(TEST_USER_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StockItemDto>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        then(assetService).should().reconcileAndUpsertItems(eq(mockAccount), itemsCaptor.capture(), anySet());

        StockItemDto item = itemsCaptor.getValue().get(0);
        assertThat(item.purchaseAmount()).isEqualTo(100_000L);
        assertThat(item.valuationAmt()).isEqualTo(100_000L);
        assertThat(item.valuationPl()).isEqualTo(0L);
    }

    @Test
    @DisplayName("syncStock — 전량 매도 종목은 이름·티커가 있어도 보유 목록에서 제외하고 reconcile")
    void syncStock_fullySoldItem_reconcilesWithEmptyHoldings() throws Exception {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), eq("1"), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(connectedAccountRepository.findByUserIdAndAccountTypeAndIsActiveTrue(TEST_USER_ID, AccountType.STOCK))
                .willReturn(List.of(stockAccount));
        given(codefApiClient.requestProduct(anyString(), any())).willReturn(STOCK_FULLY_SOLD_RESPONSE);

        com.project.flowfinserver.domain.AssetAccount mockAccount =
                com.project.flowfinserver.domain.AssetAccount.create(TEST_USER_ID, "0240", "12345678901", 0L, 500_000L);
        given(assetService.saveOrUpdateAccount(eq(TEST_USER_ID), any(StockAssetDto.class))).willReturn(mockAccount);

        CodefSyncResultDto result = codefSyncService.syncStock(TEST_USER_ID);

        assertThat(result.getSavedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isZero();
        then(assetService).should(times(1)).reconcileAndUpsertItems(eq(mockAccount), eq(List.of()), eq(java.util.Set.of()));
        then(assetService).should(never()).saveOrUpdateItems(any(), anyList());
    }

    @Test
    @DisplayName("syncStock — 연동 증권 계좌 없음: CodefAccountNotFoundException 발생")
    void syncStock_throwsWhenNoAccount() {
        given(connectedAccountRepository.findByUserIdAndAccountTypeAndIsActiveTrue(TEST_USER_ID, AccountType.STOCK))
                .willReturn(List.of());

        assertThatThrownBy(() -> codefSyncService.syncStock(TEST_USER_ID))
                .isInstanceOf(CodefAccountNotFoundException.class);
    }
}
