package com.project.flowfinserver.asset;

import com.project.flowfinserver.domain.AssetAccount;
import com.project.flowfinserver.domain.AssetItem;
import com.project.flowfinserver.domain.CategoryType;
import com.project.flowfinserver.dto.codef.StockAssetDto;
import com.project.flowfinserver.dto.codef.StockItemDto;
import com.project.flowfinserver.repository.*;
import com.project.flowfinserver.service.AssetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock AssetAccountRepository assetAccountRepository;
    @Mock AssetItemRepository assetItemRepository;
    @Mock ManualAssetRepository manualAssetRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ExpenseRepository expenseRepository;
    @Mock PortfolioRepository portfolioRepository;

    @Spy
    @InjectMocks
    AssetService assetService;

    private static final Long USER_ID = 1L;

    private StockAssetDto accountDto(String brokerCode, String accountNo) {
        return new StockAssetDto(brokerCode, accountNo, 1_000_000L, 500_000L);
    }

    private StockItemDto itemDto(String itemCode) {
        return new StockItemDto("주식", "삼성전자", itemCode, 10, 700_000L, 800_000L, 100_000L, new BigDecimal("14.28"));
    }

    // updateInvestableAmount 내부 의존성 stubbing (공통 셋업용 헬퍼)
    private void stubUpdateInvestable() {
        given(assetAccountRepository.findAllByUserId(USER_ID)).willReturn(List.of());
        given(manualAssetRepository.findByUserIdAndAssetTypeIn(eq(USER_ID), any())).willReturn(List.of());
        given(categoryRepository.findByType(CategoryType.FIXED)).willReturn(List.of());
        given(portfolioRepository.findTopByUserIdOrderByCreatedAtDesc(USER_ID)).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("계좌가 없으면 신규 생성하고 save가 1회 호출된다")
    void syncAssetData_신규계좌_정상저장() {
        // given
        given(assetAccountRepository.findByUserIdAndBrokerCode(USER_ID, "00001"))
                .willReturn(Optional.empty());
        AssetAccount mockAccount = AssetAccount.create(USER_ID, "00001", "1234567890", 1_000_000L, 500_000L);
        given(assetAccountRepository.save(any(AssetAccount.class))).willReturn(mockAccount);
        // itemCode null 이 아닌 정상 종목
        given(assetItemRepository.findByAccountIdAndItemCode(any(), eq("005930")))
                .willReturn(Optional.empty());
        stubUpdateInvestable();

        // when
        assetService.syncAssetData(USER_ID, accountDto("00001", "1234567890"), List.of(itemDto("005930")));

        // then — 계좌 save 1회 호출, account_no 평문 전달 (AES Converter가 DB 저장 시 암호화)
        ArgumentCaptor<AssetAccount> captor = ArgumentCaptor.forClass(AssetAccount.class);
        then(assetAccountRepository).should().save(captor.capture());
        assertThat(captor.getValue().getAccountNo()).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("계좌가 이미 존재하면 잔액이 업데이트된다")
    void syncAssetData_기존계좌_잔액업데이트() {
        // given
        AssetAccount existing = AssetAccount.create(USER_ID, "00001", "1234567890", 900_000L, 400_000L);
        given(assetAccountRepository.findByUserIdAndBrokerCode(USER_ID, "00001"))
                .willReturn(Optional.of(existing));
        given(assetItemRepository.findByAccountIdAndItemCode(any(), eq("005930")))
                .willReturn(Optional.empty());
        given(assetItemRepository.save(any())).willReturn(mock(AssetItem.class));
        stubUpdateInvestable();

        // when
        assetService.syncAssetData(USER_ID, accountDto("00001", "1234567890"), List.of(itemDto("005930")));

        // then — save 미호출 (기존 엔티티 업데이트), total_asset 변경 확인
        then(assetAccountRepository).should(never()).save(any());
        assertThat(existing.getTotalAsset()).isEqualTo(1_000_000L);
        assertThat(existing.getDepositReceived()).isEqualTo(500_000L);
    }

    @Test
    @DisplayName("종목이 없으면 신규 생성하고 save가 호출된다")
    void syncAssetData_신규종목_정상저장() {
        // given
        AssetAccount account = AssetAccount.create(USER_ID, "00001", "1234567890", 1_000_000L, 500_000L);
        given(assetAccountRepository.findByUserIdAndBrokerCode(USER_ID, "00001"))
                .willReturn(Optional.of(account));
        given(assetItemRepository.findByAccountIdAndItemCode(any(), eq("005930")))
                .willReturn(Optional.empty());
        given(assetItemRepository.save(any())).willReturn(mock(AssetItem.class));
        stubUpdateInvestable();

        // when
        assetService.syncAssetData(USER_ID, accountDto("00001", "1234567890"), List.of(itemDto("005930")));

        // then
        then(assetItemRepository).should().save(any(AssetItem.class));
    }

    @Test
    @DisplayName("종목이 이미 존재하면 수량/평가금액이 업데이트된다")
    void syncAssetData_기존종목_수량업데이트() {
        // given
        AssetAccount account = AssetAccount.create(USER_ID, "00001", "1234567890", 1_000_000L, 500_000L);
        given(assetAccountRepository.findByUserIdAndBrokerCode(USER_ID, "00001"))
                .willReturn(Optional.of(account));

        AssetItem existingItem = AssetItem.create(
                account.getId(), USER_ID, "주식", "삼성전자", "005930",
                5, 600_000L, 700_000L, 50_000L, new BigDecimal("8.33"));
        given(assetItemRepository.findByAccountIdAndItemCode(any(), eq("005930")))
                .willReturn(Optional.of(existingItem));
        stubUpdateInvestable();

        // when
        assetService.syncAssetData(USER_ID, accountDto("00001", "1234567890"), List.of(itemDto("005930")));

        // then — save 미호출, 필드 업데이트 확인
        then(assetItemRepository).should(never()).save(any());
        assertThat(existingItem.getQuantity()).isEqualTo(10);
        assertThat(existingItem.getValuationAmt()).isEqualTo(800_000L);
    }

    @Test
    @DisplayName("종목 저장 시 DataIntegrityViolationException이 발생하면 해당 종목만 skip하고 나머지는 정상 처리된다")
    void syncAssetData_종목중복예외_해당종목만스킵() {
        // given
        AssetAccount account = AssetAccount.create(USER_ID, "00001", "1234567890", 1_000_000L, 500_000L);
        given(assetAccountRepository.findByUserIdAndBrokerCode(USER_ID, "00001"))
                .willReturn(Optional.of(account));

        given(assetItemRepository.findByAccountIdAndItemCode(any(), eq("005930")))
                .willReturn(Optional.empty());
        given(assetItemRepository.findByAccountIdAndItemCode(any(), eq("000660")))
                .willReturn(Optional.empty());
        given(assetItemRepository.save(argThat(item -> item != null && "005930".equals(item.getItemCode()))))
                .willThrow(DataIntegrityViolationException.class);
        given(assetItemRepository.save(argThat(item -> item != null && "000660".equals(item.getItemCode()))))
                .willReturn(mock(AssetItem.class));
        stubUpdateInvestable();

        StockItemDto item1 = itemDto("005930");
        StockItemDto item2 = new StockItemDto("반도체", "SK하이닉스", "000660", 5, 200_000L, 220_000L, 20_000L, new BigDecimal("10.00"));

        // when — 예외 전파 없이 정상 종료
        assetService.syncAssetData(USER_ID, accountDto("00001", "1234567890"), List.of(item1, item2));

        // then — 두 번째 종목은 정상 저장
        then(assetItemRepository).should(times(2)).save(any());
    }

    @Test
    @DisplayName("itemCode가 null이면 해당 종목은 skip되고 나머지 종목은 정상 처리된다")
    void syncAssetData_itemCode없음_skip() {
        // given
        AssetAccount account = AssetAccount.create(USER_ID, "00001", "1234567890", 1_000_000L, 500_000L);
        given(assetAccountRepository.findByUserIdAndBrokerCode(USER_ID, "00001"))
                .willReturn(Optional.of(account));

        // itemCode null인 종목
        StockItemDto noCode = new StockItemDto("채권", "국공채", null, 1, 100_000L, 100_000L, 0L, BigDecimal.ZERO);
        // itemCode 정상 종목
        StockItemDto normal = itemDto("005930");
        given(assetItemRepository.findByAccountIdAndItemCode(any(), eq("005930")))
                .willReturn(Optional.empty());
        given(assetItemRepository.save(any())).willReturn(mock(AssetItem.class));
        stubUpdateInvestable();

        // when
        assetService.syncAssetData(USER_ID, accountDto("00001", "1234567890"), List.of(noCode, normal));

        // then — null 종목 skip, 정상 종목만 1회 save
        then(assetItemRepository).should(times(1)).save(any());
    }

    @Test
    @DisplayName("syncAssetData 완료 후 updateInvestableAmount가 호출된다")
    void syncAssetData_완료_후_updateInvestableAmount_호출() {
        // given
        AssetAccount account = AssetAccount.create(USER_ID, "00001", "1234567890", 1_000_000L, 500_000L);
        given(assetAccountRepository.findByUserIdAndBrokerCode(USER_ID, "00001"))
                .willReturn(Optional.of(account));
        stubUpdateInvestable();

        // when
        assetService.syncAssetData(USER_ID, accountDto("00001", "1234567890"), List.of());

        // then
        then(assetService).should().updateInvestableAmount(USER_ID);
    }
}
