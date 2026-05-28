package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.AssetAccount;
import com.project.flowfinserver.domain.AssetItem;
import com.project.flowfinserver.domain.CategoryType;
import com.project.flowfinserver.dto.asset.StockAccountResponse;
import com.project.flowfinserver.dto.codef.StockAssetDto;
import com.project.flowfinserver.dto.codef.StockItemDto;
import com.project.flowfinserver.repository.AssetAccountRepository;
import com.project.flowfinserver.repository.AssetItemRepository;
import com.project.flowfinserver.repository.CategoryRepository;
import com.project.flowfinserver.repository.ExpenseRepository;
import com.project.flowfinserver.repository.ManualAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssetServiceTest {

    @Mock AssetAccountRepository assetAccountRepository;
    @Mock AssetItemRepository assetItemRepository;
    @Mock ManualAssetRepository manualAssetRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ExpenseRepository expenseRepository;

    @Spy
    @InjectMocks
    AssetService assetService;

    private static final Long USER_ID = 1L;
    private static final String BROKER_CODE = "0240";
    private static final String ACCOUNT_NO = "12345678901";

    private StockAssetDto dto;

    @BeforeEach
    void setUp() {
        dto = new StockAssetDto(BROKER_CODE, ACCOUNT_NO, 2_000_000L, 500_000L);

        // syncAssetData 내부의 updateInvestableAmount 의존성 — LENIENT 모드로 무관한 테스트에 영향 방지
        given(categoryRepository.findByType(any(CategoryType.class))).willReturn(List.of());
        given(manualAssetRepository.findByUserIdAndAssetTypeIn(anyLong(), any())).willReturn(List.of());
        given(assetAccountRepository.findAllByUserId(anyLong())).willReturn(List.of());
    }

    // ==================== saveOrUpdateAccount ====================

    @Test
    @DisplayName("saveOrUpdateAccount — 계좌 없음: 신규 save 호출")
    void saveOrUpdateAccount_noExisting_savesNewAccount() {
        given(assetAccountRepository.findByUserIdAndBrokerCode(USER_ID, BROKER_CODE))
                .willReturn(Optional.empty());

        AssetAccount newAccount = AssetAccount.create(USER_ID, BROKER_CODE, ACCOUNT_NO, 2_000_000L, 500_000L);
        given(assetAccountRepository.save(any(AssetAccount.class))).willReturn(newAccount);

        AssetAccount result = assetService.saveOrUpdateAccount(USER_ID, dto);

        then(assetAccountRepository).should(times(1)).save(any(AssetAccount.class));
        assertThat(result.getBrokerCode()).isEqualTo(BROKER_CODE);
    }

    @Test
    @DisplayName("saveOrUpdateAccount — 기존 계좌 있음: 업데이트 후 save 미호출")
    void saveOrUpdateAccount_existing_updatesWithoutSave() {
        AssetAccount existing = AssetAccount.create(USER_ID, BROKER_CODE, ACCOUNT_NO, 1_000_000L, 200_000L);
        given(assetAccountRepository.findByUserIdAndBrokerCode(USER_ID, BROKER_CODE))
                .willReturn(Optional.of(existing));

        AssetAccount result = assetService.saveOrUpdateAccount(USER_ID, dto);

        then(assetAccountRepository).should(never()).save(any());
        assertThat(result.getTotalAsset()).isEqualTo(2_000_000L);
        assertThat(result.getDepositReceived()).isEqualTo(500_000L);
    }

    // ==================== saveOrUpdateItems ====================

    @Test
    @DisplayName("saveOrUpdateItems — 종목 없음: 신규 save 호출")
    void saveOrUpdateItems_noExisting_savesNewItem() {
        AssetAccount account = AssetAccount.create(USER_ID, BROKER_CODE, ACCOUNT_NO, 1_000_000L, 0L);
        ReflectionTestUtils.setField(account, "id", 1);
        ReflectionTestUtils.setField(account, "userId", USER_ID);

        StockItemDto itemDto = new StockItemDto("주식", "삼성전자", "005930", 10,
                1_000_000L, 1_100_000L, 100_000L, new BigDecimal("10.00"));

        given(assetItemRepository.findByAccountIdAndItemCode(1, "005930")).willReturn(Optional.empty());

        assetService.saveOrUpdateItems(account, List.of(itemDto));

        then(assetItemRepository).should(times(1)).save(any(AssetItem.class));
    }

    @Test
    @DisplayName("saveOrUpdateItems — 기존 종목 있음: update 호출, save 미호출")
    void saveOrUpdateItems_existing_updatesWithoutSave() {
        AssetAccount account = AssetAccount.create(USER_ID, BROKER_CODE, ACCOUNT_NO, 1_000_000L, 0L);
        ReflectionTestUtils.setField(account, "id", 1);
        ReflectionTestUtils.setField(account, "userId", USER_ID);

        StockItemDto itemDto = new StockItemDto("주식", "삼성전자", "005930", 20,
                2_000_000L, 2_200_000L, 200_000L, new BigDecimal("10.00"));

        AssetItem existingItem = AssetItem.create(1, USER_ID, "주식", "삼성전자", "005930",
                10, 1_000_000L, 1_100_000L, 100_000L, new BigDecimal("10.00"));
        given(assetItemRepository.findByAccountIdAndItemCode(1, "005930"))
                .willReturn(Optional.of(existingItem));

        assetService.saveOrUpdateItems(account, List.of(itemDto));

        then(assetItemRepository).should(never()).save(any());
        assertThat(existingItem.getQuantity()).isEqualTo(20);
        assertThat(existingItem.getValuationAmt()).isEqualTo(2_200_000L);
    }

    // ==================== getStocks ====================

    @Test
    @DisplayName("getStocks — accountNo 마스킹 확인: 앞3자리+*******+뒤4자리")
    void getStocks_accountNoIsMasked() {
        AssetAccount account = AssetAccount.create(USER_ID, BROKER_CODE, "12345678901", 1_000_000L, 0L);
        ReflectionTestUtils.setField(account, "id", 1);

        given(assetAccountRepository.findAllByUserId(USER_ID)).willReturn(List.of(account));
        given(assetItemRepository.findAllByAccountId(1)).willReturn(List.of());

        List<StockAccountResponse> result = assetService.getStocks(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).accountNo()).isEqualTo("123*******8901");
    }

    // ==================== syncAssetData ====================

    @Test
    @DisplayName("syncAssetData — 신규 계좌: assetAccountRepository.save() 호출, account_no 평문 set")
    void syncAssetData_신규계좌_정상저장() {
        given(assetAccountRepository.findByUserIdAndBrokerCode(USER_ID, BROKER_CODE))
                .willReturn(Optional.empty());
        AssetAccount saved = AssetAccount.create(USER_ID, BROKER_CODE, ACCOUNT_NO, 2_000_000L, 500_000L);
        ReflectionTestUtils.setField(saved, "id", 1);
        given(assetAccountRepository.save(any(AssetAccount.class))).willReturn(saved);

        assetService.syncAssetData(USER_ID, dto, List.of());

        ArgumentCaptor<AssetAccount> captor = ArgumentCaptor.forClass(AssetAccount.class);
        then(assetAccountRepository).should(times(1)).save(captor.capture());
        assertThat(captor.getValue().getAccountNo()).isEqualTo(ACCOUNT_NO);
    }

    @Test
    @DisplayName("syncAssetData — 기존 계좌: save 미호출, totalAsset·depositReceived 값 변경 확인")
    void syncAssetData_기존계좌_잔액업데이트() {
        AssetAccount existing = AssetAccount.create(USER_ID, BROKER_CODE, ACCOUNT_NO, 1_000_000L, 100_000L);
        given(assetAccountRepository.findByUserIdAndBrokerCode(USER_ID, BROKER_CODE))
                .willReturn(Optional.of(existing));

        assetService.syncAssetData(USER_ID, dto, List.of());

        then(assetAccountRepository).should(never()).save(any(AssetAccount.class));
        assertThat(existing.getTotalAsset()).isEqualTo(2_000_000L);
        assertThat(existing.getDepositReceived()).isEqualTo(500_000L);
    }

    @Test
    @DisplayName("syncAssetData — 신규 종목: assetItemRepository.save() 호출")
    void syncAssetData_신규종목_정상저장() {
        AssetAccount account = AssetAccount.create(USER_ID, BROKER_CODE, ACCOUNT_NO, 2_000_000L, 500_000L);
        ReflectionTestUtils.setField(account, "id", 1);
        given(assetAccountRepository.findByUserIdAndBrokerCode(USER_ID, BROKER_CODE))
                .willReturn(Optional.of(account));

        StockItemDto itemDto = new StockItemDto("주식", "삼성전자", "005930", 10,
                1_000_000L, 1_100_000L, 100_000L, new BigDecimal("10.00"));
        given(assetItemRepository.findByAccountIdAndItemCode(1, "005930")).willReturn(Optional.empty());

        assetService.syncAssetData(USER_ID, dto, List.of(itemDto));

        then(assetItemRepository).should(times(1)).save(any(AssetItem.class));
    }

    @Test
    @DisplayName("syncAssetData — 기존 종목: 기존 엔티티 필드 변경 확인, save 미호출")
    void syncAssetData_기존종목_수량업데이트() {
        AssetAccount account = AssetAccount.create(USER_ID, BROKER_CODE, ACCOUNT_NO, 2_000_000L, 500_000L);
        ReflectionTestUtils.setField(account, "id", 1);
        given(assetAccountRepository.findByUserIdAndBrokerCode(USER_ID, BROKER_CODE))
                .willReturn(Optional.of(account));

        StockItemDto itemDto = new StockItemDto("주식", "삼성전자", "005930", 20,
                2_000_000L, 2_200_000L, 200_000L, new BigDecimal("10.00"));
        AssetItem existingItem = AssetItem.create(1, USER_ID, "주식", "삼성전자", "005930",
                10, 1_000_000L, 1_100_000L, 100_000L, new BigDecimal("10.00"));
        given(assetItemRepository.findByAccountIdAndItemCode(1, "005930"))
                .willReturn(Optional.of(existingItem));

        assetService.syncAssetData(USER_ID, dto, List.of(itemDto));

        then(assetItemRepository).should(never()).save(any(AssetItem.class));
        assertThat(existingItem.getQuantity()).isEqualTo(20);
        assertThat(existingItem.getValuationAmt()).isEqualTo(2_200_000L);
    }

    @Test
    @DisplayName("syncAssetData — itemCode가 null인 종목은 skip, 정상 itemCode 종목만 save 호출")
    void syncAssetData_itemCode없음_skip() {
        AssetAccount account = AssetAccount.create(USER_ID, BROKER_CODE, ACCOUNT_NO, 1_000_000L, 0L);
        given(assetAccountRepository.findByUserIdAndBrokerCode(USER_ID, BROKER_CODE))
                .willReturn(Optional.of(account));

        StockItemDto noCode = new StockItemDto("채권", "국공채", null, 1,
                100_000L, 100_000L, 0L, BigDecimal.ZERO);
        StockItemDto normal = new StockItemDto("주식", "삼성전자", "005930", 10,
                1_000_000L, 1_100_000L, 100_000L, new BigDecimal("10.00"));
        given(assetItemRepository.findByAccountIdAndItemCode(any(), eq("005930"))).willReturn(Optional.empty());
        given(assetItemRepository.save(any(AssetItem.class))).willReturn(null);

        assetService.syncAssetData(USER_ID, dto, List.of(noCode, normal));

        // itemCode=null 종목은 skip → save 1회만 호출
        then(assetItemRepository).should(times(1)).save(any(AssetItem.class));
    }

    @Test
    @DisplayName("syncAssetData 완료 후 updateInvestableAmount가 1회 호출된다")
    void syncAssetData_완료_후_updateInvestableAmount_호출() {
        given(assetAccountRepository.findByUserIdAndBrokerCode(USER_ID, BROKER_CODE))
                .willReturn(Optional.of(AssetAccount.create(USER_ID, BROKER_CODE, ACCOUNT_NO, 2_000_000L, 500_000L)));

        assetService.syncAssetData(USER_ID, dto, List.of());

        then(assetService).should(times(1)).updateInvestableAmount(USER_ID);
    }

    @Test
    @DisplayName("syncAssetData — 특정 종목 DataIntegrityViolationException: 해당 종목만 skip, 나머지 정상 처리, 예외 미전파")
    void syncAssetData_종목중복예외_해당종목만스킵() {
        AssetAccount account = AssetAccount.create(USER_ID, BROKER_CODE, ACCOUNT_NO, 2_000_000L, 500_000L);
        ReflectionTestUtils.setField(account, "id", 1);
        given(assetAccountRepository.findByUserIdAndBrokerCode(USER_ID, BROKER_CODE))
                .willReturn(Optional.of(account));

        StockItemDto item1 = new StockItemDto("주식", "삼성전자", "005930", 10,
                1_000_000L, 1_100_000L, 100_000L, new BigDecimal("10.00"));
        StockItemDto item2 = new StockItemDto("주식", "SK하이닉스", "000660", 5,
                500_000L, 550_000L, 50_000L, new BigDecimal("10.00"));

        given(assetItemRepository.findByAccountIdAndItemCode(1, "005930")).willReturn(Optional.empty());
        given(assetItemRepository.findByAccountIdAndItemCode(1, "000660")).willReturn(Optional.empty());
        given(assetItemRepository.save(any(AssetItem.class)))
                .willThrow(new DataIntegrityViolationException("Duplicate"))
                .willReturn(null);

        assertThatNoException().isThrownBy(() -> assetService.syncAssetData(USER_ID, dto, List.of(item1, item2)));

        then(assetItemRepository).should(times(2)).save(any(AssetItem.class));
    }
}
