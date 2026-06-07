# Stage 1: Build
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Gradle 의존성 캐시 레이어 분리 — 소스 변경 시 의존성 재다운로드 방지
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# src/ 복사 후 빌드
# NOTE: src/main/resources/application-prod.yml이 이미지에 포함됨 (비밀값은 없고 환경변수 플레이스홀더만 있음)
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Run
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

ENV TZ=Asia/Seoul
ENV SPRING_PROFILES_ACTIVE=prod

RUN groupadd -r app && useradd -r -g app app

COPY --from=build /app/build/libs/*.jar app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
