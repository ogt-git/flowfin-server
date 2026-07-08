package com.project.flowfinserver.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.flowfinserver.exception.KoreaEximBankException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class KoreaEximBankClient {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestTemplate restTemplate;

    @Value("${koreaexim.api.base-url}")
    private String baseUrl;

    @Value("${koreaexim.api.auth-key}")
    private String authKey;

    @Value("${koreaexim.api.data:AP01}")
    private String dataCode;

    public KoreaEximBankClient(@Qualifier("koreaEximRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 지정 날짜의 환율 목록을 조회한다.
     * authKey 미설정 → 빈 리스트 반환 (앱 기동/동기화 중단 없음)
     * result=2: 데이터 없음(공휴일·업데이트 전) → 빈 리스트 반환
     * result=3: 인증키 오류 → KoreaEximBankException
     * result=4: 일일 호출 한도 초과 → KoreaEximBankException
     */
    public List<ExchangeRateItemDto> fetchRates(LocalDate date) {
        if (authKey == null || authKey.isBlank()) {
            log.warn("[KoreaEximBank] KOREAEXIM_AUTH_KEY 미설정 — 환율 조회 skip");
            return Collections.emptyList();
        }

        // URL은 authKey를 포함하므로 로그에 절대 출력하지 않음
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/site/program/financial/exchangeJSON")
                .queryParam("authkey", authKey)
                .queryParam("searchdate", date.format(DATE_FMT))
                .queryParam("data", dataCode)
                .build(false)
                .toUriString();

        List<ExchangeRateItemDto> response;
        try {
            response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<ExchangeRateItemDto>>() {}
            ).getBody();
        } catch (Exception e) {
            // URL 자체를 로그에 남기지 않음 (authKey 노출 방지)
            log.error("[KoreaEximBank] API 호출 실패 date={} exceptionType={}", date, e.getClass().getSimpleName());
            throw new KoreaEximBankException("한국수출입은행 API 호출 실패", e);
        }

        if (response == null || response.isEmpty()) {
            log.info("[KoreaEximBank] 응답 없음 date={}", date);
            return Collections.emptyList();
        }

        // 단일 오류 아이템이면 result 코드로 판단
        if (response.size() == 1) {
            int resultCode = response.get(0).getResult();
            if (resultCode == 2) {
                log.info("[KoreaEximBank] 데이터 없음(공휴일 또는 업데이트 전) date={}", date);
                return Collections.emptyList();
            }
            if (resultCode == 3) {
                log.error("[KoreaEximBank] 인증키 오류(result=3) date={}", date);
                throw new KoreaEximBankException("한국수출입은행 인증키 오류 — 설정을 확인하세요 (result=3)");
            }
            if (resultCode == 4) {
                log.error("[KoreaEximBank] 일일 호출 한도 초과(result=4) date={}", date);
                throw new KoreaEximBankException("한국수출입은행 API 일일 호출 한도 초과 (result=4)");
            }
        }

        return response;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExchangeRateItemDto {
        @JsonProperty("result")
        private int result;

        @JsonProperty("cur_unit")
        private String curUnit;

        @JsonProperty("cur_nm")
        private String curNm;

        @JsonProperty("deal_bas_r")
        private String dealBasR;
    }
}
