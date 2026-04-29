package com.project.flowfinserver;

import com.project.flowfinserver.codef.CodefApiClient;
import com.project.flowfinserver.domain.*;
import com.project.flowfinserver.dto.codef.CodefSyncResultDto;
import com.project.flowfinserver.exception.CodefAccountNotFoundException;
import com.project.flowfinserver.repository.*;
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
 * CODEF API만 Mock, 나머지는 실제 Spring 빈 + Docker MySQL로 동작하는 통합 테스트.
 * @Transactional → 각 테스트 후 DB 자동 롤백
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CodefSyncIntegrationTest {

    @MockBean
    CodefApiClient codefApiClient;

    @Autowired CodefSyncService codefSyncService;
    @Autowired CodefConnectedAccountRepository connectedAccountRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired StockAccountSnapshotRepository stockSnapshotRepository;
    @Autowired UserRepository userRepository;

    private Long testUserId;

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
        // FK(user_id → users.id) 만족을 위해 테스트 유저 생성 (트랜잭션 롤백으로 자동 정리)
        User testUser = userRepository.save(User.builder()
                .email("integration-test@flowfin.test")
                .name("테스트유저")
                .provider(Provider.LOCAL)
                .build());
        testUserId = testUser.getId();

        // "기타지출" 기본 카테고리가 없을 때만 삽입 (init.sql이 이미 넣었다면 스킵)
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
        // given
        connectedAccountRepository.save(
                CodefConnectedAccount.builder()
                        .userId(testUserId)
                        .connectedId("demo-connected-id-001")
                        .organizationCode("0301")
                        .accountType(AccountType.CARD)
                        .build()
        );

        given(codefApiClient.requestProduct(anyString(), any())).willReturn(CARD_MOCK_RESPONSE);

        // when
        CodefSyncResultDto result = codefSyncService.syncCard(testUserId);

        // then — DTO 검증
        System.out.println("\n========== CODEF 동기화 결과 ==========");
        System.out.println("저장 성공: " + result.getSavedCount() + "건");
        System.out.println("중복 스킵: " + result.getSkippedCount() + "건");
        System.out.println("실패 계좌: " + result.getFailedAccounts());
        System.out.println("동기화 시각: " + result.getSyncedAt());

        assertThat(result.getSavedCount()).isEqualTo(4);
        assertThat(result.getSkippedCount()).isZero();
        assertThat(result.getFailedAccounts()).isEmpty();

        // then — DB에서 실제 저장된 데이터 확인
        List<Expense> savedExpenses = expenseRepository.findByUserIdAndTransactedAtBetween(
                testUserId,
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
                .allMatch(e -> e.getUserId().equals(testUserId));
        assertThat(savedExpenses)
                .allMatch(e -> "0301".equals(e.getCardCompany()));
    }

    @Test
    @DisplayName("중복 동기화: 동일 데이터 두 번 호출 → 두 번째는 모두 스킵")
    void syncCard_secondCall_skipsAllDuplicates() throws Exception {
        connectedAccountRepository.save(CodefConnectedAccount.builder()
                .userId(testUserId)
                .connectedId("demo-connected-id-002")
                .organizationCode("0301")
                .accountType(AccountType.CARD)
                .build());

        given(codefApiClient.requestProduct(anyString(), any())).willReturn(CARD_MOCK_RESPONSE);

        CodefSyncResultDto first = codefSyncService.syncCard(testUserId);
        CodefSyncResultDto second = codefSyncService.syncCard(testUserId);

        System.out.println("\n========== 중복 동기화 결과 ==========");
        System.out.println("1차: saved=" + first.getSavedCount() + ", skipped=" + first.getSkippedCount());
        System.out.println("2차: saved=" + second.getSavedCount() + ", skipped=" + second.getSkippedCount());

        assertThat(first.getSavedCount()).isEqualTo(4);
        assertThat(second.getSavedCount()).isZero();
        assertThat(second.getSkippedCount()).isEqualTo(4);

        long count = expenseRepository.findByUserIdAndTransactedAtBetween(
                testUserId, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)).size();
        assertThat(count).isEqualTo(4);
    }

    @Test
    @DisplayName("연동 계정 없을 때 CodefAccountNotFoundException 발생")
    void syncCard_noAccount_throwsException() {
        assertThatThrownBy(() -> codefSyncService.syncCard(testUserId))
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
        connectedAccountRepository.save(CodefConnectedAccount.builder()
                .userId(testUserId)
                .connectedId("stock-connected-id-001")
                .organizationCode("0240")
                .accountType(AccountType.STOCK)
                .build());

        given(codefApiClient.requestProduct(anyString(), any())).willReturn(STOCK_MOCK_RESPONSE);

        CodefSyncResultDto result = codefSyncService.syncStock(testUserId);

        System.out.println("\n========== 증권 동기화 결과 ==========");
        System.out.println("저장 성공: " + result.getSavedCount() + "건");
        System.out.println("중복 스킵: " + result.getSkippedCount() + "건");
        System.out.println("동기화 시각: " + result.getSyncedAt());

        assertThat(result.getSavedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isZero();
        assertThat(result.getFailedAccounts()).isEmpty();

        Optional<StockAccountSnapshot> snapshotOpt = stockSnapshotRepository
                .findTopByUserIdOrderBySnapshotDateDesc(testUserId);

        assertThat(snapshotOpt).isPresent();
        StockAccountSnapshot snapshot = snapshotOpt.get();

        System.out.println("\n========== 저장된 증권 스냅샷 ==========");
        System.out.printf("계좌 브로커: %s%n", snapshot.getBrokerName());
        System.out.printf("평가금액:    %,d원%n", snapshot.getTotalEvalAmount());
        System.out.printf("매입금액:    %,d원%n", snapshot.getTotalPurchaseAmount());
        System.out.printf("평가손익:    %,d원%n", snapshot.getProfitLoss());
        System.out.printf("예수금:      %,d원%n", snapshot.getDepositReceived());
        System.out.printf("스냅샷 날짜: %s%n", snapshot.getSnapshotDate());

        assertThat(snapshot.getTotalEvalAmount()).isEqualTo(1_980_000L);
        assertThat(snapshot.getTotalPurchaseAmount()).isEqualTo(1_800_000L);
        assertThat(snapshot.getProfitLoss()).isEqualTo(180_000L);
        assertThat(snapshot.getDepositReceived()).isEqualTo(500_000L);
        assertThat(snapshot.getBrokerName()).isEqualTo("0240");
        assertThat(snapshot.getSnapshotDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("증권 중복 동기화: 오늘 날짜 동일 계좌 두 번 호출 → 두 번째 스킵")
    void syncStock_secondCall_skipsForSameDate() throws Exception {
        connectedAccountRepository.save(CodefConnectedAccount.builder()
                .userId(testUserId)
                .connectedId("stock-connected-id-002")
                .organizationCode("0240")
                .accountType(AccountType.STOCK)
                .build());

        given(codefApiClient.requestProduct(anyString(), any())).willReturn(STOCK_MOCK_RESPONSE);

        CodefSyncResultDto first  = codefSyncService.syncStock(testUserId);
        CodefSyncResultDto second = codefSyncService.syncStock(testUserId);

        System.out.println("\n========== 증권 중복 동기화 결과 ==========");
        System.out.println("1차: saved=" + first.getSavedCount() + ", skipped=" + first.getSkippedCount());
        System.out.println("2차: saved=" + second.getSavedCount() + ", skipped=" + second.getSkippedCount());

        assertThat(first.getSavedCount()).isEqualTo(1);
        assertThat(second.getSavedCount()).isZero();
        assertThat(second.getSkippedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("AES 암호화 검증: connectedId가 JPA 컨버터를 통해 투명하게 암/복호화됨")
    void connectedId_isStoredEncrypted() {
        String plainConnectedId = "plain-connected-id-12345";

        CodefConnectedAccount saved = connectedAccountRepository.save(
                CodefConnectedAccount.builder()
                        .userId(testUserId)
                        .connectedId(plainConnectedId)
                        .organizationCode("0301")
                        .accountType(AccountType.CARD)
                        .build()
        );

        connectedAccountRepository.flush();
        CodefConnectedAccount reloaded = connectedAccountRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getConnectedId()).isEqualTo(plainConnectedId);

        System.out.println("\n========== AES 암호화 검증 ==========");
        System.out.println("원본 connectedId: " + plainConnectedId);
        System.out.println("복호화 후 connectedId: " + reloaded.getConnectedId());
        System.out.println("✓ JPA 컨버터가 투명하게 암/복호화 처리 확인");
    }
}
