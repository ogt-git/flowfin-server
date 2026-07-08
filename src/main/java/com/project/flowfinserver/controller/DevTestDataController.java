package com.project.flowfinserver.controller;

import com.project.flowfinserver.domain.Category;
import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.domain.Expense;
import com.project.flowfinserver.domain.ManualAsset;
import com.project.flowfinserver.domain.ManualAssetType;
import com.project.flowfinserver.domain.RiskType;
import com.project.flowfinserver.domain.User;
import com.project.flowfinserver.dto.codef.StockAssetDto;
import com.project.flowfinserver.dto.codef.StockItemDto;
import com.project.flowfinserver.repository.CategoryRepository;
import com.project.flowfinserver.repository.ExpenseRepository;
import com.project.flowfinserver.repository.ManualAssetRepository;
import com.project.flowfinserver.repository.UserRepository;
import com.project.flowfinserver.service.AssetService;
import com.project.flowfinserver.service.ExchangeRateService;
import com.project.flowfinserver.util.AesEncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Profile("!prod")
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DevTestDataController {

    private static final String TEST_EMAIL = "flowfin.demo.user@example.com";
    private static final String TEST_PASSWORD = "Flowfin123!";
    private static final String TEST_NAME = "FlowFin Demo User";
    private static final String TERMS_VERSION = "2026-07-01";
    private static final String BROKER_CODE = "DEMO";
    private static final String ACCOUNT_NO = "999900001234";
    private static final String USED_CARD = "DEMO-CARD";

    private final UserRepository userRepository;
    private final ManualAssetRepository manualAssetRepository;
    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final AssetService assetService;
    private final ExchangeRateService exchangeRateService;
    private final PasswordEncoder passwordEncoder;
    private final AesEncryptionUtil encryptionUtil;

    @PostMapping("/test-user")
    @Transactional
    public ResponseEntity<Map<String, Object>> seedTestUser() {
        User user = upsertUser();
        exchangeRateService.fetchWithFallback();
        seedManualAssets(user.getId());
        seedStockAssets(user.getId());
        seedExpenses(user.getId());

        return ResponseEntity.ok(Map.of(
                "email", TEST_EMAIL,
                "password", TEST_PASSWORD,
                "userId", user.getId(),
                "message", "Local test user and demo data are ready."
        ));
    }

    private User upsertUser() {
        String emailHash = encryptionUtil.hash(TEST_EMAIL);
        return userRepository.findByEmailHash(emailHash)
                .map(user -> {
                    user.updatePassword(passwordEncoder.encode(TEST_PASSWORD));
                    user.updateProfile(TEST_NAME, RiskType.MODERATE);
                    return user;
                })
                .orElseGet(() -> userRepository.save(User.create(
                        TEST_EMAIL,
                        emailHash,
                        passwordEncoder.encode(TEST_PASSWORD),
                        TEST_NAME,
                        RiskType.MODERATE,
                        TERMS_VERSION
                )));
    }

    private void seedManualAssets(Long userId) {
        List<String> existingNames = manualAssetRepository.findAllByUserId(userId).stream()
                .map(ManualAsset::getItemName)
                .toList();

        saveManualAssetIfAbsent(userId, existingNames, ManualAssetType.DEPOSIT,
                "Demo emergency deposit", 10_000_000L, 10_000_000L,
                LocalDate.of(2026, 7, 1), "Demo liquid asset");
        saveManualAssetIfAbsent(userId, existingNames, ManualAssetType.CASH,
                "Demo cash balance", 1_200_000L, 1_200_000L,
                LocalDate.of(2026, 7, 1), "Demo cash asset");
        saveManualAssetIfAbsent(userId, existingNames, ManualAssetType.SAVINGS,
                "Demo installment savings", 5_000_000L, 5_200_000L,
                LocalDate.of(2026, 1, 15), "Excluded from investable amount");
        saveManualAssetIfAbsent(userId, existingNames, ManualAssetType.PENSION,
                "Demo IRP pension", 3_000_000L, 3_150_000L,
                LocalDate.of(2025, 12, 1), "Excluded from investable amount");
    }

    private void saveManualAssetIfAbsent(Long userId, List<String> existingNames,
                                         ManualAssetType type, String itemName,
                                         Long purchaseAmount, Long amount,
                                         LocalDate purchaseDate, String memo) {
        if (existingNames.contains(itemName)) {
            return;
        }
        manualAssetRepository.save(ManualAsset.create(
                userId, type, itemName, purchaseAmount, amount, purchaseDate, memo));
    }

    private void seedStockAssets(Long userId) {
        long usdPurchase = exchangeRateService.convertToKrw(4_000L, "USD");
        long usdValuation = exchangeRateService.convertToKrw(4_300L, "USD");
        long jpyPurchase = exchangeRateService.convertToKrw(350_000L, "JPY");
        long jpyValuation = exchangeRateService.convertToKrw(366_000L, "JPY");
        long cnyPurchase = exchangeRateService.convertToKrw(18_000L, "CNY");
        long cnyValuation = exchangeRateService.convertToKrw(18_900L, "CNY");

        long totalValuation = 3_650_000L + 2_030_000L + usdValuation + jpyValuation + cnyValuation;

        var account = assetService.saveOrUpdateAccount(userId,
                new StockAssetDto(BROKER_CODE, ACCOUNT_NO, totalValuation + 2_500_000L, 2_500_000L));

        List<StockItemDto> demoItems = List.of(
                new StockItemDto("ETF", "Demo KOSPI 200 ETF", "DEMO-KOSPI200",
                        35, 3_500_000L, 3_650_000L, 150_000L, new BigDecimal("4.29")),
                new StockItemDto("BOND", "Demo short-term bond fund", "DEMO-BOND",
                        10, 2_000_000L, 2_030_000L, 30_000L, new BigDecimal("1.50")),
                new StockItemDto("FOREIGN_STOCK", "Demo USD asset - 4,300 USD converted to KRW", "DEMO-USD",
                        4, usdPurchase, usdValuation, usdValuation - usdPurchase, new BigDecimal("7.50")),
                new StockItemDto("FOREIGN_STOCK", "Demo JPY asset - 366,000 JPY converted to KRW", "DEMO-JPY",
                        366, jpyPurchase, jpyValuation, jpyValuation - jpyPurchase, new BigDecimal("4.57")),
                new StockItemDto("FOREIGN_STOCK", "Demo CNY asset - 18,900 CNY converted to KRW", "DEMO-CNY",
                        189, cnyPurchase, cnyValuation, cnyValuation - cnyPurchase, new BigDecimal("5.00"))
        );
        Set<String> demoItemCodes = demoItems.stream()
                .map(StockItemDto::itemCode)
                .collect(java.util.stream.Collectors.toSet());
        assetService.reconcileAndUpsertItems(account, demoItems, demoItemCodes);
    }

    private void seedExpenses(Long userId) {
        LocalDate baseMonth = LocalDate.now().withDayOfMonth(1);

        saveExpenseIfAbsent(userId, 1L, "Demo Rent", 850_000L,
                baseMonth.minusMonths(1).withDayOfMonth(25).atTime(LocalTime.NOON), "Demo Card");
        saveExpenseIfAbsent(userId, 3L, "Demo Mobile Plan", 69_000L,
                baseMonth.minusMonths(1).withDayOfMonth(10).atTime(LocalTime.NOON), "Demo Card");
        saveExpenseIfAbsent(userId, 5L, "Demo Lunch", 14_500L,
                baseMonth.withDayOfMonth(1).atTime(12, 30), "Demo Card");
        saveExpenseIfAbsent(userId, 6L, "Demo Grocery", 83_000L,
                baseMonth.withDayOfMonth(1).atTime(18, 20), "Demo Card");
        saveExpenseIfAbsent(userId, 7L, "Demo Transit", 3_200L,
                baseMonth.withDayOfMonth(1).atTime(8, 45), "Demo Card");
        saveExpenseIfAbsent(userId, 9L, "Demo Streaming", 17_000L,
                baseMonth.withDayOfMonth(1).atTime(22, 10), "Demo Card");
    }

    private void saveExpenseIfAbsent(Long userId, Long categoryId, String merchantName,
                                     Long amount, LocalDateTime expenseDate, String cardCompany) {
        if (expenseRepository.existsByUserIdAndExpenseDateAndMerchantNameAndAmountAndUsedCard(
                userId, expenseDate, merchantName, amount, USED_CARD)) {
            return;
        }

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalStateException("Missing category id: " + categoryId));
        expenseRepository.save(Expense.create(
                userId, cardCompany, USED_CARD, amount, merchantName, expenseDate,
                category, ClassifiedBy.RULE, 100));
    }
}
