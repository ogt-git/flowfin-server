package com.project.flowfinserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    @Bean("aiClassificationExecutor")
    public ThreadPoolTaskExecutor aiClassificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(80);   // burst 흡수용 (카드 동기화 시 건별 유입)
        executor.setThreadNamePrefix("ai-classify-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy()); // 거부 시 호출부에서 기타지출 동기 확정
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean("portfolioExecutor")
    public ThreadPoolTaskExecutor portfolioExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("portfolio-recommend-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(420); // 최악 실행시간(374s) + 여유 → GPT 재시도 중 서버 종료 시 완료 대기
        executor.initialize();
        return executor;
    }

    @Bean("openAiRestTemplate")
    public RestTemplate openAiRestTemplate(
            @Value("${openai.classification.timeout-ms:10000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
    }

    @Bean("openAiPortfolioRestTemplate")
    public RestTemplate openAiPortfolioRestTemplate(
            @Value("${openai.portfolio.timeout-ms:60000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
    }
}
