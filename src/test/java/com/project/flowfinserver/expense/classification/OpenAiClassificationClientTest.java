package com.project.flowfinserver.expense.classification;

// 테스트 대상: OpenAiClassificationClient.classify(String merchantName, long amount)
// — 4xx 에러 재시도 없음, 5xx/429 재시도, 코드펜스 파싱, 범위 검증

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.flowfinserver.domain.Category;
import com.project.flowfinserver.domain.ClassifiedBy;
import com.project.flowfinserver.dto.ClassificationResult;
import com.project.flowfinserver.exception.OpenAiClassificationException;
import com.project.flowfinserver.openai.OpenAiClassificationClient;
import com.project.flowfinserver.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpenAiClassificationClientTest {

    @Mock RestTemplate openAiRestTemplate;
    @Mock CategoryRepository categoryRepository;

    private OpenAiClassificationClient client;

    private Category cat5;
    private Category fallbackCat;

    @BeforeEach
    void setUp() {
        client = new OpenAiClassificationClient(
                openAiRestTemplate, new ObjectMapper(), categoryRepository);

        ReflectionTestUtils.setField(client, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(client, "model", "gpt-4.1-nano");
        ReflectionTestUtils.setField(client, "confidenceThreshold", 60);
        ReflectionTestUtils.setField(client, "maxRetries", 3);

        cat5 = mock(Category.class);
        given(cat5.getId()).willReturn(5L);

        fallbackCat = mock(Category.class);
        given(fallbackCat.getId()).willReturn(11L);
    }

    private String buildOpenAiResponse(String content) {
        return "{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}";
    }

    // ==================== 정상 응답 ====================

    @Test
    @DisplayName("정상 응답(confidence>=60) 시 해당 카테고리의 ClassificationResult를 반환한다")
    void 정상_응답_시_ClassificationResult를_반환한다() {
        String responseBody = buildOpenAiResponse("{\\\"category_id\\\":5,\\\"confidence\\\":85}");
        given(openAiRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .willReturn(ResponseEntity.ok(responseBody));
        given(categoryRepository.findById(5L)).willReturn(Optional.of(cat5));

        ClassificationResult result = client.classify("스타벅스", 6500L);

        assertThat(result.getClassifiedBy()).isEqualTo(ClassifiedBy.AI);
        assertThat(result.getConfidence()).isEqualTo(85);
        assertThat(result.getCategory()).isSameAs(cat5);
    }

    @Test
    @DisplayName("confidence < 60이면 기타지출(11)로 fallback된다")
    void confidence_미달_시_기타지출로_fallback된다() {
        String responseBody = buildOpenAiResponse("{\\\"category_id\\\":5,\\\"confidence\\\":45}");
        given(openAiRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .willReturn(ResponseEntity.ok(responseBody));
        given(categoryRepository.findById(11L)).willReturn(Optional.of(fallbackCat));

        ClassificationResult result = client.classify("모호한가맹점", 10000L);

        assertThat(result.getCategory()).isSameAs(fallbackCat);
    }

    @Test
    @DisplayName("category_id가 0(범위 이탈)이면 11로 보정 후 기타지출을 반환한다")
    void category_id가_범위_이탈이면_11로_보정한다() {
        String responseBody = buildOpenAiResponse("{\\\"category_id\\\":0,\\\"confidence\\\":80}");
        given(openAiRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .willReturn(ResponseEntity.ok(responseBody));
        given(categoryRepository.findById(11L)).willReturn(Optional.of(fallbackCat));

        ClassificationResult result = client.classify("테스트가맹점", 5000L);

        assertThat(result.getCategory()).isSameAs(fallbackCat);
    }

    @Test
    @DisplayName("category_id가 12(범위 초과)이면 11로 보정한다")
    void category_id가_12이면_11로_보정한다() {
        String responseBody = buildOpenAiResponse("{\\\"category_id\\\":12,\\\"confidence\\\":80}");
        given(openAiRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .willReturn(ResponseEntity.ok(responseBody));
        given(categoryRepository.findById(11L)).willReturn(Optional.of(fallbackCat));

        ClassificationResult result = client.classify("테스트가맹점", 5000L);

        assertThat(result.getCategory()).isSameAs(fallbackCat);
    }

    @Test
    @DisplayName("JSON 코드펜스(```json)가 포함된 응답도 정상 파싱한다")
    void JSON_코드펜스_포함_응답을_정상_파싱한다() {

        // 직접 파싱 테스트를 위해 content 필드를 제어
        given(openAiRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .willReturn(ResponseEntity.ok(
                        "{\"choices\":[{\"message\":{\"content\":\"```json\\n{\\\"category_id\\\":5,\\\"confidence\\\":75}\\n```\"}}]}"));
        given(categoryRepository.findById(5L)).willReturn(Optional.of(cat5));

        ClassificationResult result = client.classify("스타벅스", 6500L);

        assertThat(result.getCategory()).isSameAs(cat5);
        assertThat(result.getConfidence()).isEqualTo(75);
    }

    // ==================== 4xx 에러 → 재시도 없음 ====================

    @Test
    @DisplayName("4xx(400) 에러 시 재시도 없이 OpenAiClassificationException을 던진다")
    void 에러_400_시_재시도_없이_예외를_던진다() {
        given(openAiRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.classify("스타벅스", 6500L))
                .isInstanceOf(OpenAiClassificationException.class);

        // 재시도 없음 → exchange 1회만 호출
        verify(openAiRestTemplate, times(1))
                .exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class));
    }

    @Test
    @DisplayName("4xx(401) 에러 시 재시도 없이 예외를 던진다")
    void 에러_401_시_재시도_없이_예외를_던진다() {
        given(openAiRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .willThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.classify("스타벅스", 6500L))
                .isInstanceOf(OpenAiClassificationException.class);

        verify(openAiRestTemplate, times(1))
                .exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class));
    }

    // ==================== 5xx 에러 → 재시도 ====================

    @Test
    @DisplayName("5xx 에러 3회 발생 시 3회 재시도 후 OpenAiClassificationException을 던진다")
    void 에러_500_3회_시_재시도_후_예외를_던진다() {
        given(openAiRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.classify("스타벅스", 6500L))
                .isInstanceOf(OpenAiClassificationException.class);

        // maxRetries=3 이므로 3회 호출
        verify(openAiRestTemplate, times(3))
                .exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class));
    }

    @Test
    @DisplayName("5xx 에러 2회 후 성공 시 정상 결과를 반환한다")
    void 에러_500_2회_후_성공_시_정상_결과를_반환한다() {
        String responseBody = buildOpenAiResponse("{\\\"category_id\\\":5,\\\"confidence\\\":85}");
        given(openAiRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
                .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
                .willReturn(ResponseEntity.ok(responseBody));
        given(categoryRepository.findById(5L)).willReturn(Optional.of(cat5));

        ClassificationResult result = client.classify("스타벅스", 6500L);

        assertThat(result.getClassifiedBy()).isEqualTo(ClassifiedBy.AI);
        verify(openAiRestTemplate, times(3))
                .exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class));
    }

    // ==================== 429 → 재시도 ====================

    @Test
    @DisplayName("429(TooManyRequests) 에러 3회 시 재시도 후 예외를 던진다")
    void 에러_429_3회_시_재시도_후_예외를_던진다() {
        given(openAiRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .willThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.classify("스타벅스", 6500L))
                .isInstanceOf(OpenAiClassificationException.class);

        verify(openAiRestTemplate, times(3))
                .exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class));
    }
}
