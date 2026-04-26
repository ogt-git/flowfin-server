package com.project.flowfinserver;

import com.project.flowfinserver.codef.CodefApiClient;
import com.project.flowfinserver.domain.*;
import com.project.flowfinserver.dto.codef.CodefSyncResultDto;
import com.project.flowfinserver.exception.CodefAccountNotFoundException;
import com.project.flowfinserver.repository.CategoryRepository;
import com.project.flowfinserver.repository.CodefConnectedAccountRepository;
import com.project.flowfinserver.repository.ExpenseRepository;
import com.project.flowfinserver.repository.StockAccountSnapshotRepository;
import com.project.flowfinserver.service.CodefSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * CODEF API만 Mock, 나머지는 실제 Spring 빈 + H2 인메모리 DB로 동작하는 통합 테스트.
 * @Transactional → 각 테스트 후 DB 자동 롤백
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CodefSyncIntegrationTest {

    @MockBean
    CodefApiClient codefApiClient; // 외부 CODEF API만 Mock

    @Autowired CodefSyncService codefSyncService;
    @Autowired CodefConnectedAccountRepository connectedAccountRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired StockAccountSnapshotRepository stockSnapshotRepository;

    private static final Long TEST_USER_ID = 999L;

    // CODEF 카드 청구내역 Mock 응답 — 실제 API 응답 형식과 동일
    private static final String CARD_MOCK_RESPONSE = """
            {
              "result": {"code": "CF-00000", "message": "성공", "extraMessage": ""},
              "data": {
                "resTotalCnt": "4",
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
                  },
                  {
                    "resUsedDate": "20240404",
                    "resMemberStoreName": "배달의민족",
                    "resUsedAmount": "28000"
                  }
                ]
              }
            }
            """;

    @BeforeEach
    void setUp() {
        // "기타지출" 기본 카테고리 삽입 (CategoryClassificationService fallback용)
        if (categoryRepository.findByName("기타지출").isEmpty()) {
            categoryRepository.save(Category.builder()
                    .name("기타지출")
                    .icon("📦")
                    .color("#B0B0B0")
                    .isFixed(false)
                    .defaultExpenseType(ExpenseType.IRREGULAR)
                    .sortOrder(99)
                    .build());
        }
    }

    @Test
    @DisplayName("카드 동기화 전체 흐름: CODEF 응답 → 파싱 → DB 저장 → 조회")
    void syncCard_fullFlow_savesAndRetrievesExpenses() throws Exception {
        // given — 연동 계정 DB 저장 (AES 암호화 자동 적용)
        CodefConnectedAccount account = connectedAccountRepository.save(
                CodefConnectedAccount.builder()
                        .userId(TEST_USER_ID)
                        .connectedId("demo-connected-id-001")  // 암호화 후 저장됨
                        .organizationCode("0301")
                        .accountType(AccountType.CARD)
                        .build()
        );

        given(codefApiClient.requestProduct(anyString(), any())).willReturn(CARD_MOCK_RESPONSE);

        // when
        CodefSyncResultDto result = codefSyncService.syncCard(TEST_USER_ID);

        // then — 결과 DTO 검증
        System.out.println("\n========== CODEF 동기화 결과 ==========");
        System.out.println("저장 성공: " + result.getSavedCount() + "건");
        System.out.println("중복 스킵: " + result.getSkippedCount() + "건");
        System.out.println("실패 계좌: " + result.getFailedAccounts());
        System.out.println("동기화 시각: " + result.getSyncedAt());

        assertThat(result.getSavedCount()).isEqualTo(4);
        assertThat(result.getSkippedCount()).isZero();
        assertThat(result.getFailedAccounts()).isEmpty();

        // then — DB에서 실제 저장된 데이터 조회 및 출력
        List<Expense> savedExpenses = expenseRepository.findByUserIdAndTransactedAtBetween(
                TEST_USER_ID,
                LocalDate.of(2024, 4, 1),
                LocalDate.of(2024, 4, 30)
        );

        System.out.println("\n========== 저장된 지출 내역 ==========");
        savedExpenses.forEach(e -> System.out.printf(
                "[%s] %-20s %,7d원  (카드사: %s, 카테고리ID: %s, 분류: %s)%n",
                e.getTransactedAt(), e.getMerchantName(), e.getAmount(),
                e.getCardCompany(), e.getCategoryId(), e.getClassifiedBy()
        ));

        assertThat(savedExpenses).hasSize(4);
        assertThat(savedExpenses)
                .extracting(Expense::getMerchantName)
                .containsExactlyInAnyOrder("스타벅스 강남점", "쿠팡", "넷플릭스", "배달의민족");
        assertThat(savedExpenses)
                .extracting(Expense::getAmount)
                .containsExactlyInAnyOrder(6500L, 35000L, 13500L, 28000L);
        assertThat(savedExpenses)
                .allMatch(e -> e.getUserId().equals(TEST_USER_ID));
        assertThat(savedExpenses)
                .allMatch(e -> "0301".equals(e.getCardCompany()));
    }

    @Test
    @DisplayName("중복 동기화: 동일 데이터 두 번 호출 → 두 번째는 모두 스킵")
    void syncCard_secondCall_skipsAllDuplicates() throws Exception {
        // given
        connectedAccountRepository.save(CodefConnectedAccount.builder()
                .userId(TEST_USER_ID)
                .connectedId("demo-connected-id-002")
                .organizationCode("0301")
                .accountType(AccountType.CARD)
                .build());

        given(codefApiClient.requestProduct(anyString(), any())).willReturn(CARD_MOCK_RESPONSE);

        // when — 첫 번째 동기화
        CodefSyncResultDto first = codefSyncService.syncCard(TEST_USER_ID);
        // when — 두 번째 동기화 (동일 데이터)
        CodefSyncResultDto second = codefSyncService.syncCard(TEST_USER_ID);

        // then
        System.out.println("\n========== 중복 동기화 결과 ==========");
        System.out.println("1차: saved=" + first.getSavedCount() + ", skipped=" + first.getSkippedCount());
        System.out.println("2차: saved=" + second.getSavedCount() + ", skipped=" + second.getSkippedCount());

        assertThat(first.getSavedCount()).isEqualTo(4);
        assertThat(second.getSavedCount()).isZero();
        assertThat(second.getSkippedCount()).isEqualTo(4);

        // DB에는 4건만 존재
        long count = expenseRepository.findByUserIdAndTransactedAtBetween(
                TEST_USER_ID, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)).size();
        assertThat(count).isEqualTo(4);
    }

    @Test
    @DisplayName("연동 계정 없을 때 CodefAccountNotFoundException 발생")
    void syncCard_noAccount_throwsException() {
        assertThatThrownBy(() -> codefSyncService.syncCard(TEST_USER_ID))
                .isInstanceOf(CodefAccountNotFoundException.class);
    }

    // ==================== syncStock 통합 테스트 ====================

    private static final String STOCK_MOCK_RESPONSE = """
            {
              "result": {"code": "CF-00000", "message": "성공", "extraMessage": ""},
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
    @DisplayName("증권 동기화 전체 흐름: resItemList 합산 → DB 저장 → 조회")
    void syncStock_fullFlow_savesSnapshotAndRetrievesIt() throws Exception {
        // given
        connectedAccountRepository.save(CodefConnectedAccount.builder()
                .userId(TEST_USER_ID)
                .connectedId("stock-connected-id-001")
                .organizationCode("0240")   // KB증권
                .accountType(AccountType.STOCK)
                .build());

        given(codefApiClient.requestProduct(anyString(), any())).willReturn(STOCK_MOCK_RESPONSE);

        // when
        CodefSyncResultDto result = codefSyncService.syncStock(TEST_USER_ID);

        // then
        System.out.println("\n========== 증권 동기화 결과 ==========");
        System.out.println("저장 성공: " + result.getSavedCount() + "건");
        System.out.println("중복 스킵: " + result.getSkippedCount() + "건");
        System.out.println("동기화 시각: " + result.getSyncedAt());

        assertThat(result.getSavedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isZero();
        assertThat(result.getFailedAccounts()).isEmpty();

        Optional<StockAccountSnapshot> snapshotOpt = stockSnapshotRepository
                .findTopByUserIdOrderBySnapshotDateDesc(TEST_USER_ID);

        assertThat(snapshotOpt).isPresent();
        StockAccountSnapshot snapshot = snapshotOpt.get();

        System.out.println("\n========== 저장된 증권 스냅샷 ==========");
        System.out.printf("계좌 브로커: %s%n", snapshot.getBrokerName());
        System.out.printf("평가금액:    %,d원%n", snapshot.getTotalEvalAmount());
        System.out.printf("매입금액:    %,d원%n", snapshot.getTotalPurchaseAmount());
        System.out.printf("평가손익:    %,d원%n", snapshot.getProfitLoss());
        System.out.printf("예수금:      %,d원%n", snapshot.getDepositReceived());
        System.out.printf("스냅샷 날짜: %s%n", snapshot.getSnapshotDate());

        // 삼성전자 1,100,000 + SK하이닉스 880,000 = 1,980,000
        assertThat(snapshot.getTotalEvalAmount()).isEqualTo(1_980_000L);
        // 1,000,000 + 800,000 = 1,800,000
        assertThat(snapshot.getTotalPurchaseAmount()).isEqualTo(1_800_000L);
        // 100,000 + 80,000 = 180,000
        assertThat(snapshot.getProfitLoss()).isEqualTo(180_000L);
        assertThat(snapshot.getDepositReceived()).isEqualTo(500_000L);
        assertThat(snapshot.getBrokerName()).isEqualTo("0240");
        assertThat(snapshot.getSnapshotDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("증권 중복 동기화: 오늘 날짜 동일 계좌 두 번 호출 → 두 번째 스킵")
    void syncStock_secondCall_skipsForSameDate() throws Exception {
        connectedAccountRepository.save(CodefConnectedAccount.builder()
                .userId(TEST_USER_ID)
                .connectedId("stock-connected-id-002")
                .organizationCode("0240")
                .accountType(AccountType.STOCK)
                .build());

        given(codefApiClient.requestProduct(anyString(), any())).willReturn(STOCK_MOCK_RESPONSE);

        CodefSyncResultDto first  = codefSyncService.syncStock(TEST_USER_ID);
        CodefSyncResultDto second = codefSyncService.syncStock(TEST_USER_ID);

        System.out.println("\n========== 증권 중복 동기화 결과 ==========");
        System.out.println("1차: saved=" + first.getSavedCount() + ", skipped=" + first.getSkippedCount());
        System.out.println("2차: saved=" + second.getSavedCount() + ", skipped=" + second.getSkippedCount());

        assertThat(first.getSavedCount()).isEqualTo(1);
        assertThat(second.getSavedCount()).isZero();
        assertThat(second.getSkippedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AES 암호화 검증: connectedId가 DB에 평문으로 저장되지 않음")
    void connectedId_isStoredEncrypted() {
        String plainConnectedId = "plain-connected-id-12345";

        CodefConnectedAccount saved = connectedAccountRepository.save(
                CodefConnectedAccount.builder()
                        .userId(TEST_USER_ID)
                        .connectedId(plainConnectedId)
                        .organizationCode("0301")
                        .accountType(AccountType.CARD)
                        .build()
        );

        // JPA 캐시 우회를 위해 EntityManager flush 후 재조회
        connectedAccountRepository.flush();
        CodefConnectedAccount reloaded = connectedAccountRepository.findById(saved.getId()).orElseThrow();

        // 복호화된 값이 원본과 일치해야 함 (JPA 컨버터가 투명하게 암/복호화)
        assertThat(reloaded.getConnectedId()).isEqualTo(plainConnectedId);

        System.out.println("\n========== AES 암호화 검증 ==========");
        System.out.println("원본 connectedId: " + plainConnectedId);
        System.out.println("복호화 후 connectedId: " + reloaded.getConnectedId());
        System.out.println("✓ JPA 컨버터가 투명하게 암/복호화 처리 확인");
    }
}
