# CLAUDE.md — FlowFin 개발 지침서

> 이 파일은 Claude가 FlowFin 프로젝트의 코드를 작성할 때 반드시 준수해야 할 규칙과 컨텍스트를 정의한다.
> 모르는 부분은 추측하지 말고 반드시 질문으로 되물어라.

---

## 1. 프로젝트 개요

| 항목 | 내용 |
|------|------|
| 서비스명 | FlowFin (플로우핀) |
| 목적 | 지출 분석 → 자산 통합 → AI 진단 → 맞춤형 포트폴리오 제안 |
| 팀 규모 | 2인 (A: 회원/보안/CODEF/AI/DevOps, B: API서버/DB/커뮤니티) |
| 개발 기간 | 8주 (2주 단위 스프린트 × 4회) |
| 플랫폼 | 반응형 웹 (모바일 네이티브 앱 제외) |

---

## 2. 기술 스택

### Backend
```
Java 17
Spring Boot 3.3.12
Spring Security 6.x (JWT 기반)
Spring Data JPA + Hibernate
MySQL 8.0
Redis (토큰 관리, Rate Limiting, 캐싱)
```

### Frontend
```
React 18
Tailwind CSS
Recharts 또는 Chart.js (데이터 시각화)
```

### DevOps / 인프라
```
Docker
GitHub Actions (CI/CD)
AWS EC2 (t3.medium), RDS (MySQL 8.0)
HTTPS (SSL/TLS) 전구간 적용
```

### 외부 API
```
CODEF API — 카드 청구 내역, 증권 종합자산
OpenAI API — GPT-4 (지출 분석, 포트폴리오 추천, 카테고리 AI 분류)
```

---

## 3. 보안 규칙 (금융 서비스 수준 — 절대 예외 없음)

### 3.1 암호화 매트릭스

| 데이터 | 암호화 방식 | 비고 |
|--------|------------|------|
| 비밀번호 | BCrypt (단방향) | strength=10 이상 |
| 이메일 | SHA-256 해시 저장 (`email_hash` 컬럼) | 중복 확인 인덱스용 |
| `connected_id` | AES-256 양방향 암호화 | JPA `@Convert` 자동 처리 |
| `account_number` (카드·증권 계좌번호) | AES-256 양방향 암호화 | JPA `@Convert` 자동 처리 |
| 전송 구간 | HTTPS (SSL/TLS) | 모든 API 엔드포인트 |

### 3.2 AES-256 JPA Converter 패턴 (반드시 이 방식 사용)

```java
@Converter
public class AesEncryptConverter implements AttributeConverter<String, String> {

    @Value("${encrypt.aes.secret-key}")
    private String secretKey;

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) return null;
        return AesUtil.encrypt(plainText, secretKey); // 암호화 후 저장
    }

    @Override
    public String convertToEntityAttribute(String cipherText) {
        if (cipherText == null) return null;
        return AesUtil.decrypt(cipherText, secretKey); // 조회 시 복호화
    }
}
```

엔티티 적용 예:
```java
// Codef_Connection 엔티티
@Convert(converter = AesEncryptConverter.class)
@Column(name = "connected_id")
private String connectedId;

@Convert(converter = AesEncryptConverter.class)
@Column(name = "account_number")
private String accountNumber;

// Asset_Account 엔티티
@Convert(converter = AesEncryptConverter.class)
@Column(name = "account_no", unique = true)
private String accountNo;
```

### 3.3 JWT 인증

- **Access Token**: 단기 (권장 30분), HTTP 헤더 `Authorization: Bearer {token}`
- **Refresh Token**: Redis 저장, 1일(86400초), 재발급 시 기존 토큰 즉시 무효화
- Refresh Token 탈취 대비: RTR(Rotation) 방식 적용 (재발급 시 이전 토큰 블랙리스트 처리)
- JWT 만료 시 `401 Unauthorized` 반환, 클라이언트가 Refresh Token으로 재발급 요청

> ⚠️ **User 테이블에 `refresh_token`, `token_expired_at` 컬럼이 존재하나, 실제 토큰 유효성 검증은 Redis 기준으로 처리한다. DB 컬럼은 보조 수단(장애 시 폴백용)으로만 사용.**

### 3.4 금융 데이터 마스킹 (API 응답 시 필수)

> ⚠️ **응답 DTO에서 반드시 마스킹 처리 — DB 저장 값을 그대로 반환하지 말 것**

```java
// 카드번호: 앞 6자리 + ****** + 뒤 4자리
"card_number": "123456******7890"

// 계좌번호: 앞 3자리 + ******* + 뒤 4자리
        "account_number": "123*******4567"

// 이메일: 로컬파트 2자리 + *** + @도메인
        "email": "us***@example.com"
```

마스킹 유틸 메서드를 `MaskingUtil` 클래스로 일원화하여 DTO 변환 시점에 적용한다.

---

## 4. DB 정합성 및 제약 조건

### 4.1 테이블 전체 스펙 (DB 명세서 v3.3 기준 — 반드시 이 스펙을 우선으로 따를 것)

#### User
| 컬럼 | 타입 | Key | 설명 |
|------|------|-----|------|
| id | BIGINT | PK | 사용자 ID |
| email | VARCHAR(100) | | 이메일 원문 |
| email_hash | VARCHAR(64) | UNIQUE | 이메일 SHA-256 해시 (조회·중복확인 기준) |
| password | VARCHAR(255) | | BCrypt 암호화 비밀번호 |
| name | VARCHAR(30) | | 이름 |
| refresh_token | VARCHAR(500) | | Refresh Token (보조용, 주 검증은 Redis) |
| token_expired_at | DATETIME | | 토큰 만료일시 |
| created_at | DATETIME | | 가입일시 |

#### Codef_Connection
| 컬럼 | 타입 | Key | 설명 |
|------|------|-----|------|
| id | BIGINT | PK | 연결 정보 ID |
| user_id | BIGINT | FK(User.id) | 사용자 ID |
| connected_id | VARCHAR(255) | | CODEF 발급 연결 ID (AES-256 암호화) |
| organization_code | VARCHAR(100) | | 금융기관 코드 |
| account_type | VARCHAR(50) | | 계좌 유형 (`CARD` / `STOCK`) |
| account_number | VARCHAR(50) | | 카드번호 또는 증권 계좌번호 (AES-256 암호화) |
| is_active | BOOLEAN | | 활성화 여부 (default: true) |
| created_at | DATETIME | | 생성 일시 |

#### Category
| 컬럼 | 타입 | Key | 설명 |
|------|------|-----|------|
| id | INT | PK | 카테고리 ID |
| name | VARCHAR(50) | | 카테고리명 |
| type | ENUM('FIXED','VARIABLE','ETC') | | 고정비/변동비/비정기 |
| created_at | DATETIME | | 생성 일시 |

#### Expense
| 컬럼 | 타입 | Key | 설명 |
|------|------|-----|------|
| id | BIGINT | PK | 거래 ID |
| user_id | BIGINT | FK(User.id) | 사용자 |
| card_company | VARCHAR(50) | | 카드사 |
| amount | BIGINT | | 금액 |
| merchant_name | VARCHAR(255) | | 가맹점명 |
| expense_date | DATETIME | | 거래일시 (CODEF 응답 파싱, LocalDateTime 매핑) |
| category_id | INT | FK(Category.id) | 카테고리 |
| classified_by | VARCHAR(10) | | 분류 주체 (`RULE` / `AI` / `USER`) |
| category_confidence | INT | | AI 분류 신뢰도 (0~100, AI 분류 시에만 저장) |
| is_user_modified | BOOLEAN | | 사용자 수동 수정 여부 |
| is_excluded | BOOLEAN | | 지출 제외 여부 (소프트 삭제, default: false) |
| created_at | DATETIME | | 생성일시 |
| * UNIQUE | | (user_id, expense_date, merchant_name, amount) | 중복 거래 방지 |

#### Asset_Account
| 컬럼 | 타입 | Key | 설명 |
|------|------|-----|------|
| id | INT | PK | 계좌 ID |
| user_id | BIGINT | FK(User.id) | 사용자 |
| broker_code | VARCHAR(20) | | 증권사 코드 (resOrganization) |
| account_no | VARCHAR(50) | | 계좌번호 (AES-256 암호화 저장) |
| total_asset | BIGINT | | 총 자산 |
| deposit_received | BIGINT | | 예수금 (resDepositReceived) |
| updated_at | DATETIME | | 업데이트 일시 |

#### Asset_Item
| 컬럼 | 타입 | Key | 설명 |
|------|------|-----|------|
| id | INT | PK | 종목 ID |
| account_id | INT | FK(Asset_Account.id) | 계좌 |
| user_id | BIGINT | FK(User.id) | 사용자 |
| product_type | VARCHAR(50) | | 상품유형 (resProductType) |
| item_name | VARCHAR(100) | | 종목명 (resItemName) |
| item_code | VARCHAR(20) | | 종목코드 (resItemCode) |
| quantity | INT | | 보유수량 (resQuantity) |
| purchase_amount | BIGINT | | 평균매입금액 (resPurchaseAmount) |
| valuation_amt | BIGINT | | 평가금액 (resValuationAmt) |
| valuation_pl | BIGINT | | 평가손익 (resValuationPL) |
| earnings_rate | DECIMAL(5,2) | | 수익률 (resEarningsRate) |
| updated_at | DATETIME | | 업데이트 일시 |
| * UNIQUE | | (account_id, item_code) | 계좌 내 동일 종목 중복 방지 |

#### Portfolio
| 컬럼 | 타입 | Key | 설명 |
|------|------|-----|------|
| id | INT | PK | 포트폴리오 ID |
| user_id | BIGINT | FK(User.id) | 사용자 |
| risk_type | VARCHAR(20) | | 위험 성향 |
| recommended_assets | JSON | | 추천 자산 배분 (파이 차트 시각화용 구조화 필수) |
| investable_amount | BIGINT | | 투자 가능 금액 (산출 후 저장) |
| created_at | DATETIME | | 생성일시 |

#### Community
| 컬럼 | 타입 | Key | 설명 |
|------|------|-----|------|
| id | BIGINT | PK | 게시글 ID |
| user_id | BIGINT | FK(User.id) | 작성자 |
| title | VARCHAR(255) | | 제목 |
| content | TEXT | | 내용 |
| category | VARCHAR(20) | | 게시글 카테고리 |
| views | INT | | 조회수 |
| like_count | INT | | 좋아요 수 |
| created_at | DATETIME | | 작성일시 |
| updated_at | DATETIME | | 수정일시 |

#### Community_Like
| 컬럼 | 타입 | Key | 설명 |
|------|------|-----|------|
| id | BIGINT | PK | 좋아요 ID |
| user_id | BIGINT | FK(User.id) | 사용자 |
| community_id | BIGINT | FK(Community.id) | 게시글 |
| * UNIQUE | | (user_id, community_id) | 중복 좋아요 방지 |

#### Comment
| 컬럼 | 타입 | Key | 설명 |
|------|------|-----|------|
| id | BIGINT | PK | 댓글 ID |
| community_id | BIGINT | FK(Community.id) | 게시글 |
| user_id | BIGINT | FK(User.id) | 작성자 |
| content | TEXT | | 댓글 내용 |
| is_anonymous | BOOLEAN | | 익명 여부 |
| is_deleted | BOOLEAN | | 삭제 여부 (소프트 삭제 전용) |
| created_at | DATETIME | | 작성일시 |

---

### 4.2 UNIQUE 제약 조건 (절대 누락 금지)

```sql
-- Expense 중복 거래 방지
ALTER TABLE expense
    ADD UNIQUE KEY uq_expense (user_id, expense_date, merchant_name, amount);

-- Community_Like 중복 좋아요 방지
ALTER TABLE community_like
    ADD UNIQUE KEY uq_like (user_id, community_id);

-- 계좌 내 동일 종목 중복 생성 방지 (Upsert 기준점)
ALTER TABLE asset_item
    ADD UNIQUE KEY uq_asset_item (account_id, item_code);
```

JPA에서 중복 삽입 시 `DataIntegrityViolationException`을 반드시 catch하여 비즈니스 예외로 변환한다.

### 4.3 테이블별 핵심 규칙

**Expense 테이블**
- `classified_by` ENUM: `RULE` / `AI` / `USER` (다른 값 사용 금지)
- `category_confidence`: 0~100 정수, AI 분류 시에만 저장 (Rule/USER는 null 또는 100)
- `is_user_modified`: 사용자가 카테고리를 직접 변경한 경우에만 `true`
- `is_excluded`: 지출 제외 처리 시 `true` (대납·법인카드 등), **DB 물리 삭제 금지**
- `expense_date`: **DATETIME 타입 (LocalDateTime)**, CODEF 응답의 거래일시를 파싱하여 저장, 중복 구별 시키기 위한 용도로 시간 타임 확인

**Codef_Connection 테이블**
- `account_type`: `CARD` / `STOCK` 두 값만 허용
- `connected_id`, `account_number`: 모두 AES-256 암호화 저장 필수
- `is_active`: 연동 해지 시 `false`로 soft delete (CODEF `deleteAccount` 호출 후 변경)

**Category 테이블**
- 아래 11개 카테고리는 시스템 기본값으로 초기 데이터 삽입 후 변경 금지
- `type` ENUM: `FIXED`(고정비) / `VARIABLE`(변동비) / `ETC`(비정기)
- DB 명세서 기준 `parent_id` 컬럼 없음 — 별도 계층 구조 사용 금지

| ID | 카테고리명 | type |
|----|-----------|------|
| 1 | 주거비 | FIXED |
| 2 | 보험비 | FIXED |
| 3 | 통신비 | FIXED |
| 4 | 교육비 | FIXED |
| 5 | 식비 | VARIABLE |
| 6 | 생활비 | VARIABLE |
| 7 | 교통비 | VARIABLE |
| 8 | 의류비 | VARIABLE |
| 9 | 문화/여가비 | VARIABLE |
| 10 | 의료비 | ETC |
| 11 | 기타지출 | ETC |

**Portfolio 테이블**
- 컬럼명은 `investable_amount` (BIGINT) — `total_invest_amount`로 혼용 금지
- `recommended_assets`: JSON 타입, 파이 차트 파싱 용이하도록 구조화 저장

**Community 테이블**
- `like_count`는 `Community_Like` 삽입/삭제 시 함께 업데이트 (정합성 유지)
- `views`는 조회 API 호출 시 +1 증가, 본인 게시글 조회도 카운트

**Comment 테이블**
- 삭제는 소프트 삭제만 허용: `is_deleted = true` 설정 (**DELETE SQL 사용 금지**)
- `is_deleted = true`인 댓글은 응답 시 "삭제된 댓글입니다" 텍스트로 대체

### 4.4 타입 기준
- 금액: `BIGINT` (Java: `Long`) 또는 정밀도 필요 시 `DECIMAL` / `BigDecimal`
- 날짜/시간: `DATETIME` → `LocalDateTime` (프로젝트 전체 DATETIME 기준, DATE 단독 사용 없음)
- Asset_Account/Asset_Item의 `id`: `INT` (Java: `Integer`)
- UNIQUE KEY, 인덱스, 제약 조건 DDL에 반드시 포함

> ⚠️ **수동 자산 입력(예금·적금·부동산·현금·기타)은 DB 명세서 v3.3 기준 별도 테이블 미정의 상태. 구현 전 반드시 테이블 설계 확정 후 진행할 것 — 임의로 Asset 테이블을 생성하지 말 것.**

---

## 5. 핵심 비즈니스 로직

### 5.1 지출 카테고리 분류 — 하이브리드 방식

```
[카드 청구 내역 수신]
        ↓
[Rule-based 분류 시도]
 - keyword_rule 테이블에서 merchant_name 키워드 매핑
 - 매칭 성공 → classified_by = 'RULE', confidence = 100
        ↓ (매칭 실패 또는 confidence < 임계값)
[GPT-4 AI 분류 호출]
 - 가맹점명 + 금액을 프롬프트로 전달
 - 11개 카테고리 중 선택 + confidence 반환
 - classified_by = 'AI', category_confidence = GPT 응답값
        ↓
[DB 저장]
```

**AI 분류 임계값**: `category_confidence < 60` → 기타지출(ID=11)로 fallback 처리

**프롬프트 기본 형식**:
```
다음 카드 거래를 아래 카테고리 중 하나로 분류하고 신뢰도(0~100)를 JSON으로 반환하라.
가맹점: {merchant_name}, 금액: {amount}원
카테고리: [주거비, 보험비, 통신비, 교육비, 식비, 생활비, 교통비, 의류비, 문화/여가비, 의료비, 기타지출]
응답 형식: {"category": "식비", "confidence": 85}
```

**AI 분류 비동기 처리**: Spring `@Async` 사용 (RabbitMQ는 이번 프로젝트 범위 제외)

### 5.2 ⚠️ 사용자 수동 수정 시 AI 덮어쓰기 방지 (CRITICAL)

```java
// 배치 동기화 또는 AI 재분류 시 반드시 아래 조건 확인
if (expense.isUserModified()) {
        // is_user_modified = true 이면 category_id, classified_by 절대 변경 금지
        return;
        }
// is_user_modified = false 일 때만 분류 업데이트 허용
        expense.updateCategory(newCategoryId, ClassifiedBy.AI, confidence);
```

> 사용자가 직접 수정한 카테고리는 배치 재동기화, AI 재분류, 어떤 자동화 로직도 덮어쓸 수 없다.

### 5.3 투자 가능 금액(investable_amount) 산출

```
investable_amount = 투자가능자산 합계 - 고정비_월_합계 - 비상금

투자가능자산: Asset_Account.deposit_received (예수금) + 현금성 자산 (CMA 등)
고정비_월_합계: Category.type = 'FIXED' 인 최근 3개월 평균 지출
              (데이터가 3개월 미만일 시 가용 기간 평균 적용)
비상금: 사용자가 직접 설정한 값 (미설정 시 고정비 1개월치 기본 적용)
```

- 계산 결과는 `Portfolio.investable_amount`에 저장하고 포트폴리오 추천에서 시각화
- 산출 결과가 0 이하인 경우 **0으로 반환 (음수 금지)**

### 5.4 CODEF API Rate Limiting (Redis 쿨다운)

```java
// 수동 새로고침 요청 시
String key = "codef:refresh:cooldown:" + userId;
if (redisTemplate.hasKey(key)) {
        throw new TooManyRequestsException("새로고침은 5분에 한 번만 가능합니다.");
}
        redisTemplate.opsForValue().set(key, "1", 5, TimeUnit.MINUTES);
// CODEF API 호출 진행
```

### 5.5 배치 동기화 스케줄러

```java
@Scheduled(cron = "0 0 2 * * *") // 매일 새벽 2시
public void syncAllActiveUsers() {
    List<CodefConnection> connections = codefConnectionRepository.findAllActive();
    for (CodefConnection conn : connections) {
        boolean success = false;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                codefSyncService.sync(conn);
                success = true;
                break; // 성공 시 탈출
            } catch (Exception e) {
                log.warn("CODEF 동기화 실패 userId={} attempt={}", conn.getUserId(), attempt);
            }
        }
        if (!success) {
            conn.deactivate(); // is_active = false
        }
    }
}
```

배치 실행 시간: 새벽 2:00 ~ 4:00 사이 (사용자 트래픽 최소 구간)

### 5.6 CODEF 연동 실패 처리

#### 오류 유형별 처리 전략

**① 계정/인증 오류 → `updateAccount` 자동 호출 후 재시도**
- `CF-04010` (사용자 계정정보 수정 실패): 비밀번호·인증서 변경으로 인한 인증 실패 → `updateAccount` 호출
- `CF-04008` (connectedId 미존재): connectedId 유효성 이상 → `updateAccount` 호출
- `CF-04019` (존재하지 않는 connectedId): connectedId 만료 또는 삭제 → `updateAccount` 호출
- `CF-04038` (해당 계정 미존재): 연동 계정 불일치 → `updateAccount` 호출

**② 일시적 서버/네트워크 오류 → 최대 3회 재시도 (즉시 탈출)**
- `CF-01004` (응답 대기시간 초과): 타임아웃 → 재시도, 조회 날짜 범위 축소 고려
- `CF-01007` (네트워크 불안정): 일시적 접속 오류 → 재시도
- `CF-01002` (정보수집 서버 접속 실패): 서버 접속 불가 → 재시도
- `CF-12003` (대상 기관 서버 오류): 금융기관 서버 장애 → 재시도, 지속 시 `is_active = false`
- `CF-00016` (중복 요청 처리 중): 이전 요청 처리 진행 중 → 잠시 후 재시도
- `CF-01006` (중복 로그인 방지 제한): → 재시도

**③ 요청 한도 초과 → 재시도 없이 대기 처리**
- `CF-00012` (일 100건 초과): 익일 배치 재시도, 해당 회차 스킵
- `CF-00022` (상품별 일일 요청 초과): 익일 배치 재시도
- `CF-00023` (IP 차단 일일 한도 초과): 익일 배치 재시도

**④ 설정/파라미터 오류 → 재시도 없이 즉시 `is_active = false`**
- `CF-04000` (계정정보 등록 실패): 연동 등록 자체 실패
- `CF-04011` (계정정보 삭제 실패): deleteAccount 실패
- `CF-04015` (기관 계정 미존재): 기관 코드 또는 토큰 불일치

#### 공통 처리 규칙

- 배치 실행 중 ②번 오류: 최대 3회 반복 재시도, 성공 시 즉시 탈출
- 3회 모두 실패 시: `Codef_Connection.is_active = false` 처리, 사용자 알림 발송 예정
- ①번 오류: `updateAccount` 1회 호출 후 재시도, 재시도도 실패 시 `is_active = false`
- 실패 로그는 `log.warn`으로 기록 (오류코드 + userId 포함, DB 컬럼 별도 관리 없음)

```java
// 오류코드 기반 분기 처리 예시
private void handleCodefError(String errorCode, CodefConnection conn) {
    if (isAuthError(errorCode)) {           // CF-04008, CF-04010, CF-04019, CF-04038
        codefClient.updateAccount(conn);    // updateAccount 자동 호출
    } else if (isTransientError(errorCode)) { // CF-01004, CF-01007, CF-12003 등
        throw new CodefRetryableException(errorCode); // 재시도 루프로 위임
    } else if (isRateLimitError(errorCode)) { // CF-00012, CF-00022, CF-00023
        log.warn("CODEF 일일 한도 초과 userId={} code={}", conn.getUserId(), errorCode);
        // 재시도 없이 스킵, 익일 배치에서 재처리
    } else {
        conn.deactivate(); // is_active = false
        log.warn("CODEF 복구 불가 오류 userId={} code={}", conn.getUserId(), errorCode);
    }
}
```

> **참고:** RabbitMQ는 이번 프로젝트 범위에서 제외. AI 분류 비동기 처리는 Spring `@Async`로 대체.

---

## 6. API 설계 원칙

### 6.1 공통 응답 포맷

```json
{
  "success": true,
  "data": { ... },
  "message": "요청이 성공적으로 처리되었습니다.",
  "timestamp": "2025-05-03T10:00:00"
}
```

오류 응답:
```json
{
  "success": false,
  "data": null,
  "message": "에러 메시지",
  "errorCode": "EXPENSE_NOT_FOUND",
  "timestamp": "2025-05-03T10:00:00"
}
```

### 6.2 주요 엔드포인트 패턴

> 개발계획서 + DB 명세서 기준. Admin 권한·뱃지 기능은 개발 범위 제외.
> 모든 인증 필요 API는 `header: Authorization: Bearer {accessToken}` 필수.

#### 🔐 인증 (Auth)
```
POST   /api/auth/signup          회원가입        body: email, password, name
POST   /api/auth/login           로그인          body: email, password  → 응답: accessToken, refreshToken
POST   /api/auth/refresh         토큰 재발급      header: RefreshToken (Redis RTR 방식)
POST   /api/auth/logout          로그아웃        header: Authorization  → Redis Refresh Token 즉시 삭제
DELETE /api/users/{id}           회원 탈퇴       path: id, header: Authorization
```

#### 👤 사용자 (User)
```
GET    /api/users/myprofile      마이페이지 조회  header: Authorization
                                  → 연동 카드·증권 목록, 포트폴리오 이력 포함
POST   /api/users/tendency       투자성향 설문 저장  body: riskType, investGoal, investPeriod 등
                                  → Portfolio 추천 전 반드시 선행 호출
```

#### 💳 CODEF 연동
```
# 유저 직접 호출
POST   /api/codef/connect        Connected ID 등록   body: connectedId, organizationCode, accountType(CARD|STOCK)
DELETE /api/codef/connect/{id}   연동 해지           path: id → CODEF deleteAccount 호출 후 is_active=false
POST   /api/codef/sync           수동 새로고침        header: Authorization
                                  → Redis 쿨다운 5분 체크 후 CODEF API 호출
                                  → 429 Too Many Requests 반환 시 "5분 후 재시도" 메시지

# 서버 내부 전용 (프론트 직접 호출 불가 — @Scheduled 배치 또는 /sync 트리거에서만 호출)
POST   /api/codef/card           카드 청구내역 수집·Expense 저장
POST   /api/codef/stock          증권 종합자산 수집·Asset_Account/Asset_Item 저장
```

#### 💰 지출 (Expense)
```
GET    /api/expenses             지출 목록 조회   query: startDate, endDate, category(categoryId), page, size
                                  → is_excluded=false 인 건만 반환 (제외 처리된 건 기본 숨김)
GET    /api/expenses/details/{id} 지출 상세 조회  path: id
PUT    /api/expenses/category/{id} 카테고리 수동 수정  path: id, body: categoryId
                                  ⚠️ 반드시 is_user_modified=true, classified_by='USER' 로 업데이트
DELETE /api/expenses/delete/{id} 지출 제외 처리   path: id
                                  ⚠️ DB 삭제 금지 — is_excluded=true 소프트 처리만 허용
                                  (대납·법인카드 등 개인 지출 아닌 건 제외용)
GET    /api/analysis             지출 분석 리포트  header: Authorization
                                  → GPT-4 소비패턴 분석, 과소비 카테고리 경고, 절약 제안
```

#### 📈 자산 (Asset)
```
GET    /api/stocks               증권 자산 조회   header: Authorization
                                  → Asset_Account + Asset_Item 조인 응답
POST   /api/stocks               증권 자산 저장   body: organization, accountNo, depositReceived, itemList[]
                                  → CODEF 연동 후 내부 저장용 (account_no AES-256 암호화 저장)
GET    /api/assets/summary       총 자산 요약     header: Authorization
                                  → 증권 자산(Asset_Account) + 수동 입력 자산 합산
                                  → investable_amount 자동 산출 포함
POST   /api/assets/manual        수동 자산 입력   body: assetType(예금|적금|부동산|현금|기타), amount, memo
                                  ⚠️ 저장 테이블 미확정 — 구현 전 반드시 DB 설계 확정할 것
```

#### 🤖 AI 분석 & 포트폴리오 (Portfolio)
```
POST   /api/portfolio/recommend  포트폴리오 추천 생성  body: riskType
                                  → 투자성향(/api/users/tendency) 선행 저장 필요
                                  → GPT-4 호출 → Portfolio 테이블 저장
                                     (risk_type, recommended_assets JSON, investable_amount)
GET    /api/portfolio            포트폴리오 조회       header: Authorization → 최신 추천 결과
GET    /api/portfolio/history    추천 이력 조회        header: Authorization → 마이페이지용
```

#### 💬 커뮤니티 (Community)
```
GET    /api/community/lists               게시글 목록      query: page, size
GET    /api/community/details/{id}        게시글 상세      path: id  → 조회 시 views +1
POST   /api/community/create              게시글 작성      body: title, content, category
PUT    /api/community/edit/{id}           게시글 수정      path: id, body: title, content
DELETE /api/community/delete/{id}         게시글 삭제      path: id  → 본인 게시글만 가능
POST   /api/community/like/{id}           좋아요 토글      path: id  → 중복 시 409, 취소 시 재호출
POST   /api/community/portfolio           포트폴리오 공유  body: portfolioId (선택적 연동)
GET    /api/community/{id}/comments       댓글 목록 조회   path: id  → is_deleted=true는 "삭제된 댓글" 텍스트로 대체
POST   /api/community/{id}/comments       댓글 작성        body: content, isAnonymous(boolean)
DELETE /api/community/comments/{id}       댓글 삭제        path: id
                                           ⚠️ DB 삭제 금지 — is_deleted=true 소프트 처리만 허용
```

### 6.3 페이지네이션

Expense 목록 등 리스트 API는 `Pageable` 기반 Spring Data Page 사용:
```java
Pageable pageable = PageRequest.of(page, size, Sort.by("expenseDate").descending());
```

---

## 7. 예외 처리

모든 예외는 `@RestControllerAdvice`의 `GlobalExceptionHandler`에서 일원화 처리한다.

```java
@ExceptionHandler(DataIntegrityViolationException.class)
// → 중복 거래/좋아요 시 409 Conflict 반환

@ExceptionHandler(EntityNotFoundException.class)
// → 존재하지 않는 리소스 404 반환

@ExceptionHandler(AccessDeniedException.class)
// → 타인 리소스 접근 403 반환 (본인 데이터만 수정 가능)

@ExceptionHandler(TooManyRequestsException.class)
// → Rate Limit 초과 429 반환
```

비즈니스 예외는 `FlowFinException` 기반 커스텀 예외 계층으로 관리한다.

---

## 8. 성능 목표 및 Redis 활용

| 목표 | 수치 |
|------|------|
| 목표 TPS | 30~50 (일반), 50+ (Redis 캐싱 적용 조회) |
| 동시접속자 | 100명 안정 수용 |
| 인스턴스 | AWS t3.medium |

### Redis 사용 목적별 Key 패턴

```
# Refresh Token (주 검증 기준, TTL = 1일)
refresh:token:{userId}  →  TTL: 1일 (86400초)

# 수동 새로고침 쿨다운
codef:refresh:cooldown:{userId}  →  TTL: 5분

# 지출 통계 캐싱 (월별)
expense:stats:{userId}:{yyyyMM}  →  TTL: 1시간

# AI 분류 결과 캐싱 (가맹점명 기준)
category:merchant:{merchantNameHash}  →  TTL: 7일
```

---

## 9. 프로젝트 컨벤션

### 9.1 브랜치 전략

```
main          → 운영 배포 (직접 push 금지, PR only)
develop       → 통합 개발 브랜치
feature/*     → 기능 개발 (예: feature/expense-classification)
hotfix/*      → 긴급 수정
```

### 9.2 PR 제목 규칙

```
[add]       새 기능 추가
[fix]       버그 수정
[refactor]  리팩토링 (기능 변경 없음)
[test]      테스트 코드
[docs]      문서 업데이트
[chore]     빌드/설정 변경

예시: [add] CODEF 카드 청구 내역 연동 API
```

### 9.3 코드 컨벤션
```
- 클래스명: PascalCase
- 메서드/변수명: camelCase
- 상수: UPPER_SNAKE_CASE
- DB 컬럼 → Java 필드: snake_case → camelCase 자동 매핑 (spring.jpa.hibernate.naming.physical-strategy 설정)
- 패키지 구조: 도메인형 → com.project.flowfinserver.{domain} (예: .expense, .asset, .community)
- 클래스명: 레이어 suffix 명시 (UserController, UserService, UserRepository)
- Controller → Service → Repository 엄격히 분리
- DTO ↔ Entity 변환 로직(Mapping) 반드시 포함
- @Transactional 서비스 레이어에 명시, 읽기 전용은 readOnly = true

- DTO: 요청/응답 분리 (UserCreateRequest, UserResponse)
- 예외: 커스텀 예외 클래스 사용, exception/GlobalExceptionHandler에서 일괄 처리
```

### 9.4 테스트 원칙

```
단위 테스트  : JUnit5 + Mockito — 지출 분류 엔진, investable_amount 산출, JWT 핸들러 필수 작성
통합 테스트  : @SpringBootTest — 주요 REST API 엔드포인트 정상 응답 확인
E2E 시나리오 : 로그인→카드연동→지출조회 / 포트폴리오 추천 플로우 최소 3개
```

---

## 10. 8주 마일스톤 요약

| 주차 | 마일스톤 | 핵심 완료 조건 |
|------|---------|--------------|
| 1주 | 기획·설계 | DB ERD 확정, API 명세서 완성, 개발환경 세팅 |
| 2주 | Sprint 1① | 회원가입/로그인(JWT), CODEF Connected ID 등록, 카드 청구 내역→Expense 저장 |
| 3주 | Sprint 1② | 증권 종합자산→Asset_Account/Item 저장, 대시보드 UI 뼈대, 커뮤니티 CRUD 뼈대 |
| 4주 | Sprint 2① | Rule-based 분류 엔진, 월별 지출 통계 API, 지출 목록 UI |
| 5주 | Sprint 2② | 자산 조회 UI, investable_amount 산출, Recharts 시각화 |
| 6주 | Sprint 3① | AI API 연동, GPT-4 지출 분석 메시지, AI 포트폴리오 추천, Portfolio 저장/이력 조회 |
| 7주 | Sprint 3② | 커뮤니티 완성(좋아요/댓글/데이터 공유), 카테고리 수동 수정 UI, UX 고도화 |
| 8주 | QA & 배포 | 통합/E2E 테스트, 금융 데이터 마스킹 검증, 보안 점검, 운영 배포 |

---

## 11. ⚠️ 자주 실수하는 위험 패턴 (ANTI-PATTERNS)

### ❌ 금지 사항

```java
// 1. 응답 DTO에 암호화 원문 그대로 반환 금지
return new ExpenseResponse(expense.getAmount(), accountNumber); // accountNumber 마스킹 필수!

// 2. is_user_modified 확인 없이 카테고리 덮어쓰기 금지
expense.setCategory(aiCategory); // is_user_modified 체크 먼저!

// 3. 이메일 평문으로 DB 조회 금지
userRepository.findByEmail(email); // email_hash로 조회해야 함
userRepository.findByEmailHash(sha256(email)); // 올바른 방식

// 4. CODEF API를 조회 요청마다 직접 호출 금지
codefClient.getExpenses(connectedId); // 5분 쿨다운 + 배치 기반으로 운영

// 5. Comment 하드 삭제 금지
commentRepository.delete(comment); // is_deleted = true 로 소프트 삭제만 허용

// 6. Community like_count 직접 count 쿼리로 계산 금지 (정합성 이슈)
int count = communityLikeRepository.countByCommunityId(id); // 매번 count 쿼리 X
// → Community_Like INSERT/DELETE 시 Community.like_count +1/-1 트랜잭션 내 처리

// 7. Expense 물리 삭제 금지
expenseRepository.delete(expense); // is_excluded = true 소프트 처리만 허용

// 8. 컬럼명 혼용 금지
expense.getTotalInvestAmount(); // ❌ — Portfolio 컬럼명은 investable_amount
portfolio.getInvestableAmount(); // ✅

// 9. Codef_Connection의 account_number를 account_no로 혼용 금지
conn.getAccountNo();     // ❌ — Codef_Connection 엔티티 필드명은 accountNumber
conn.getAccountNumber(); // ✅
// (Asset_Account의 계좌번호 필드는 accountNo — 두 엔티티의 필드명이 다름에 주의)
// 다르긴 하지만 연동하면서 Codef_Connection의 account_number를 Asset_Account의 account_no로 저장하여 쓸 예정
```

### ✅ 올바른 패턴

```java
// 이메일 중복 확인
String emailHash = DigestUtils.sha256Hex(email.toLowerCase());
userRepository.existsByEmailHash(emailHash);

// AI 분류 전 사용자 수정 여부 확인
if (!expense.isUserModified()) {
    classificationService.reclassify(expense);
}

// 계좌번호 마스킹 후 응답 (Codef_Connection)
response.setAccountNumber(MaskingUtil.maskAccountNumber(accountNumber));

// 계좌번호 마스킹 후 응답 (Asset_Account)
response.setAccountNo(MaskingUtil.maskAccountNumber(accountNo));
```

---

## 12. 환경 변수 (application.yml 절대 하드코딩 금지)

```yaml
# 반드시 환경 변수 또는 AWS Parameter Store에서 주입
encrypt:
  aes:
    secret-key: ${AES_SECRET_KEY}

jwt:
  secret: ${JWT_SECRET}
  access-expiration: ${JWT_ACCESS_EXP:1800}    # 30분 (초)
  refresh-expiration: ${JWT_REFRESH_EXP:86400}  # 1일 (초)

codef:
  client-id: ${CODEF_CLIENT_ID}
  client-secret: ${CODEF_CLIENT_SECRET}

openai:
  api-key: ${OPENAI_API_KEY}
```

---

## Out of Scope (구현 제외)
- 송금·결제·인출 등 실제 금융 거래 기능
- Android/iOS 모바일 앱
- 다국어 지원
- CODEF 미지원 금융기관 연동
- 주식 매매 (일임형 투자) 서비스

---

## 불확실할 때 반드시 질문 (추측 금지)
```
- CODEF API 실제 응답 필드 구조 미확인 시
- 포트폴리오 추천 가중치 등 비즈니스 정책 미정 시
- AWS 등 인프라 종속 설정 필요 시
- 수동 자산 입력 저장 테이블 미확정 시 (DB 명세서 v3.3 기준 미포함)
```

---

*최종 업데이트: 2026-05-07 | FlowFin 개발팀*