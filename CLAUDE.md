# FlowFin Server

## 프로젝트 개요

개인 금융 데이터(카드 지출, 소득, 증권)를 통합 분석하고 AI 기반 투자 포트폴리오를 추천하는 자산 관리 서비스의 백엔드 서버.

- **기술 스택**: Java 17 / Spring Boot 3.3.12 / JPA / Spring Security / JWT / MySQL 8.0 / OpenAI API / CODEF API
- **프론트엔드 레포**: `flowfin-client` (React 18 / Tailwind CSS)

---

## 팀

| 이름 | 담당 영역 |
|------|-----------|
| 오경택 | 회원/보안/CODEF 연동/AI 로직/CI·CD |
| 김진엽 | Spring Boot API 서버/DB 설계/커뮤니티 |

> 프론트엔드는 두 명이 공동 분담

---

## 아키텍처

```
com.project.flowfinserver/
├── controller/      # REST API 엔드포인트
├── service/         # 비즈니스 로직
├── repository/      # JPA Repository
├── dto/             # 요청/응답 DTO
├── domain/          # JPA Entity
├── config/          # @Configuration 설정 클래스만 (Security, JPA 등)
├── codef/           # CODEF API 호출 로직
├── openai/          # OpenAI API 호출 로직
├── validation/      # 커스텀 검증 로직
└── exception/       # 전역 예외처리, 공통 응답 포맷
```

**핵심 설계 결정**
- CODEF API 응답 데이터는 수신 즉시 AES-256 암호화 후 저장 (복호화는 서비스 레이어에서만)
- 지출 분류는 Rule-based 1차 처리 → 미분류 항목만 GPT-4 호출로 비용 최소화
- JWT는 Access Token (단기) + Refresh Token (DB 저장) 구조로 운용 (회원 인증 전용)
- 민감 금융 데이터 필드는 `@Convert(AttributeEncryptor)` JPA Converter로 투명 암호화

---

## 개발 명령어

```bash
./gradlew bootRun           # 서버 실행 (기본 포트 8080)
./gradlew test              # 전체 테스트 실행
./gradlew build             # 빌드 (테스트 포함)
./gradlew build -x test     # 테스트 스킵하고 빌드
```

---

## 환경 변수 / 시크릿

`.env` 또는 `application-local.yml`로 관리, **절대 커밋 금지**

```yaml
# 필수 환경 변수 목록
CODEF_CLIENT_ID:
CODEF_CLIENT_SECRET:
OPENAI_API_KEY:
JWT_SECRET:
AES_SECRET_KEY:
DB_URL:
DB_USERNAME:
DB_PASSWORD:
```

---

## 코드 컨벤션

- **패키지**: `com.project.flowfinserver.{패키지}` 구조 유지
- **클래스명**: 레이어 suffix 명시 (`UserController`, `UserService`, `UserRepository`)
- **DTO**: 요청/응답 분리 (`UserCreateRequest`, `UserResponse`)
- **예외**: 커스텀 예외 클래스 사용, `exception/` 패키지의 `GlobalExceptionHandler`에서 일괄 처리
- **테스트**: 서비스 레이어 단위 테스트 필수, 통합 테스트는 선택

---

## 브랜치 전략

### 브랜치 구조
main ← develop ← feature/*
                ← fix/*
← hotfix/* (main에서 분기, main + develop 동시 머지)

### 브랜치 규칙
- `main` 직접 push 금지 (배포 완료 상태만 유지)
- `develop` 직접 push 금지 (통합 브랜치)
- `feature/*` → `develop` PR 시 상대방 리뷰 1명 필수 승인 후 merge
- `develop` → `main` PR 시 2명 모두 확인 후 merge (배포 전 최종 검증)

### PR 제목 형식
[feat]  기능 추가       예) [feat] CODEF 카드 내역 연동
[fix]   버그 수정       예) [fix] JWT 만료 처리 오류
[refactor] 리팩토링    예) [refactor] 지출 분류 서비스 구조 개선
[chore] 빌드/설정      예) [chore] Docker 환경 변수 추가
[docs]  문서 수정       예) [docs] API 명세 업데이트

### 브랜치 네이밍
feature/codef-card-api
feature/jwt-auth
fix/card-category-null
hotfix/security-token-leak

## 현재 진행 상황

- [ ] CODEF API 연동 테스트
- [ ] DB 스키마 설계
- [ ] 회원/인증 모듈 (JWT + Spring Security)
- [ ] CODEF 카드 청구 내역 연동
- [ ] 지출 카테고리 자동 분류 (Rule-based)
- [ ] AI 분류 보완 (OpenAI GPT-4)
- [ ] 자산 분류 로직 (고정비/변동비/비정기)
- [ ] AI 투자 포트폴리오 추천
- [ ] 커뮤니티 CRUD
- [ ] CI/CD 파이프라인 (GitHub Actions → AWS EC2/RDS)

---

## 주의사항 / 결정된 규칙

- 테스트 없이 `main` merge 금지
- 금융 데이터 관련 엔티티는 반드시 암호화 적용 여부 리뷰 후 merge
- CODEF / OpenAI API 키는 코드에 하드코딩 절대 금지
- PR은 상대방 리뷰 필수, 본인 PR 본인 merge 금지
