package com.project.flowfinserver.service;

import com.project.flowfinserver.domain.ExchangeRate;
import com.project.flowfinserver.exception.ExchangeRateUnavailableException;
import com.project.flowfinserver.exception.KoreaEximBankException;
import com.project.flowfinserver.repository.ExchangeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExchangeRateServiceTest {

    @Mock ExchangeRateRepository exchangeRateRepository;
    @Mock KoreaEximBankClient koreEximBankClient;
    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks
    ExchangeRateService service;

    @BeforeEach
    void setUp() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(anyString())).willReturn(null);
    }

    // ---- 파싱 관련 ----

    @Nested
    @DisplayName("환율 파싱")
    class ParsingTest {

        @Test
        @DisplayName("USD deal_bas_r 쉼표 포함 문자열 정상 파싱")
        void usd_with_comma() {
            // 1,395.62 → 1395.62
            given(koreEximBankClient.fetchRates(any())).willReturn(List.of(
                    item("USD", "1,395.62")
            ));
            given(exchangeRateRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            service.fetchAndStoreForDate(LocalDate.now());

            ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
            verify(exchangeRateRepository).save(captor.capture());
            assertThat(captor.getValue().getRate()).isEqualByComparingTo("1395.6200");
            assertThat(captor.getValue().getCurrencyCode()).isEqualTo("USD");
        }

        @Test
        @DisplayName("CNH → 내부 코드 CNY로 정규화")
        void cnh_normalized_to_cny() {
            given(koreEximBankClient.fetchRates(any())).willReturn(List.of(
                    item("CNH", "192.4500")
            ));
            given(exchangeRateRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            service.fetchAndStoreForDate(LocalDate.now());

            ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
            verify(exchangeRateRepository).save(captor.capture());
            assertThat(captor.getValue().getCurrencyCode()).isEqualTo("CNY");
            assertThat(captor.getValue().getOriginalCurrencyCode()).isEqualTo("CNH");
        }

        @Test
        @DisplayName("JPY(100) → 100으로 나눠 1엔당 환율로 저장")
        void jpy100_divided_by_100() {
            // 929.36은 100엔 기준 → 1엔당 9.2936
            given(koreEximBankClient.fetchRates(any())).willReturn(List.of(
                    item("JPY(100)", "929.36")
            ));
            given(exchangeRateRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            service.fetchAndStoreForDate(LocalDate.now());

            ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
            verify(exchangeRateRepository).save(captor.capture());
            assertThat(captor.getValue().getCurrencyCode()).isEqualTo("JPY");
            assertThat(captor.getValue().getRate()).isEqualByComparingTo("9.2936");
        }

        @Test
        @DisplayName("관심 없는 통화(EUR 등)는 저장하지 않음")
        void unknown_currency_skipped() {
            given(koreEximBankClient.fetchRates(any())).willReturn(List.of(
                    item("EUR", "1550.00")
            ));

            service.fetchAndStoreForDate(LocalDate.now());

            verify(exchangeRateRepository, never()).save(any());
        }
    }

    // ---- 날짜 fallback ----

    @Nested
    @DisplayName("날짜 fallback")
    class DateFallbackTest {

        @Test
        @DisplayName("오늘 데이터 없으면(빈 리스트) 전날 조회")
        void fallback_to_previous_day_when_empty() {
            // 오늘(daysBack=0) → 빈 리스트, 어제(daysBack=1) → 데이터 있음
            given(koreEximBankClient.fetchRates(eq(LocalDate.now()))).willReturn(List.of());
            given(koreEximBankClient.fetchRates(eq(LocalDate.now().minusDays(1)))).willReturn(List.of(
                    item("USD", "1395.00")
            ));
            given(exchangeRateRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            service.fetchWithFallback();

            verify(koreEximBankClient).fetchRates(LocalDate.now());
            verify(koreEximBankClient).fetchRates(LocalDate.now().minusDays(1));
            verify(exchangeRateRepository).save(any());
        }

        @Test
        @DisplayName("7일 모두 데이터 없으면 저장 없이 종료")
        void fallback_exhausted_no_save() {
            given(koreEximBankClient.fetchRates(any())).willReturn(List.of());

            service.fetchWithFallback();

            verify(exchangeRateRepository, never()).save(any());
        }

        @Test
        @DisplayName("API 오류(KoreaEximBankException) 발생 시 fallback 중단 — 날짜 변경 재시도 없음")
        void api_exception_stops_fallback() {
            given(koreEximBankClient.fetchRates(any()))
                    .willThrow(new KoreaEximBankException("인증키 오류"));

            // 예외가 전파되지 않고 조용히 중단되어야 함
            assertThatCode(() -> service.fetchWithFallback()).doesNotThrowAnyException();

            // 오류 발생 후 다른 날짜 재시도 없음
            verify(koreEximBankClient, times(1)).fetchRates(any());
        }
    }

    // ---- API 실패 fallback ----

    @Nested
    @DisplayName("환율 조회 fallback")
    class RateFallbackTest {

        @Test
        @DisplayName("Redis miss 시 DB의 마지막 성공 환율 사용")
        void redis_miss_falls_back_to_db() {
            ExchangeRate dbRate = stubExchangeRate("USD", "1390.0000");
            given(exchangeRateRepository.findFirstByCurrencyCodeOrderByFetchedAtDesc("USD")).willReturn(Optional.of(dbRate));

            long result = service.convertToKrw(100L, "USD");

            assertThat(result).isEqualTo(139000L);
            verify(valueOps).set(eq("fx:rate:USD"), eq("1390.0000"), anyLong(), any());
        }

        @Test
        @DisplayName("Redis에 값 있으면 Redis 환율 사용")
        void redis_hit_uses_cached_rate() {
            given(valueOps.get("fx:rate:USD")).willReturn("1400.0000");

            long result = service.convertToKrw(100L, "USD");

            assertThat(result).isEqualTo(140000L);
            verify(exchangeRateRepository, never()).findFirstByCurrencyCodeOrderByFetchedAtDesc(any());
        }

        @Test
        @DisplayName("환율 없으면(Redis miss + DB miss) ExchangeRateUnavailableException 발생")
        void no_rate_throws_exchange_rate_unavailable() {
            given(exchangeRateRepository.findFirstByCurrencyCodeOrderByFetchedAtDesc(anyString())).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.convertToKrw(500L, "USD"))
                    .isInstanceOf(ExchangeRateUnavailableException.class)
                    .hasMessageContaining("USD");
        }
    }

    // ---- convertToKrw 동작 ----

    @Nested
    @DisplayName("convertToKrw 통화 처리")
    class ConvertToKrwTest {

        @Test
        @DisplayName("KRW 통화는 환산 없이 그대로 반환")
        void krw_returns_as_is() {
            long result = service.convertToKrw(50000L, "KRW");
            assertThat(result).isEqualTo(50000L);
            verifyNoInteractions(exchangeRateRepository);
        }

        @Test
        @DisplayName("빈 문자열 통화는 원화로 간주하여 그대로 반환")
        void blank_currency_returns_as_is() {
            long result = service.convertToKrw(50000L, "");
            assertThat(result).isEqualTo(50000L);
        }

        @Test
        @DisplayName("null 통화는 원화로 간주하여 그대로 반환")
        void null_currency_returns_as_is() {
            long result = service.convertToKrw(50000L, null);
            assertThat(result).isEqualTo(50000L);
        }

        @Test
        @DisplayName("CNH는 내부적으로 CNY 환율 적용")
        void cnh_uses_cny_rate() {
            given(valueOps.get("fx:rate:CNY")).willReturn("192.0000");

            long result = service.convertToKrw(100L, "CNH");

            assertThat(result).isEqualTo(19200L);
        }

        @Test
        @DisplayName("JPY(100) 통화 코드도 JPY 환율 적용")
        void jpy100_currency_code_uses_jpy_rate() {
            given(valueOps.get("fx:rate:JPY")).willReturn("9.2936");

            long result = service.convertToKrw(1000L, "JPY(100)");

            // JPY(100)은 이미 1엔 기준으로 DB에 저장됨 — 1000 * 9.2936 = 9293.6 → 9294
            assertThat(result).isEqualTo(9294L);
        }

        @Test
        @DisplayName("알 수 없는 통화 — warn 후 원화 fallback")
        void unknown_currency_fallback() {
            long result = service.convertToKrw(100L, "GBP");
            assertThat(result).isEqualTo(100L);
        }

        @Test
        @DisplayName("USD 환산 결과가 Long 반올림으로 처리됨")
        void usd_amount_rounded_to_long() {
            // 1395.67 * 3 = 4187.01 → 4187
            given(valueOps.get("fx:rate:USD")).willReturn("1395.6700");

            long result = service.convertToKrw(3L, "USD");

            assertThat(result).isEqualTo(4187L);
        }

        @Test
        @DisplayName("JPY 1엔당 환율 적용 (100엔 기준이 아닌 1엔 기준)")
        void jpy_per_unit_rate_applied() {
            // DB에 JPY 환율이 9.2936(이미 1엔 기준으로 저장됨)
            given(valueOps.get("fx:rate:JPY")).willReturn("9.2936");

            long result = service.convertToKrw(1000L, "JPY");

            // 1000 * 9.2936 = 9293.6 → 9294
            assertThat(result).isEqualTo(9294L);
        }
    }

    // ---- 이미 원화 처리 기관 검증 (GROUP_A/GROUP_B) ----

    @Test
    @DisplayName("이미 원화인 기관 통화가 KRW면 exchangeRateService 호출 안 됨 — 중복 환산 없음")
    void krw_currency_no_rate_lookup() {
        // GROUP_A/GROUP_B 기관은 toKrw를 아예 호출하지 않도록 CodefSyncService가 분기함.
        // 여기서는 convertToKrw에 "KRW" 전달 시 repository 접근이 없음을 검증.
        service.convertToKrw(100_000L, "KRW");
        verifyNoInteractions(exchangeRateRepository);
        verifyNoInteractions(koreEximBankClient);
    }

    // ---- helpers ----

    private KoreaEximBankClient.ExchangeRateItemDto item(String curUnit, String dealBasR) {
        KoreaEximBankClient.ExchangeRateItemDto dto = new KoreaEximBankClient.ExchangeRateItemDto();
        ReflectionTestUtils.setField(dto, "result", 1);
        ReflectionTestUtils.setField(dto, "curUnit", curUnit);
        ReflectionTestUtils.setField(dto, "dealBasR", dealBasR);
        return dto;
    }

    private ExchangeRate stubExchangeRate(String currencyCode, String rate) {
        return ExchangeRate.builder()
                .currencyCode(currencyCode)
                .baseCurrency("KRW")
                .rate(new BigDecimal(rate))
                .baseDate(LocalDate.now())
                .provider("한국수출입은행")
                .fetchedAt(LocalDateTime.now())
                .build();
    }
}
