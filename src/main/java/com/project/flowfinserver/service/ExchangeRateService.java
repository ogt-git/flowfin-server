package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.ExchangeRate;
import com.project.flowfinserver.dto.exchangerate.ExchangeRateResponse;
import com.project.flowfinserver.exception.ExchangeRateUnavailableException;
import com.project.flowfinserver.exception.KoreaEximBankException;
import com.project.flowfinserver.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private static final String REDIS_KEY_PREFIX = "fx:rate:";
    // 금요일 11:10 갱신 기준, 다음 갱신(월요일 11:10)까지 최대 ~76시간 → 80h로 여유분 확보
    private static final long REDIS_TTL_HOURS = 80;
    private static final String PROVIDER = "한국수출입은행";
    private static final List<String> MANAGED_CURRENCIES = List.of("USD", "CNY", "JPY");

    private final ExchangeRateRepository exchangeRateRepository;
    private final KoreaEximBankClient koreEximBankClient;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 오늘부터 최대 7일 전까지 순차 fallback 조회하여 환율을 저장한다.
     * 스케줄러 및 운영 수동 갱신 진입점.
     */
    public void fetchWithFallback() {
        LocalDate today = LocalDate.now();
        for (int daysBack = 0; daysBack < 7; daysBack++) {
            LocalDate target = today.minusDays(daysBack);
            try {
                boolean fetched = fetchAndStoreForDate(target);
                if (fetched) {
                    log.info("[ExchangeRate] 환율 갱신 완료 date={}", target);
                    return;
                }
                log.info("[ExchangeRate] {} 데이터 없음 — 전날 시도", target);
            } catch (KoreaEximBankException e) {
                // 인증 오류, 한도 초과 등 날짜를 바꿔도 해결되지 않는 오류
                log.warn("[ExchangeRate] API 오류 — fallback 중단 error={}", e.getMessage());
                return;
            }
        }
        log.warn("[ExchangeRate] 최근 7일간 환율 데이터 없음 — 기존 캐시/DB 값 유지");
    }

    /**
     * 특정 날짜 환율을 조회하여 DB 저장 + Redis 갱신.
     *
     * @return true: 저장 성공, false: 해당 날짜 데이터 없음
     */
    @Transactional
    public boolean fetchAndStoreForDate(LocalDate date) {
        List<KoreaEximBankClient.ExchangeRateItemDto> items = koreEximBankClient.fetchRates(date);
        if (items.isEmpty()) {
            return false;
        }

        int savedCount = 0;
        LocalDateTime now = LocalDateTime.now();
        for (KoreaEximBankClient.ExchangeRateItemDto item : items) {
            String curUnit = item.getCurUnit();
            if (curUnit == null || curUnit.isBlank()) continue;

            String normalizedCode = normalizeApiCurrencyCode(curUnit);
            if (normalizedCode == null) continue;

            String rawRateStr = item.getDealBasR();
            if (rawRateStr == null || rawRateStr.isBlank()) continue;

            BigDecimal rate = parseRate(rawRateStr, curUnit);

            // 같은 통화/기준일 이미 존재하면 update, 없으면 insert (중복 저장 방지)
            ExchangeRate entity = exchangeRateRepository
                    .findByCurrencyCodeAndBaseDate(normalizedCode, date)
                    .orElse(null);
            if (entity != null) {
                entity.updateRate(rate, now, curUnit, rawRateStr);
            } else {
                entity = ExchangeRate.builder()
                        .currencyCode(normalizedCode)
                        .baseCurrency("KRW")
                        .rate(rate)
                        .baseDate(date)
                        .provider(PROVIDER)
                        .fetchedAt(now)
                        .originalCurrencyCode(curUnit)
                        .originalRateStr(rawRateStr)
                        .build();
            }
            exchangeRateRepository.save(entity);
            updateRedisCache(normalizedCode, rate);
            log.debug("[ExchangeRate] 저장 currency={} rate={} date={}", normalizedCode, rate, date);
            savedCount++;
        }

        log.info("[ExchangeRate] DB/Redis 갱신 완료 date={} 저장={}건", date, savedCount);
        return savedCount > 0;
    }

    /**
     * CODEF 외화 금액을 원화로 환산한다.
     * KRW/빈값/null → 그대로 반환
     * 알 수 없는 통화 → warn + 그대로 반환 (안전 fallback)
     * 환율 조회 실패 → warn + 그대로 반환 (CODEF 동기화 중단 방지)
     */
    public long convertToKrw(long amount, String rawCurrencyCode) {
        if (rawCurrencyCode == null || rawCurrencyCode.isBlank()) {
            return amount;
        }
        String upper = rawCurrencyCode.trim().toUpperCase();
        if ("KRW".equals(upper)) {
            return amount;
        }

        String normalizedCode = normalizeCurrencyCode(upper);
        if (normalizedCode == null) {
            log.warn("[FX] 알 수 없는 통화 — 원화 fallback rawCode={}", rawCurrencyCode);
            return amount;
        }

        BigDecimal rate = getLatestRate(normalizedCode);
        if (rate == null) {
            log.warn("[FX] 환율 정보 없음 — DB/Redis 모두 miss currency={}", normalizedCode);
            throw new ExchangeRateUnavailableException(normalizedCode);
        }

        return BigDecimal.valueOf(amount)
                .multiply(rate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    /**
     * 현재 적용 중인 환율 목록을 반환한다 (GET /api/market/exchange-rates 응답용).
     * Redis에 있으면 Redis 기준, 없으면 DB 최신값, 그것도 없으면 목록에서 제외.
     */
    @Transactional(readOnly = true)
    public List<ExchangeRateResponse> findAllLatestRates() {
        return MANAGED_CURRENCIES.stream()
                .map(code -> {
                    boolean fresh = stringRedisTemplate.opsForValue().get(REDIS_KEY_PREFIX + code) != null;
                    return exchangeRateRepository
                            .findFirstByCurrencyCodeOrderByFetchedAtDesc(code)
                            .map(e -> ExchangeRateResponse.from(e, !fresh))
                            .orElse(null);
                })
                .filter(r -> r != null)
                .toList();
    }

    // ---- internal helpers ----

    private BigDecimal getLatestRate(String currencyCode) {
        String cached = stringRedisTemplate.opsForValue().get(REDIS_KEY_PREFIX + currencyCode);
        if (cached != null) {
            return new BigDecimal(cached);
        }
        Optional<ExchangeRate> latest =
                exchangeRateRepository.findFirstByCurrencyCodeOrderByFetchedAtDesc(currencyCode);
        if (latest.isPresent()) {
            BigDecimal rate = latest.get().getRate();
            updateRedisCache(currencyCode, rate);
            log.info("[ExchangeRate] DB fallback 환율 사용 currency={} rate={}", currencyCode, rate);
            return rate;
        }
        return null;
    }

    private void updateRedisCache(String currencyCode, BigDecimal rate) {
        stringRedisTemplate.opsForValue()
                .set(REDIS_KEY_PREFIX + currencyCode, rate.toPlainString(), REDIS_TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * 한국수출입은행 API cur_unit → 내부 통화 코드 정규화.
     * 관심 통화가 아니면 null 반환.
     */
    private String normalizeApiCurrencyCode(String curUnit) {
        return switch (curUnit.toUpperCase().trim()) {
            case "USD" -> "USD";
            case "CNH" -> "CNY";
            case "JPY(100)" -> "JPY";
            default -> null;
        };
    }

    /**
     * CODEF resAccountCurrency → 내부 통화 코드 정규화.
     */
    private String normalizeCurrencyCode(String upper) {
        return switch (upper) {
            case "USD" -> "USD";
            case "CNH", "CNY" -> "CNY";
            case "JPY", "JPY(100)" -> "JPY";
            default -> null;
        };
    }

    /**
     * deal_bas_r 문자열(쉼표 포함 가능) → BigDecimal.
     * JPY(100)은 100엔 기준이므로 100으로 나눠 1엔당 환율로 반환.
     */
    private BigDecimal parseRate(String rawRateStr, String curUnit) {
        String cleaned = rawRateStr.replace(",", "").trim();
        BigDecimal raw = new BigDecimal(cleaned);
        if ("JPY(100)".equalsIgnoreCase(curUnit.trim())) {
            return raw.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        }
        return raw.setScale(4, RoundingMode.HALF_UP);
    }
}
