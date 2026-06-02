package com.project.flowfinserver.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.flowfinserver.dto.portfolio.PortfolioAiInput;
import com.project.flowfinserver.dto.portfolio.PortfolioAiResponse;
import com.project.flowfinserver.exception.OpenAiPortfolioException;
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
public class OpenAiPortfolioClient {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final int[] BACKOFF_MS = {0, 2000, 5000};

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model.portfolio}")
    private String model;

    public OpenAiPortfolioClient(
            @Qualifier("openAiPortfolioRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public PortfolioAiResponse recommend(PortfolioAiInput input) {
        String userMessage = serializeInput(input);
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", PortfolioPromptConstants.SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userMessage)
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.5
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        String content = callWithRetry(requestBody, headers);
        return parseResponse(content);
    }

    private String callWithRetry(Map<String, Object> requestBody, HttpHeaders headers) {
        int maxRetries = BACKOFF_MS.length;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            if (BACKOFF_MS[attempt - 1] > 0) {
                try {
                    Thread.sleep(BACKOFF_MS[attempt - 1]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new OpenAiPortfolioException("포트폴리오 추천 인터럽트", ie);
                }
            }

            try {
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                ResponseEntity<String> response = restTemplate.exchange(
                        OPENAI_URL, HttpMethod.POST, entity, String.class);
                return extractContent(response.getBody());
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    log.warn("[OpenAI-Portfolio] 429 Too Many Requests attempt={}/{}", attempt, maxRetries);
                    lastException = e;
                } else {
                    throw new OpenAiPortfolioException("OpenAI 클라이언트 오류 status=" + e.getStatusCode(), e);
                }
            } catch (HttpServerErrorException | ResourceAccessException e) {
                log.warn("[OpenAI-Portfolio] 일시적 오류 attempt={}/{} error={}", attempt, maxRetries, e.getMessage());
                lastException = e;
            }
        }

        throw new OpenAiPortfolioException("포트폴리오 추천 최종 실패 maxRetries=" + maxRetries, lastException);
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new OpenAiPortfolioException("OpenAI 응답 파싱 실패: " + responseBody, e);
        }
    }

    private PortfolioAiResponse parseResponse(String content) {
        try {
            String cleaned = content
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();
            return objectMapper.readValue(cleaned, PortfolioAiResponse.class);
        } catch (Exception e) {
            throw new OpenAiPortfolioException("포트폴리오 응답 JSON 파싱 실패 content=" + content, e);
        }
    }

    private String serializeInput(PortfolioAiInput input) {
        try {
            return objectMapper.writeValueAsString(input);
        } catch (Exception e) {
            throw new OpenAiPortfolioException("포트폴리오 추천 입력 직렬화 실패", e);
        }
    }
}
