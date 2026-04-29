package com.project.flowfinserver.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FlowFin API")
                        .description("개인 금융 데이터 통합 분석 서비스 API")
                        .version("v1"));
    }

    // X-User-Id 헤더를 모든 엔드포인트에 전역 파라미터로 추가 (JWT 통합 전 임시)
    @Bean
    public OperationCustomizer globalHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            operation.addParametersItem(
                    new Parameter()
                            .in("header")
                            .name("X-User-Id")
                            .description("사용자 ID (JWT 통합 전 임시 헤더)")
                            .required(false)
                            .example("1")
            );
            return operation;
        };
    }
}
