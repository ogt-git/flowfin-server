# agent.md — FlowFin (플로우핀) Codex 지침서

> 코드 수정 전 반드시 기존 파일을 읽고, 확신 없으면 작업 중단 후 질문하라.
> 커밋·푸시는 명시적 요청 시에만.

---

## 프로젝트 개요

- **서비스**: 지출 분석 → 자산 통합 → AI 진단 → 포트폴리오 추천
- **스택**: Java 17 / Spring Boot 3.3.12 / JPA / Spring Security(JWT) / Redis / MySQL 8.0
- **프론트**: React 18 / Vite / Tailwind CSS / Recharts
- **외부 API**: CODEF(카드·증권), OpenAI(gpt-4.1-nano=지출분류, gpt-4o=포트폴리오)
- **인프라**: Docker / GitHub Actions / AWS EC2(t3.medium) + RDS

---

## 절대 금지 사항

1. **커밋·푸시 금지** — 사용자 명시 요청 없이 절대 실행하지 않음
2. **응답에 암호화 원문 노출 금지** — 계좌번호·카드번호·이메일은 DTO에서 마스킹 필수 (`MaskingUtil`)
3. **`is_user_modified=true` 지출의 카테고리 덮어쓰기 금지** — AI/배치/어떤 자동화도 불가
4. **Comment 하드 삭제 금지** — `is_deleted=true` 소프트 삭제만 허용, DELETE SQL 금지
5. **Expense 물리 삭제 금지** — `is_excluded=true`로 소프트 처리
6. **이메일 평문 DB 조회 금지** — `findByEmailHash(sha256(email))` 사용
7. **CODEF API 직접 호출 금지** — 일반조회=DB, 수동=5분 쿨다운, 배치=새벽 2시
8. **모델명 하드코딩 금지** — `application.yml`의 환경변수로 주입
9. **API URL·포트 하드코딩 금지** — `.env` / `application.yml`에서 참조
10. **HTML `<form>` 태그 금지** (프론트) — `onClick`/`onChange` 핸들러 사용
11. **Access Token을 localStorage/sessionStorage 저장 금지** — JS 메모리(전역변수)만 허용

---

## 보안 규칙

| 데이터 | 방식 | 비고 |
|--------|------|------|
| 비밀번호 | BCrypt (단방향) | strength ≥ 10 |
| 이메일 | SHA-256 해시 (`email_hash` UNIQUE) | 조회·중복확인 기준 |
| 계좌번호·connectedId | AES-256 양방향 (`@Convert`) | JPA Converter 자동 처리 |
| JWT Access Token | 30분, 메모리 저장 | `Authorization: Bearer` |
| JWT Refresh Token | Redis 1일, HttpOnly 쿠키 | RTR(Rotation) 적용 |

- 인증: `@AuthenticationPrincipal CustomUserDetails` 필수 (X-User-Id 헤더 신뢰 금지 → IDOR 취약점)
- 마스킹: 카드번호 `123456******7890` / 계좌번호 `123*******4567` / 이메일 `us***@example.com`

---

## DB 핵심 규칙 (v3.3)

### UNIQUE 제약 (누락 금지)
- **Expense**: `(user_id, expense_date, merchant_name, amount, used_card)`
- **Asset_Item**: `(account_id, item_code)`
- **User**: `email_hash` UNIQUE

### 주요 컬럼 타입
- 금액: `BIGINT` (Java: `Long`)
- 날짜: `DATETIME` → `LocalDateTime` (DATE 단독 사용 금지)
- Asset_Account/Asset_Item의 `id`: `INT` (Java: `Integer`)

### 카테고리 (11개 고정, 변경 금지)
```
1:주거비(FIXED) 2:보험비(FIXED) 3:통신비(FIXED) 4:교육비(FIXED)
5:식비(VARIABLE) 6:생활비(VARIABLE) 7:교통비(VARIABLE) 8:의류비(VARIABLE)
9:문화/여가비(VARIABLE) 10:의료비(ETC) 11:기타지출(ETC)
```

### Expense 필드 규칙
- `classified_by`: `RULE` / `AI` / `USER` 만 허용
- `category_confidence`: 0~100 정수, AI 분류 시에만 저장
- `is_user_modified=true` → 자동 분류 업데이트 차단
- `is_excluded=true` → 소프트 삭제 (물리 삭제 금지)
- `used_card` 빈값: NULL 아닌 `''` 저장 (MySQL UNIQUE에서 NULL은 별개 취급)

### Portfolio 필드 규칙
- `portfolio_risk_type`: 추천 당시 성향 스냅샷 (User.risk_type과 혼용 금지)
- `investable_amount`: BIGINT, 화면 표시용 수치 (GPT ratio 계산에 직접 사용 안 함)
- `recommended_assets`: JSON

---

## 핵심 비즈니스 로직

### 지출 분류 (하이브리드)
```
Rule-based 매칭 시도 → classified_by='RULE', confidence=100
  ↓ 실패
AI 호출 (gpt-4.1-nano) → classified_by='AI', confidence=모델값
  ↓ confidence < 60
기타지출(ID=11) fallback
```
- 1건 = 1 API 호출 + `@Async` 병렬 (배치 전송 아님)
- Redis 캐시: `category:merchant:{SHA-256해시}`, TTL 7일
- 파싱 실패 → 기타지출(ID=11) fallback

### investable_amount 산출
```
= (예수금 + 현금성자산) - 고정비 3개월 평균 - 비상금
→ 결과 < 0이면 0 반환 (음수 금지)
```

### CODEF 연동
- amount 우선순위: `resUsedAmount → resPaymentPrincipal → resPaymentAmt`
- 취소거래: 음수 금액 저장 (스킵 아님), 파이차트는 `amount > 0` 필터
- 날짜: yyyyMMdd만 제공 (시분초 없음)
- 오류 처리: 인증오류→updateAccount재시도 / 일시오류→3회재시도 / 한도초과→익일스킵 / 설정오류→is_active=false

### 배치 동기화
- 매일 새벽 2시, 실패 3회 → `is_active=false`
- `is_user_modified=true` 지출은 절대 덮어쓰기 금지

---

## 코드 컨벤션

### Java (백엔드)
- 클래스: PascalCase + 레이어 suffix (`UserController`, `UserService`, `UserRepository`)
- 패키지: `com.project.flowfinserver.{domain}` (도메인형)
- Controller → Service → Repository 엄격 분리
- DTO ↔ Entity 변환 필수, DTO 요청/응답 분리 (`UserCreateRequest`, `UserResponse`)
- `@Transactional` 서비스 레이어, 읽기전용은 `readOnly=true`
- 예외: `GlobalExceptionHandler`에서 일괄 처리, 커스텀 예외 클래스 사용
- `@Async` 메서드: 같은 클래스 내 호출 시 프록시 우회 → 별도 빈 분리 필수
- 트랜잭션 밖에서 Entity ID만 파라미터 전달 (Entity 객체 전달 금지)
- 카운트 연산: `List.size()` 금지 → COUNT 쿼리 사용

### React (프론트)
- 함수형 컴포넌트 + named export (default export 혼용 금지)
- 파일명: PascalCase(컴포넌트), camelCase(유틸·훅·API)
- `<form>` 태그 금지, `onClick`/`onChange` 핸들러 사용
- 환경변수: `import.meta.env.VITE_API_BASE_URL`
- Access Token: JS 메모리 전역변수, Refresh Token: HttpOnly 쿠키
- Axios 인터셉터: 401 시 자동 RTR 재발급

---

## API 공통 응답 포맷

```json
// 성공
{ "success": true, "data": { ... }, "message": "...", "timestamp": "..." }

// 실패
{ "success": false, "data": null, "message": "...", "errorCode": "EXPENSE_NOT_FOUND", "timestamp": "..." }
```

---

## Redis Key 패턴

```
refresh:token:{userId}                    TTL 1일
codef:refresh:cooldown:{userId}           TTL 5분
expense:stats:{userId}:{yyyyMM}           TTL 1시간
category:merchant:{merchantNameHash}      TTL 7일
```

---

## 테스트 원칙

- MockitoExtension만 사용, Spring 컨텍스트 로드 금지 (단위 테스트)
- src/main 수정 금지, 실제 메서드 시그니처 확인 후 작성
- `./gradlew test --continue`로 GREEN 확인
- 커밋·푸시 금지

---

## 브랜치 / PR

```
main → 운영 (직접 push 금지, PR only)
develop → 통합 개발
feature/* → 기능 개발
hotfix/* → 긴급 수정
```

PR 제목: `[add]`, `[fix]`, `[refactor]`, `[test]`, `[docs]`, `[chore]`

---

## 불확실하면 질문 (추측 금지)

- CODEF API 응답 필드 구조 미확인 시
- 포트폴리오 추천 비즈니스 정책 미정 시
- 수동 자산 입력 테이블 미확정 시 (v3.3 기준 미포함)
- 인프라 종속 설정 필요 시
