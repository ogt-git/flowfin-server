-- ============================================================
-- FlowFin 더미 데이터
-- 실행 전: SET @uid = <실제 user_id>; 로 변경 후 실행
-- ============================================================
SET @uid = 1;

-- -------------------------------------------------------
-- 1. 카테고리 (없으면 삽입)
-- -------------------------------------------------------
INSERT IGNORE INTO category (id, name, icon, color, parent_id, is_fixed, default_expense_type, sort_order) VALUES
(1,  '주거비',    '🏠', '#4A90D9', NULL, true,  'FIXED',    1),
(2,  '보험비',    '🛡️', '#5BA85A', NULL, true,  'FIXED',    2),
(3,  '통신비',    '📱', '#F5A623', NULL, true,  'FIXED',    3),
(4,  '교육비',    '📚', '#9B59B6', NULL, true,  'FIXED',    4),
(5,  '식비',      '🍽️', '#E74C3C', NULL, false, 'VARIABLE', 5),
(6,  '생활비',    '🛒', '#1ABC9C', NULL, false, 'VARIABLE', 6),
(7,  '교통비',    '🚇', '#3498DB', NULL, false, 'VARIABLE', 7),
(8,  '의류비',    '👗', '#E91E63', NULL, false, 'VARIABLE', 8),
(9,  '문화여가비','🎬', '#FF9800', NULL, false, 'VARIABLE', 9),
(10, '의료비',    '💊', '#00BCD4', NULL, false, 'IRREGULAR',10),
(11, '기타지출',  '📦', '#9E9E9E', NULL, false, 'VARIABLE', 11);

-- -------------------------------------------------------
-- 2. 지출 내역 (2025-04, 2025-05 두 달치)
-- -------------------------------------------------------
INSERT IGNORE INTO expense
    (user_id, expense_date, merchant_name, amount, card_company,
     category_id, classified_by, category_confidence,
     is_user_modified, is_excluded, expense_type, raw_data, created_at)
VALUES
-- 4월 고정비
(@uid, '2025-04-01', '○○아파트 관리비',  250000, '신한카드', 1,  'RULE', 100, false, false, 'FIXED',    NULL, NOW()),
(@uid, '2025-04-03', '삼성생명 월납보험', 120000, '국민카드', 2,  'RULE', 100, false, false, 'FIXED',    NULL, NOW()),
(@uid, '2025-04-05', 'KT 휴대폰요금',     59000, '현대카드', 3,  'RULE', 100, false, false, 'FIXED',    NULL, NOW()),
(@uid, '2025-04-10', '패스트캠퍼스',      99000, '신한카드', 4,  'RULE', 100, false, false, 'FIXED',    NULL, NOW()),

-- 4월 변동비
(@uid, '2025-04-02', '스타벅스 강남점',    6500, '국민카드', 5,  'RULE', 100, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-04-04', '쿠팡',              45000, '현대카드', 6,  'RULE',  90, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-04-06', '서울 지하철',        1800, '삼성카드', 7,  'RULE', 100, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-04-07', '이마트 역삼점',     87000, '국민카드', 6,  'RULE', 100, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-04-08', '무신사',            34000, '현대카드', 8,  'RULE',  95, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-04-12', 'CGV 강남',          15000, '신한카드', 9,  'RULE', 100, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-04-14', '배달의민족',        28000, '카카오페이',5, 'RULE',  90, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-04-16', '올리브영',          32000, '국민카드', 6,  'RULE',  85, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-04-19', 'GS25',               3200, '삼성카드', 5,  'RULE', 100, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-04-22', '카카오택시',         9500, '카카오페이',7, 'RULE',  95, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-04-24', '넷플릭스',          17000, '현대카드', 9,  'RULE', 100, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-04-27', '교보문고',          22000, '신한카드', 9,  'RULE',  90, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-04-28', '약국',               8500, '국민카드', 10, 'RULE',  80, false, false, 'IRREGULAR',NULL, NOW()),

-- 5월 고정비
(@uid, '2025-05-01', '○○아파트 관리비',  250000, '신한카드', 1,  'RULE', 100, false, false, 'FIXED',    NULL, NOW()),
(@uid, '2025-05-03', '삼성생명 월납보험', 120000, '국민카드', 2,  'RULE', 100, false, false, 'FIXED',    NULL, NOW()),
(@uid, '2025-05-05', 'KT 휴대폰요금',     59000, '현대카드', 3,  'RULE', 100, false, false, 'FIXED',    NULL, NOW()),

-- 5월 변동비
(@uid, '2025-05-02', '스타벅스 합정점',    7000, '국민카드', 5,  'RULE', 100, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-05-06', '이마트 역삼점',     62000, '국민카드', 6,  'RULE', 100, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-05-08', '쿠팡',              18500, '현대카드', 6,  'RULE',  90, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-05-09', '지하철 정기권',     55000, '삼성카드', 7,  'RULE', 100, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-05-11', '배달의민족',        21000, '카카오페이',5, 'RULE',  90, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-05-13', 'H&M',              43000, '현대카드', 8,  'RULE',  85, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-05-15', '롯데시네마',        14000, '신한카드', 9,  'RULE', 100, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-05-16', 'GS25',               4100, '삼성카드', 5,  'RULE', 100, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-05-17', '카카오택시',        12300, '카카오페이',7, 'RULE',  95, false, false, 'VARIABLE', NULL, NOW()),
(@uid, '2025-05-18', '병원 진료비',       35000, '국민카드', 10, 'RULE',  80, false, false, 'IRREGULAR',NULL, NOW());

-- -------------------------------------------------------
-- 3. 자산 계좌 (account_no는 AES 암호화 컬럼 → NULL 처리)
-- -------------------------------------------------------
INSERT INTO asset_account (user_id, broker_code, account_no, total_asset, deposit_received, updated_at)
VALUES
(@uid, '0240', NULL, 15800000, 3200000, NOW()),  -- 키움증권
(@uid, '0226', NULL,  8500000, 1500000, NOW());  -- 미래에셋

-- -------------------------------------------------------
-- 4. 자산 종목 (account_id는 위에서 생성된 ID 기준)
-- -------------------------------------------------------
-- 키움 계좌 종목
INSERT INTO asset_item
    (account_id, user_id, product_type, item_name, item_code,
     quantity, purchase_amount, valuation_amt, valuation_pl, earnings_rate, updated_at)
SELECT
    a.id, @uid, '주식', '삼성전자', '005930',
    10, 780000, 840000, 60000, 7.69, NOW()
FROM asset_account a
WHERE a.user_id = @uid AND a.broker_code = '0240'
LIMIT 1;

INSERT INTO asset_item
    (account_id, user_id, product_type, item_name, item_code,
     quantity, purchase_amount, valuation_amt, valuation_pl, earnings_rate, updated_at)
SELECT
    a.id, @uid, '주식', 'SK하이닉스', '000660',
    5, 1050000, 1180000, 130000, 12.38, NOW()
FROM asset_account a
WHERE a.user_id = @uid AND a.broker_code = '0240'
LIMIT 1;

INSERT INTO asset_item
    (account_id, user_id, product_type, item_name, item_code,
     quantity, purchase_amount, valuation_amt, valuation_pl, earnings_rate, updated_at)
SELECT
    a.id, @uid, 'ETF', 'TIGER 미국S&P500', '360750',
    20, 2400000, 2580000, 180000, 7.50, NOW()
FROM asset_account a
WHERE a.user_id = @uid AND a.broker_code = '0240'
LIMIT 1;

-- 미래에셋 계좌 종목
INSERT INTO asset_item
    (account_id, user_id, product_type, item_name, item_code,
     quantity, purchase_amount, valuation_amt, valuation_pl, earnings_rate, updated_at)
SELECT
    a.id, @uid, 'ETF', 'KODEX 200', '069500',
    15, 1200000, 1350000, 150000, 12.50, NOW()
FROM asset_account a
WHERE a.user_id = @uid AND a.broker_code = '0226'
LIMIT 1;

INSERT INTO asset_item
    (account_id, user_id, product_type, item_name, item_code,
     quantity, purchase_amount, valuation_amt, valuation_pl, earnings_rate, updated_at)
SELECT
    a.id, @uid, '주식', 'NAVER', '035420',
    3, 510000, 480000, -30000, -5.88, NOW()
FROM asset_account a
WHERE a.user_id = @uid AND a.broker_code = '0226'
LIMIT 1;
