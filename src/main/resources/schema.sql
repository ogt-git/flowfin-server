/*-- ============================================================
-- 참조용 문서 — Spring Boot 자동 실행 안 됨
-- 실제 DDL 및 시드 데이터는 docker/init.sql 에서 관리
-- ============================================================

-- 카테고리 (대/소분류, 아이콘/색상 포함)
CREATE TABLE IF NOT EXISTS category (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                 VARCHAR(50)  NOT NULL,
    icon                 VARCHAR(10),
    color                VARCHAR(20),
    parent_id            BIGINT,
    is_fixed             BOOLEAN      DEFAULT FALSE,
    default_expen.sqse_type ENUM('FIXED','VARIABLE','IRREGULAR') DEFAULT 'VARIABLE',
    sort_order           INT          DEFAULT 0,
    FOREIGN KEY (parent_id) REFERENCES category(id)
);

-- 기본 카테고리 시드 데이터
INSERT IGNORE INTO category (id, name, icon, color, parent_id, is_fixed, default_expense_type, sort_order) VALUES
(1,  '주거비',      '🏠', '#FF8C94', NULL, TRUE,  'FIXED',    1),
(2,  '식비',        '🍽️', '#FF6B6B', NULL, FALSE, 'VARIABLE', 2),
(3,  '교통',        '🚌', '#4ECDC4', NULL, FALSE, 'VARIABLE', 3),
(4,  '통신비',      '📱', '#45B7D1', NULL, TRUE,  'FIXED',    4),
(5,  '의류비',      '👗', '#F7DC6F', NULL, FALSE, 'VARIABLE', 5),
(6,  '의료비',      '💊', '#96CEB4', NULL, FALSE, 'IRREGULAR',6),
(7,  '생활비',      '🛒', '#AED6F1', NULL, FALSE, 'VARIABLE', 7),
(8,  '교육비',      '📚', '#FFEAA7', NULL, TRUE,  'FIXED',    8),
(9,  '문화/여가비', '🎬', '#DDA0DD', NULL, FALSE, 'VARIABLE', 9),
(10, '보험비',      '🛡️', '#FAD7A0', NULL, TRUE,  'FIXED',    10),
(11, '기타지출',    '📦', '#B0B0B0', NULL, FALSE, 'IRREGULAR',99);

-- 가맹점-카테고리 규칙 (Rule-based 분류용)
CREATE TABLE IF NOT EXISTS merchant_category_rule (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    keyword     VARCHAR(200) NOT NULL,
    category_id BIGINT       NOT NULL,
    match_type  ENUM('EXACT','CONTAINS') NOT NULL,
    priority    INT          NOT NULL DEFAULT 0,   -- 높을수록 우선 적용
    INDEX idx_rule_match_priority (match_type, priority DESC),
    FOREIGN KEY (category_id) REFERENCES category(id)
);

-- 기본 Rule 시드 데이터 (category_id: 1=주거비 2=식비 3=교통 4=통신비 5=의류비 6=의료비 7=생활비 8=교육비 9=문화/여가비 10=보험비 11=기타지출)
INSERT IGNORE INTO merchant_category_rule (keyword, category_id, match_type, priority) VALUES
-- 주거비 (1)
('한국전력',       1, 'CONTAINS',  90),
('도시가스',       1, 'CONTAINS',  90),
('관리비',         1, 'CONTAINS',  90),
('수도',           1, 'CONTAINS',  80),
-- 식비 (2)
('스타벅스',       2, 'EXACT',    100),
('맥도날드',       2, 'EXACT',    100),
('배달의민족',     2, 'CONTAINS',  90),
('요기요',         2, 'CONTAINS',  90),
('쿠팡이츠',       2, 'CONTAINS',  90),
('GS25',           2, 'CONTAINS',  80),
('CU',             2, 'CONTAINS',  80),
('세븐일레븐',     2, 'CONTAINS',  80),
('이마트24',       2, 'CONTAINS',  80),
('카페',           2, 'CONTAINS',  70),
('베이커리',       2, 'CONTAINS',  70),
-- 교통 (3)
('지하철',         3, 'CONTAINS',  90),
('버스',           3, 'CONTAINS',  80),
('택시',           3, 'CONTAINS',  90),
('카카오택시',     3, 'CONTAINS',  95),
('티머니',         3, 'CONTAINS',  90),
('KTX',            3, 'CONTAINS',  90),
('코레일',         3, 'CONTAINS',  90),
('주유소',         3, 'CONTAINS',  80),
('GS칼텍스',       3, 'CONTAINS',  85),
('SK에너지',       3, 'CONTAINS',  85),
-- 통신비 (4)
('SKT',            4, 'CONTAINS',  90),
('KT',             4, 'EXACT',    100),
('LG유플러스',     4, 'CONTAINS',  90),
('알뜰폰',         4, 'CONTAINS',  80),
-- 의류비 (5)
('무신사',         5, 'CONTAINS',  90),
('유니클로',       5, 'EXACT',    100),
('자라',           5, 'EXACT',    100),
('에이블리',       5, 'CONTAINS',  90),
('지그재그',       5, 'CONTAINS',  90),
('H&M',            5, 'EXACT',    100),
-- 의료비 (6)
('병원',           6, 'CONTAINS',  90),
('약국',           6, 'CONTAINS',  90),
('의원',           6, 'CONTAINS',  90),
('치과',           6, 'CONTAINS',  90),
('한의원',         6, 'CONTAINS',  90),
('안과',           6, 'CONTAINS',  90),
('피부과',         6, 'CONTAINS',  90),
-- 생활비 (7)
('이마트',         7, 'CONTAINS',  90),
('롯데마트',       7, 'CONTAINS',  90),
('홈플러스',       7, 'CONTAINS',  90),
('쿠팡',           7, 'CONTAINS',  80),
('다이소',         7, 'CONTAINS',  85),
('올리브영',       7, 'CONTAINS',  85),
('코스트코',       7, 'CONTAINS',  90),
-- 교육비 (8)
('학원',           8, 'CONTAINS',  90),
('교습',           8, 'CONTAINS',  90),
('서점',           8, 'CONTAINS',  80),
('YES24',          8, 'CONTAINS',  85),
('교보문고',       8, 'CONTAINS',  90),
('알라딘',         8, 'CONTAINS',  85),
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
-- 보험비 (10)
('보험',          10, 'CONTAINS',  90),
('생명보험',      10, 'CONTAINS',  95),
('손해보험',      10, 'CONTAINS',  95),
('화재보험',      10, 'CONTAINS',  95);

-- CODEF 연동 계정 (connected_id AES-256 암호화)
CREATE TABLE IF NOT EXISTS codef_connected_account (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    connected_id      VARCHAR(255) NOT NULL,           -- AES-256 encrypted
    organization_code VARCHAR(20)  NOT NULL,
    account_type      ENUM('CARD','STOCK') NOT NULL,
    is_active         BOOLEAN      DEFAULT TRUE,
    created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 카드 지출 내역 (Expense)
CREATE TABLE IF NOT EXISTS expense (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    transacted_at DATE         NOT NULL,
    merchant_name VARCHAR(200) NOT NULL,
    amount        BIGINT       NOT NULL,
    card_company  VARCHAR(50),
    category_id   BIGINT,                             -- null = 미분류 (AI 분류 대기)
    classified_by ENUM('RULE','AI','USER'),
    expense_type  ENUM('FIXED','VARIABLE','IRREGULAR') DEFAULT 'VARIABLE',
    raw_data      JSON,
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_expense (user_id, transacted_at, merchant_name, amount),
    FOREIGN KEY (user_id)     REFERENCES users(id),
    FOREIGN KEY (category_id) REFERENCES category(id)
);

-- 증권 계좌 일별 스냅샷
CREATE TABLE IF NOT EXISTS stock_account_snapshot (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id               BIGINT  NOT NULL,
    snapshot_date         DATE    NOT NULL,
    broker_name           VARCHAR(50),
    total_eval_amount     BIGINT,
    total_purchase_amount BIGINT,
    profit_loss           BIGINT,
    deposit_received      BIGINT,
    raw_data              JSON,
    created_at            DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_snapshot (user_id, snapshot_date, broker_name),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
*/