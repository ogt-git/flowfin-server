package com.project.flowfinserver.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.flowfinserver.domain.Category;
import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.dto.ClassificationResult;
import com.project.flowfinserver.exception.OpenAiClassificationException;
import com.project.flowfinserver.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OpenAiClassificationClient {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final int[] BACKOFF_MS = {0, 1000, 3000};

    private static final String SYSTEM_PROMPT = """
            너는 대한민국 카드 거래 가맹점명과 금액을 분석하여 11개 카테고리 중 하나로 분류하는 금융 AI다.
            입력된 가맹점명과 금액을 보고 가장 적합한 카테고리 ID를 선택하고, 0~100 사이의 신뢰도(confidence)를 정수로 반환하라.
            반드시 아래 JSON 형식 하나만 출력하고, 그 외의 설명·코드펜스·줄바꿈은 절대 포함하지 마라.

            카테고리 목록:
            - 1: 주거비 (FIXED) — 월세, 관리비, 전기/가스/수도/난방 요금
            - 2: 보험비 (FIXED) — 생명보험, 손해보험, 화재보험
            - 3: 통신비 (FIXED) — 휴대폰, 인터넷, 케이블TV, 알뜰폰
            - 4: 교육비 (FIXED) — 학원, 인강, 도서, 어학원, 과외
            - 5: 식비 (VARIABLE) — 외식, 배달, 카페, 편의점, 패스트푸드
            - 6: 생활비 (VARIABLE) — 대형마트, 생필품, 드럭스토어, 가구
            - 7: 교통비 (VARIABLE) — 대중교통, 택시, 주유, 주차, 통행료
            - 8: 의류비 (VARIABLE) — 의류, 신발, 가방, 액세서리
            - 9: 문화/여가비 (VARIABLE) — 영화, 공연, 여행, OTT, 게임, 취미용품
            - 10: 의료비 (ETC) — 병원, 약국, 한의원, 치과
            - 11: 기타지출 (ETC) — 위 10개에 속하지 않거나 판단이 어려운 경우, 경조사비

            응답 포맷 (이 JSON만 출력):
            {"category_id": 5, "confidence": 85}
            """;

    private final RestTemplate openAiRestTemplate;
    private final ObjectMapper objectMapper;
    private final CategoryRepository categoryRepository;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model.classification}")
    private String model;

    @Value("${openai.classification.confidence-threshold:60}")
    private int confidenceThreshold;

    @Value("${openai.classification.max-retries:3}")
    private int maxRetries;

    public OpenAiClassificationClient(
            @Qualifier("openAiRestTemplate") RestTemplate openAiRestTemplate,
            ObjectMapper objectMapper,
            CategoryRepository categoryRepository) {
        this.openAiRestTemplate = openAiRestTemplate;
        this.objectMapper = objectMapper;
        this.categoryRepository = categoryRepository;
    }

    public ClassificationResult classify(String merchantName, long amount) {
        String userContent = buildUserContent(merchantName, amount);
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userContent)
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0,
                "max_tokens", 50
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        String content = callWithRetry(requestBody, headers, merchantName);
        return parseAndBuildResult(content, merchantName);
    }

    private String callWithRetry(Map<String, Object> requestBody, HttpHeaders headers, String merchantName) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            if (BACKOFF_MS[attempt - 1] > 0) {
                try {
                    Thread.sleep(BACKOFF_MS[attempt - 1]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new OpenAiClassificationException("AI 분류 인터럽트 merchant=" + merchantName, ie);
                }
            }

            try {
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                ResponseEntity<String> response = openAiRestTemplate.exchange(
                        OPENAI_URL, HttpMethod.POST, entity, String.class);
                return extractContent(response.getBody());
            } catch (HttpClientErrorException e) {
                // 429 Too Many Requests — 재시도
                if (e.getStatusCode().value() == 429) {
                    log.warn("[OpenAI] 429 Too Many Requests attempt={}/{} merchant={}", attempt, maxRetries, merchantName);
                    lastException = e;
                } else {
                    // 4xx (401, 400 등) — 재시도 무의미
                    throw new OpenAiClassificationException(
                            "OpenAI 클라이언트 오류 status=" + e.getStatusCode() + " merchant=" + merchantName, e);
                }
            } catch (HttpServerErrorException | ResourceAccessException e) {
                log.warn("[OpenAI] 일시적 오류 attempt={}/{} merchant={} error={}", attempt, maxRetries, merchantName, e.getMessage());
                lastException = e;
            }
        }

        throw new OpenAiClassificationException(
                "OpenAI 분류 최종 실패 maxRetries=" + maxRetries + " merchant=" + merchantName, lastException);
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new OpenAiClassificationException("OpenAI 응답 파싱 실패: " + responseBody, e);
        }
    }

    private ClassificationResult parseAndBuildResult(String content, String merchantName) {
        try {
            String cleaned = content
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();

            JsonNode parsed = objectMapper.readTree(cleaned);
            int categoryId = parsed.path("category_id").asInt(-1);
            int confidence = parsed.path("confidence").asInt(0);

            if (categoryId < 1 || categoryId > 11) {
                log.warn("[OpenAI] 유효하지 않은 category_id={} → 11로 강제 merchant={}", categoryId, merchantName);
                categoryId = 11;
            }
            if (confidence < confidenceThreshold) {
                log.debug("[OpenAI] 신뢰도 미달 confidence={} threshold={} → 기타지출(11) merchant={}",
                        confidence, confidenceThreshold, merchantName);
                categoryId = 11;
            }

            Category category = categoryRepository.findById((long) categoryId)
                    .orElseGet(this::getFallbackCategory);

            return ClassificationResult.ofAi(category, confidence);
        } catch (OpenAiClassificationException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenAiClassificationException("OpenAI 응답 JSON 파싱 실패 content=" + content, e);
        }
    }

    private String buildUserContent(String merchantName, long amount) {
        return "{\"merchant_name\":\"" + merchantName.replace("\"", "\\\"") + "\",\"amount\":" + amount + "}";
    }

    private Category getFallbackCategory() {
        return categoryRepository.findById(11L)
                .orElseThrow(() -> new IllegalStateException("기타지출 카테고리(id=11)가 DB에 없습니다"));
    }
}
