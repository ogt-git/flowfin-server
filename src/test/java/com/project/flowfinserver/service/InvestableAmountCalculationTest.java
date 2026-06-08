package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.AssetAccount;
import com.project.flowfinserver.domain.Category;
import com.project.flowfinserver.domain.CategoryType;
import com.project.flowfinserver.domain.Expense;
import com.project.flowfinserver.domain.ManualAsset;
import com.project.flowfinserver.domain.ManualAssetType;
import com.project.flowfinserver.domain.ZeroReason;
import com.project.flowfinserver.dto.portfolio.InvestableAmountResult;
import com.project.flowfinserver.repository.AssetAccountRepository;
import com.project.flowfinserver.repository.AssetItemRepository;
import com.project.flowfinserver.repository.CategoryRepository;
import com.project.flowfinserver.repository.ExpenseRepository;
import com.project.flowfinserver.repository.ManualAssetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@ExtendWith(MockitoExtension.class)
class InvestableAmountCalculationTest {

    @Mock AssetAccountRepository assetAccountRepository;
    @Mock AssetItemRepository assetItemRepository;
    @Mock ManualAssetRepository manualAssetRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ExpenseRepository expenseRepository;

    @InjectMocks
    AssetService assetService;

    private static final Long USER_ID = 1L;

    // ==================== assetLinked=false ====================

    @Test
    @DisplayName("자산 계좌 미연동: assetLinked=false, amount=0, zeroReason=NONE")
    void calculateInvestableAmount_noAccounts_returnsNotLinked() {
        given(assetAccountRepository.findAllByUserId(USER_ID)).willReturn(List.of());

        InvestableAmountResult result = assetService.calculateInvestableAmount(USER_ID);

        assertThat(result.assetLinked()).isFalse();
        assertThat(result.amount()).isZero();
        assertThat(result.zeroReason()).isEqualTo(ZeroReason.NONE);
        assertThat(result.fixedCostMissing()).isFalse();
    }

    // ==================== 정상 케이스 ====================

    @Test
    @DisplayName("정상 케이스: 예수금+유동수동 - 고정비평균 - 비상금 = 양수 → amount>0, zeroReason=NONE")
    void calculateInvestableAmount_normalCase_returnsPositiveAmount() {
        // 예수금 1,000,000 + 유동수동 500,000 = 1,500,000
        // 고정비 월평균 200,000 / 비상금 200,000 → raw = 1,100,000
        AssetAccount account = AssetAccount.create(USER_ID, "0240", "acc1", 2_000_000L, 1_000_000L);
        given(assetAccountRepository.findAllByUserId(USER_ID)).willReturn(List.of(account));

        ManualAsset liquid = mock(ManualAsset.class);
        given(liquid.getAmount()).willReturn(500_000L);
        given(manualAssetRepository.findByUserIdAndAssetTypeIn(anyLong(), anyList()))
                .willReturn(List.of(liquid));

        Category fixedCat = mock(Category.class);
        given(fixedCat.getId()).willReturn(1L);
        given(categoryRepository.findByType(CategoryType.FIXED)).willReturn(List.of(fixedCat));

        // 1개월치 데이터(minusDays(15)), months=1, 월평균=200,000
        // 비상금=200,000 → raw = 1,500,000 - 200,000 - 200,000 = 1,100,000
        Expense exp1 = mock(Expense.class);
        given(exp1.getAmount()).willReturn(200_000L);
        given(exp1.getExpenseDate()).willReturn(LocalDateTime.now().minusDays(15));
        given(expenseRepository.findByUserIdAndCategoryIdInAndExpenseDateAfterAndIsExcludedFalse(anyLong(), anyList(), any()))
                .willReturn(List.of(exp1));

        InvestableAmountResult result = assetService.calculateInvestableAmount(USER_ID);

        assertThat(result.assetLinked()).isTrue();
        assertThat(result.amount()).isEqualTo(1_100_000L);
        assertThat(result.zeroReason()).isEqualTo(ZeroReason.NONE);
        assertThat(result.fixedCostMissing()).isFalse();
    }

    // ==================== CALCULATED_ZERO ====================

    @Test
    @DisplayName("예수금·유동수동 합이 0: zeroReason=CALCULATED_ZERO, amount=0")
    void calculateInvestableAmount_noLiquidAssets_returnsCalculatedZero() {
        AssetAccount account = AssetAccount.create(USER_ID, "0240", "acc1", 5_000_000L, 0L);
        given(assetAccountRepository.findAllByUserId(USER_ID)).willReturn(List.of(account));

        given(manualAssetRepository.findByUserIdAndAssetTypeIn(anyLong(), anyList()))
                .willReturn(List.of());

        given(categoryRepository.findByType(CategoryType.FIXED)).willReturn(List.of());

        InvestableAmountResult result = assetService.calculateInvestableAmount(USER_ID);

        assertThat(result.assetLinked()).isTrue();
        assertThat(result.amount()).isZero();
        assertThat(result.zeroReason()).isEqualTo(ZeroReason.CALCULATED_ZERO);
    }

    // ==================== CLAMPED ====================

    @Test
    @DisplayName("유동자산 있으나 고정비+비상금이 더 큰 경우: zeroReason=CLAMPED, amount=0")
    void calculateInvestableAmount_fixedCostExceedsLiquid_returnsClamped() {
        // 예수금 300,000 — 유동수동 없음
        // 고정비 평균 300,000 → 비상금 300,000 → raw = 300,000 - 600,000 = -300,000 → clamp
        AssetAccount account = AssetAccount.create(USER_ID, "0240", "acc1", 1_000_000L, 300_000L);
        given(assetAccountRepository.findAllByUserId(USER_ID)).willReturn(List.of(account));

        given(manualAssetRepository.findByUserIdAndAssetTypeIn(anyLong(), anyList()))
                .willReturn(List.of());

        Category fixedCat = mock(Category.class);
        given(fixedCat.getId()).willReturn(1L);
        given(categoryRepository.findByType(CategoryType.FIXED)).willReturn(List.of(fixedCat));

        // 최근 1개월 고정비 300,000 → 월평균 300,000
        Expense exp = mock(Expense.class);
        given(exp.getAmount()).willReturn(300_000L);
        given(exp.getExpenseDate()).willReturn(LocalDateTime.now().minusDays(15));
        given(expenseRepository.findByUserIdAndCategoryIdInAndExpenseDateAfterAndIsExcludedFalse(anyLong(), anyList(), any()))
                .willReturn(List.of(exp));

        InvestableAmountResult result = assetService.calculateInvestableAmount(USER_ID);

        assertThat(result.assetLinked()).isTrue();
        assertThat(result.amount()).isZero();
        assertThat(result.zeroReason()).isEqualTo(ZeroReason.CLAMPED);
        assertThat(result.fixedCostMissing()).isFalse();
    }

    // ==================== fixedCostMissing ====================

    @Test
    @DisplayName("고정비 데이터 없음: fixedCostMissing=true, 고정비 차감 없이 유동자산 전액 반환")
    void calculateInvestableAmount_noFixedExpenses_fixedCostMissingTrue() {
        // 유동자산 1,000,000 — 고정비 0 — 비상금 0 → amount = 1,000,000
        AssetAccount account = AssetAccount.create(USER_ID, "0240", "acc1", 2_000_000L, 1_000_000L);
        given(assetAccountRepository.findAllByUserId(USER_ID)).willReturn(List.of(account));

        given(manualAssetRepository.findByUserIdAndAssetTypeIn(anyLong(), anyList()))
                .willReturn(List.of());

        Category fixedCat = mock(Category.class);
        given(fixedCat.getId()).willReturn(1L);
        given(categoryRepository.findByType(CategoryType.FIXED)).willReturn(List.of(fixedCat));

        given(expenseRepository.findByUserIdAndCategoryIdInAndExpenseDateAfterAndIsExcludedFalse(anyLong(), anyList(), any()))
                .willReturn(List.of());

        InvestableAmountResult result = assetService.calculateInvestableAmount(USER_ID);

        assertThat(result.assetLinked()).isTrue();
        assertThat(result.fixedCostMissing()).isTrue();
        assertThat(result.amount()).isEqualTo(1_000_000L);
        assertThat(result.zeroReason()).isEqualTo(ZeroReason.NONE);
    }

    @Test
    @DisplayName("고정비 카테고리 자체가 없음(DB 미세팅): fixedCostMissing=true 처리")
    void calculateInvestableAmount_noFixedCategories_fixedCostMissingTrue() {
        AssetAccount account = AssetAccount.create(USER_ID, "0240", "acc1", 1_000_000L, 500_000L);
        given(assetAccountRepository.findAllByUserId(USER_ID)).willReturn(List.of(account));

        given(manualAssetRepository.findByUserIdAndAssetTypeIn(anyLong(), anyList()))
                .willReturn(List.of());

        given(categoryRepository.findByType(CategoryType.FIXED)).willReturn(List.of());

        InvestableAmountResult result = assetService.calculateInvestableAmount(USER_ID);

        assertThat(result.fixedCostMissing()).isTrue();
        assertThat(result.amount()).isEqualTo(500_000L);
    }

    // ==================== 3개월 미만 데이터 → 가용 기간 평균 ====================

    @Test
    @DisplayName("고정비 데이터가 2개월치뿐이면 가용 기간(2개월) 평균으로 월 고정비를 산출한다")
    void calculateInvestableAmount_twoMonthData_usesTwoMonthAverage() {
        // 예수금 600,000 — 유동수동 없음
        // 고정비 2건 합계 400,000, earliest = 2개월 + 1일 전
        // months = MONTHS.between(2개월+1일 전, 오늘) = 2
        // fixedMonthlyAvg = 400,000 / 2 = 200,000, 비상금 = 200,000
        // raw = 600,000 - 200,000 - 200,000 = 200,000
        AssetAccount account = AssetAccount.create(USER_ID, "0240", "acc1", 1_000_000L, 600_000L);
        given(assetAccountRepository.findAllByUserId(USER_ID)).willReturn(List.of(account));
        given(manualAssetRepository.findByUserIdAndAssetTypeIn(anyLong(), anyList())).willReturn(List.of());

        Category fixedCat = mock(Category.class);
        given(fixedCat.getId()).willReturn(1L);
        given(categoryRepository.findByType(CategoryType.FIXED)).willReturn(List.of(fixedCat));

        Expense recent = mock(Expense.class);
        given(recent.getAmount()).willReturn(200_000L);
        given(recent.getExpenseDate()).willReturn(LocalDateTime.now().minusDays(15));

        Expense older = mock(Expense.class);
        given(older.getAmount()).willReturn(200_000L);
        // 2개월 + 1일 전: MONTHS.between(today - 2m1d, today) = 2 보장
        given(older.getExpenseDate()).willReturn(LocalDateTime.now().minusMonths(2).minusDays(1));

        given(expenseRepository.findByUserIdAndCategoryIdInAndExpenseDateAfterAndIsExcludedFalse(anyLong(), anyList(), any()))
                .willReturn(List.of(recent, older));

        InvestableAmountResult result = assetService.calculateInvestableAmount(USER_ID);

        assertThat(result.assetLinked()).isTrue();
        assertThat(result.amount()).isEqualTo(200_000L);
        assertThat(result.fixedCostMissing()).isFalse();
        assertThat(result.zeroReason()).isEqualTo(ZeroReason.NONE);
    }

    // ==================== 비상금 미설정 → 고정비 1개월치 기본 적용 ====================

    @Test
    @DisplayName("비상금 미설정 시 고정비 1개월치가 비상금 기본값으로 적용된다 (CLAUDE.md 5.3)")
    void calculateInvestableAmount_emergencyFundDefaultsToOneMonthFixedCost() {
        // 예수금 800,000 — 유동수동 없음
        // 고정비 1개월치 200,000 (minusDays(15), months=1)
        // 비상금 = fixedMonthlyAvg = 200,000 (사용자 미설정 → 고정비 1개월치 기본값)
        // raw = 800,000 - 200,000 - 200,000 = 400,000
        AssetAccount account = AssetAccount.create(USER_ID, "0240", "acc1", 1_000_000L, 800_000L);
        given(assetAccountRepository.findAllByUserId(USER_ID)).willReturn(List.of(account));
        given(manualAssetRepository.findByUserIdAndAssetTypeIn(anyLong(), anyList())).willReturn(List.of());

        Category fixedCat = mock(Category.class);
        given(fixedCat.getId()).willReturn(1L);
        given(categoryRepository.findByType(CategoryType.FIXED)).willReturn(List.of(fixedCat));

        Expense exp = mock(Expense.class);
        given(exp.getAmount()).willReturn(200_000L);
        given(exp.getExpenseDate()).willReturn(LocalDateTime.now().minusDays(15));
        given(expenseRepository.findByUserIdAndCategoryIdInAndExpenseDateAfterAndIsExcludedFalse(anyLong(), anyList(), any()))
                .willReturn(List.of(exp));

        InvestableAmountResult result = assetService.calculateInvestableAmount(USER_ID);

        // fixedMonthlyAvg = 200,000, emergencyFund = 200,000
        assertThat(result.amount()).isEqualTo(400_000L);
        assertThat(result.fixedCostMissing()).isFalse();
        assertThat(result.zeroReason()).isEqualTo(ZeroReason.NONE);
    }

    // ==================== 결과 음수 → 0 clamp ====================

    @Test
    @DisplayName("investable_amount 음수 결과는 반드시 0으로 clamp")
    void calculateInvestableAmount_negativeRaw_isClampedToZero() {
        AssetAccount account = AssetAccount.create(USER_ID, "0240", "acc1", 500_000L, 100_000L);
        given(assetAccountRepository.findAllByUserId(USER_ID)).willReturn(List.of(account));

        given(manualAssetRepository.findByUserIdAndAssetTypeIn(anyLong(), anyList()))
                .willReturn(List.of());

        Category fixedCat = mock(Category.class);
        given(fixedCat.getId()).willReturn(1L);
        given(categoryRepository.findByType(CategoryType.FIXED)).willReturn(List.of(fixedCat));

        Expense exp = mock(Expense.class);
        given(exp.getAmount()).willReturn(1_000_000L);
        given(exp.getExpenseDate()).willReturn(LocalDateTime.now().minusDays(10));
        given(expenseRepository.findByUserIdAndCategoryIdInAndExpenseDateAfterAndIsExcludedFalse(anyLong(), anyList(), any()))
                .willReturn(List.of(exp));

        InvestableAmountResult result = assetService.calculateInvestableAmount(USER_ID);

        assertThat(result.amount()).isGreaterThanOrEqualTo(0L);
        assertThat(result.zeroReason()).isEqualTo(ZeroReason.CLAMPED);
    }
}
