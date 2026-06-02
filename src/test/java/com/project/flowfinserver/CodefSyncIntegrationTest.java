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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

/**
 * CODEF API와 Redis만 Mock, 나머지는 실제 Spring 빈 + Docker MySQL로 동작하는 통합 테스트.
 * @Transactional → 각 테스트 후 DB 자동 롤백
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CodefSyncIntegrationTest {

    @MockBean CodefApiClient codefApiClient;
    @MockBean StringRedisTemplate stringRedisTemplate;

    @Autowired CodefSyncService codefSyncService;
    @Autowired CodefConnectedAccountRepository connectedAccountRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired AssetAccountRepository assetAccountRepository;
    @Autowired AssetItemRepository assetItemRepository;
    @Autowired UserRepository userRepository;

    private Long testUserId;

    private static final String CARD_MOCK_RESPONSE = """
            {
              "result": {"code": "CF-00000", "message": "성공", "extraMessage": ""},
              "data": {
                "resTotalCnt": "4",
                "resChargeHistoryList": [
                  {"resUsedDate": "20240401", "resMemberStoreName": "스타벅스 강남점", "resUsedAmount": "6500"},
                  {"resUsedDate": "20240402", "resMemberStoreName": "쿠팡", "resUsedAmount": "35000"},
                  {"resUsedDate": "20240403", "resMemberStoreName": "넷플릭스", "resUsedAmount": "13500"},
                  {"resUsedDate": "20240404", "resMemberStoreName": "배달의민족", "resUsedAmount": "28000"}
                ]
              }
            }
            """;

    private static final String STOCK_MOCK_RESPONSE = """
            {
              "result": {"code": "CF-00000", "message": "성공", "extraMessage": ""},
              "data": {
                "resAccount": "12345678901",
                "resDepositReceived": "500000",
                "resItemList": [
                  {
                    "resItemName": "삼성전자", "resItemCode": "005930",
                    "resValuationAmt": "1100000", "resPurchaseAmount": "1000000", "resValuationPL": "100000"
                  },
                  {
                    "resItemName": "SK하이닉스", "resItemCode": "000660",
                    "resValuationAmt": "880000", "resPurchaseAmount": "800000", "resValuationPL": "80000"
                  }
                ]
              }
            }
            """;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        String emailHash = Integer.toHexString("integration-test@flowfin.test".hashCode());
        User testUser = userRepository.save(
                User.create("integration-test@flowfin.test", emailHash, "pw-hash", "테스트유저", null, "1.0"));
        testUserId = testUser.getId();

        if (categoryRepository.findByName("기타지출").isEmpty()) {
            categoryRepository.save(Category.create("기타지출", CategoryType.ETC));
        }

        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        given(stringRedisTemplate.hasKey(anyString())).willReturn(false);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
    }

    // ==================== syncCard 통합 테스트 ====================

    @Test
    @DisplayName("카드 동기화 전체 흐름: CODEF 응답 → 파싱 → DB 저장 → 조회")
    void syncCard_fullFlow_savesAndRetrievesExpenses() throws Exception {
        connectedAccountRepository.save(
                CodefConnectedAccount.create(testUserId, "demo-connected-id-001", "0301", AccountType.CARD, null, null));

        given(codefApiClient.requestProduct(anyString(), any())).willReturn(CARD_MOCK_RESPONSE);

        CodefSyncResultDto result = codefSyncService.syncCard(testUserId);

        System.out.println("\n========== CODEF 동기화 결과 ==========");
        System.out.println("저장 성공: " + result.getSavedCount() + "건");
        System.out.println("중복 스킵: " + result.getSkippedCount() + "건");
        System.out.println("실패 계좌: " + result.getFailedAccounts());

        assertThat(result.getSavedCount()).isEqualTo(4);
        assertThat(result.getSkippedCount()).isZero();
        assertThat(result.getFailedAccounts()).isEmpty();

        List<Expense> savedExpenses = expenseRepository.findByUserIdAndExpenseDateBetween(
                testUserId,
                LocalDateTime.of(2024, 4, 1, 0, 0),
                LocalDateTime.of(2024, 4, 30, 23, 59)
        );

        System.out.println("\n========== 저장된 지출 내역 ==========");
        savedExpenses.forEach(e -> System.out.printf(
                "[%s] %-20s %,7d원  (카드사: %s, 카테고리: %s, 분류: %s)%n",
                e.getExpenseDate(), e.getMerchantName(), e.getAmount(),
                e.getCardCompany(),
                e.getCategory() != null ? e.getCategory().getId() : "미분류",
                e.getClassifiedBy()
        ));

        assertThat(savedExpenses).hasSize(4);
        assertThat(savedExpenses).extracting(Expense::getMerchantName)
                .containsExactlyInAnyOrder("스타벅스 강남점", "쿠팡", "넷플릭스", "배달의민족");
        assertThat(savedExpenses).extracting(Expense::getAmount)
                .containsExactlyInAnyOrder(6500L, 35000L, 13500L, 28000L);
        assertThat(savedExpenses).allMatch(e -> e.getUserId().equals(testUserId));
        assertThat(savedExpenses).allMatch(e -> "0301".equals(e.getCardCompany()));
    }

    @Test
    @DisplayName("중복 동기화: 동일 데이터 두 번 호출 → 두 번째는 모두 스킵")
    void syncCard_secondCall_skipsAllDuplicates() throws Exception {
        connectedAccountRepository.save(
                CodefConnectedAccount.create(testUserId, "demo-connected-id-002", "0301", AccountType.CARD, null, null));

        given(codefApiClient.requestProduct(anyString(), any())).willReturn(CARD_MOCK_RESPONSE);

        CodefSyncResultDto first  = codefSyncService.syncCard(testUserId);
        CodefSyncResultDto second = codefSyncService.syncCard(testUserId);

        System.out.println("\n========== 중복 동기화 결과 ==========");
        System.out.println("1차: saved=" + first.getSavedCount() + ", skipped=" + first.getSkippedCount());
        System.out.println("2차: saved=" + second.getSavedCount() + ", skipped=" + second.getSkippedCount());

        assertThat(first.getSavedCount()).isEqualTo(4);
        assertThat(second.getSavedCount()).isZero();
        assertThat(second.getSkippedCount()).isEqualTo(4);

        long count = expenseRepository.findByUserIdAndExpenseDateBetween(
                testUserId,
                LocalDateTime.of(2024, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0)).size();
        assertThat(count).isEqualTo(4);
    }

    @Test
    @DisplayName("연동 계정 없을 때 CodefAccountNotFoundException 발생")
    void syncCard_noAccount_throwsException() {
        assertThatThrownBy(() -> codefSyncService.syncCard(testUserId))
                .isInstanceOf(CodefAccountNotFoundException.class);
    }

    // ==================== syncStock 통합 테스트 ====================

    @Test
    @DisplayName("증권 동기화 전체 흐름: resItemList → AssetAccount·AssetItem upsert 확인")
    void syncStock_fullFlow_savesAssetAccountAndItems() throws Exception {
        CodefConnectedAccount stockConn = CodefConnectedAccount.create(
                testUserId, "stock-connected-id-001", "0240", AccountType.STOCK, null, null);
        stockConn.updateAccountNumber("12345678901");
        connectedAccountRepository.save(stockConn);

        given(codefApiClient.requestProduct(anyString(), any())).willReturn(STOCK_MOCK_RESPONSE);

        CodefSyncResultDto result = codefSyncService.syncStock(testUserId);

        System.out.println("\n========== 증권 동기화 결과 ==========");
        System.out.println("저장 성공: " + result.getSavedCount() + "건");

        assertThat(result.getSavedCount()).isEqualTo(1);
        assertThat(result.getFailedAccounts()).isEmpty();

        List<AssetAccount> accounts = assetAccountRepository.findAllByUserId(testUserId);
        assertThat(accounts).hasSize(1);
        AssetAccount account = accounts.get(0);

        System.out.println("\n========== 저장된 증권 계좌 ==========");
        System.out.printf("브로커: %s%n", account.getBrokerCode());
        System.out.printf("총자산: %,d원%n", account.getTotalAsset());
        System.out.printf("예수금: %,d원%n", account.getDepositReceived());

        assertThat(account.getBrokerCode()).isEqualTo("0240");
        assertThat(account.getTotalAsset()).isEqualTo(1_980_000L); // 1100000 + 880000
        assertThat(account.getDepositReceived()).isEqualTo(500_000L);

        List<AssetItem> items = assetItemRepository.findAllByAccountId(account.getId());
        assertThat(items).hasSize(2);
        assertThat(items).extracting(AssetItem::getItemCode)
                .containsExactlyInAnyOrder("005930", "000660");
    }

    @Test
    @DisplayName("증권 중복 동기화: 두 번째 호출 시 기존 데이터 upsert(업데이트)")
    void syncStock_secondCall_upsertsSameAccount() throws Exception {
        CodefConnectedAccount stockConn = CodefConnectedAccount.create(
                testUserId, "stock-connected-id-002", "0240", AccountType.STOCK, null, null);
        stockConn.updateAccountNumber("12345678901");
        connectedAccountRepository.save(stockConn);

        given(codefApiClient.requestProduct(anyString(), any())).willReturn(STOCK_MOCK_RESPONSE);

        CodefSyncResultDto first  = codefSyncService.syncStock(testUserId);
        CodefSyncResultDto second = codefSyncService.syncStock(testUserId);

        System.out.println("\n========== 증권 중복 동기화 결과 ==========");
        System.out.println("1차: saved=" + first.getSavedCount());
        System.out.println("2차: saved=" + second.getSavedCount());

        // upsert 방식이므로 두 번 모두 성공(saved=1)
        assertThat(first.getSavedCount()).isEqualTo(1);
        assertThat(second.getSavedCount()).isEqualTo(1);

        // 계좌는 여전히 1개 (중복 생성 없음)
        assertThat(assetAccountRepository.findAllByUserId(testUserId)).hasSize(1);
    }

    @Test
    @DisplayName("AES 암호화 검증: connectedId가 JPA 컨버터를 통해 투명하게 암/복호화됨")
    void connectedId_isStoredEncrypted() {
        String plainConnectedId = "plain-connected-id-12345";

        CodefConnectedAccount saved = connectedAccountRepository.save(
                CodefConnectedAccount.create(testUserId, plainConnectedId, "0301", AccountType.CARD, null, null));

        connectedAccountRepository.flush();
        CodefConnectedAccount reloaded = connectedAccountRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getConnectedId()).isEqualTo(plainConnectedId);

        System.out.println("\n========== AES 암호화 검증 ==========");
        System.out.println("원본 connectedId: " + plainConnectedId);
        System.out.println("복호화 후 connectedId: " + reloaded.getConnectedId());
        System.out.println("JPA 컨버터가 투명하게 암/복호화 처리 확인");
    }
}
