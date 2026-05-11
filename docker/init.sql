-- ============================================================
-- FlowFin DB 초기화 스크립트
-- Docker 컨테이너 첫 시작 시 자동 실행 (볼륨이 비어있을 때만)
-- 스키마 변경 시 이 파일을 수정하고 볼륨 재생성: docker-compose down -v && docker-compose up -d
-- ============================================================

USE flowfin;

-- 회원
CREATE TABLE IF NOT EXISTS users (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    email        VARCHAR(512)  NOT NULL,
    email_hash   VARCHAR(64)   NOT NULL UNIQUE,
    password     VARCHAR(255)  NOT NULL,
    name         VARCHAR(512)  NOT NULL,
    connected_id VARCHAR(255),
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 카테고리
CREATE TABLE IF NOT EXISTS category (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                 VARCHAR(50)  NOT NULL,
    icon                 VARCHAR(10),
    color                VARCHAR(20),
    parent_id            BIGINT,
    is_fixed             BOOLEAN      DEFAULT FALSE,
    default_expense_type ENUM('FIXED','VARIABLE','IRREGULAR') DEFAULT 'VARIABLE',
    sort_order           INT          DEFAULT 0,
    FOREIGN KEY (parent_id) REFERENCES category(id)
);

-- 가맹점-카테고리 룰 (Rule-based 지출 분류)
CREATE TABLE IF NOT EXISTS merchant_category_rule (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    keyword     VARCHAR(200) NOT NULL,
    category_id BIGINT       NOT NULL,
    match_type  ENUM('EXACT','CONTAINS') NOT NULL,
    priority    INT          NOT NULL DEFAULT 0,
    INDEX idx_rule_match_priority (match_type, priority DESC),
    FOREIGN KEY (category_id) REFERENCES category(id)
);

-- CODEF 연동 계정
CREATE TABLE IF NOT EXISTS codef_connected_account (
    id                BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    connected_id      VARCHAR(255) NOT NULL,
    organization_code VARCHAR(20)  NOT NULL,
    account_type      ENUM('CARD','STOCK') NOT NULL,
    account_number    VARCHAR(50),
    is_active         BOOLEAN      DEFAULT TRUE,
    created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 카드 지출 내역
CREATE TABLE IF NOT EXISTS expense (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    expense_date        DATE         NOT NULL,
    merchant_name       VARCHAR(255) NOT NULL,
    amount              BIGINT       NOT NULL,
    card_company        VARCHAR(50),
    category_id         BIGINT,
    classified_by       ENUM('RULE','AI','USER'),
    category_confidence INT,
    is_user_modified    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_excluded         BOOLEAN      NOT NULL DEFAULT FALSE,
    expense_type        ENUM('FIXED','VARIABLE','IRREGULAR') DEFAULT 'VARIABLE',
    raw_data            JSON,
    created_at          DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_expense (user_id, expense_date, merchant_name, amount),
    FOREIGN KEY (user_id)     REFERENCES users(id),
    FOREIGN KEY (category_id) REFERENCES category(id)
);

-- 증권 계좌
CREATE TABLE IF NOT EXISTS asset_account (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT      NOT NULL,
    broker_code      VARCHAR(20),
    account_no       VARCHAR(50),
    total_asset      BIGINT,
    deposit_received BIGINT,
    updated_at       DATETIME,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 보유 종목
CREATE TABLE IF NOT EXISTS asset_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id      BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    product_type    VARCHAR(50),
    item_name       VARCHAR(100),
    item_code       VARCHAR(20),
    quantity        INT,
    purchase_amount BIGINT,
    valuation_amt   BIGINT,
    valuation_pl    BIGINT,
    earnings_rate   DECIMAL(5,2),
    updated_at      DATETIME,
    UNIQUE KEY uq_asset_item (account_id, item_code),
    FOREIGN KEY (account_id) REFERENCES asset_account(id),
    FOREIGN KEY (user_id)    REFERENCES users(id)
);

-- AI 투자 포트폴리오 추천
CREATE TABLE IF NOT EXISTS portfolio (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT      NOT NULL,
    risk_type          VARCHAR(20),
    recommended_assets JSON,
    investable_amount  BIGINT,
    created_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ============================================================
-- 시드 데이터: 카테고리 (명세서 ID 순서 기준)
-- ============================================================

INSERT IGNORE INTO category (id, name, icon, color, parent_id, is_fixed, default_expense_type, sort_order) VALUES
(1,  '주거비',      '🏠', '#FF8C94', NULL, TRUE,  'FIXED',     1),
(2,  '보험비',      '🛡️', '#FAD7A0', NULL, TRUE,  'FIXED',     2),
(3,  '통신비',      '📱', '#45B7D1', NULL, TRUE,  'FIXED',     3),
(4,  '교육비',      '📚', '#FFEAA7', NULL, TRUE,  'FIXED',     4),
(5,  '식비',        '🍽️', '#FF6B6B', NULL, FALSE, 'VARIABLE',  5),
(6,  '생활비',      '🛒', '#AED6F1', NULL, FALSE, 'VARIABLE',  6),
(7,  '교통비',      '🚌', '#4ECDC4', NULL, FALSE, 'VARIABLE',  7),
(8,  '의류비',      '👗', '#F7DC6F', NULL, FALSE, 'VARIABLE',  8),
(9,  '문화/여가비', '🎬', '#DDA0DD', NULL, FALSE, 'VARIABLE',  9),
(10, '의료비',      '💊', '#96CEB4', NULL, FALSE, 'IRREGULAR', 10),
(11, '기타지출',    '📦', '#B0B0B0', NULL, FALSE, 'IRREGULAR', 99);

-- ============================================================
-- 시드 데이터: 가맹점-카테고리 룰
-- ============================================================

INSERT IGNORE INTO merchant_category_rule (keyword, category_id, match_type, priority) VALUES
-- 주거비 (1)
('한국전력',       1, 'CONTAINS',  90),
('도시가스',       1, 'CONTAINS',  90),
('관리비',         1, 'CONTAINS',  90),
('수도',           1, 'CONTAINS',  80),
-- 보험비 (2)
('보험',           2, 'CONTAINS',  90),
('생명보험',       2, 'CONTAINS',  95),
('손해보험',       2, 'CONTAINS',  95),
('화재보험',       2, 'CONTAINS',  95),
-- 통신비 (3)
('SKT',            3, 'CONTAINS',  90),
('KT',             3, 'EXACT',    100),
('LG유플러스',     3, 'CONTAINS',  90),
('알뜰폰',         3, 'CONTAINS',  80),
-- 교육비 (4)
('학원',           4, 'CONTAINS',  90),
('교습',           4, 'CONTAINS',  90),
('서점',           4, 'CONTAINS',  80),
('YES24',          4, 'CONTAINS',  85),
('교보문고',       4, 'CONTAINS',  90),
('알라딘',         4, 'CONTAINS',  85),
-- 식비 (5)
('스타벅스',       5, 'EXACT',    100),
('맥도날드',       5, 'EXACT',    100),
('배달의민족',     5, 'CONTAINS',  90),
('요기요',         5, 'CONTAINS',  90),
('쿠팡이츠',       5, 'CONTAINS',  90),
('GS25',           5, 'CONTAINS',  80),
('CU',             5, 'CONTAINS',  80),
('세븐일레븐',     5, 'CONTAINS',  80),
('이마트24',       5, 'CONTAINS',  80),
('카페',           5, 'CONTAINS',  70),
('베이커리',       5, 'CONTAINS',  70),
-- 생활비 (6)
('이마트',         6, 'CONTAINS',  90),
('롯데마트',       6, 'CONTAINS',  90),
('홈플러스',       6, 'CONTAINS',  90),
('쿠팡',           6, 'CONTAINS',  80),
('다이소',         6, 'CONTAINS',  85),
('올리브영',       6, 'CONTAINS',  85),
('코스트코',       6, 'CONTAINS',  90),
-- 교통비 (7)
('지하철',         7, 'CONTAINS',  90),
('버스',           7, 'CONTAINS',  80),
('택시',           7, 'CONTAINS',  90),
('카카오택시',     7, 'CONTAINS',  95),
('티머니',         7, 'CONTAINS',  90),
('KTX',            7, 'CONTAINS',  90),
('코레일',         7, 'CONTAINS',  90),
('주유소',         7, 'CONTAINS',  80),
('GS칼텍스',       7, 'CONTAINS',  85),
('SK에너지',       7, 'CONTAINS',  85),
-- 의류비 (8)
('무신사',         8, 'CONTAINS',  90),
('유니클로',       8, 'EXACT',    100),
('자라',           8, 'EXACT',    100),
('에이블리',       8, 'CONTAINS',  90),
('지그재그',       8, 'CONTAINS',  90),
('H&M',            8, 'EXACT',    100),
-- 문화/여가비 (9)
('넷플릭스',       9, 'EXACT',    100),
('유튜브',         9, 'CONTAINS',  90),
('웨이브',         9, 'EXACT',    100),
('왓챠',           9, 'EXACT',    100),
('티빙',           9, 'EXACT',    100),
('CGV',            9, 'EXACT',    100),
('롯데시네마',     9, 'EXACT',    100),
('메가박스',       9, 'EXACT',    100),
('멜론',           9, 'EXACT',    100),
('스포티파이',     9, 'EXACT',    100),
('게임',           9, 'CONTAINS',  70),
-- 의료비 (10)
('병원',          10, 'CONTAINS',  90),
('약국',          10, 'CONTAINS',  90),
('의원',          10, 'CONTAINS',  90),
('치과',          10, 'CONTAINS',  90),
('한의원',        10, 'CONTAINS',  90),
('안과',          10, 'CONTAINS',  90),
('피부과',        10, 'CONTAINS',  90);
