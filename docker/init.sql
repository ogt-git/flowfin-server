-- ============================================================
-- FlowFin DB 초기화 스크립트 (JPA 엔티티 기준 v3.3)
-- Docker 컨테이너 첫 시작 시 자동 실행 (볼륨이 비어있을 때만)
-- 스키마 변경 시: docker-compose down -v && docker-compose up -d
-- ============================================================

ALTER DATABASE flowfin CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'flowfin-admin'@'%' IDENTIFIED BY 'flowfin1234';
GRANT ALL PRIVILEGES ON flowfin.* TO 'flowfin-admin'@'%';
FLUSH PRIVILEGES;

USE flowfin;

-- ============================================================
-- 테이블 생성 (JPA 엔티티 정의와 정확히 일치)
-- ============================================================

-- 회원
CREATE TABLE IF NOT EXISTS users (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
    email            VARCHAR(512) NOT NULL,
    email_hash       VARCHAR(64)  NOT NULL UNIQUE,
    password         VARCHAR(255) NOT NULL,
    name             VARCHAR(50)  NOT NULL,
    connected_id VARCHAR(255),
    risk_type    VARCHAR(50),
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP
    );

-- 카테고리
CREATE TABLE IF NOT EXISTS category (
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(50) NOT NULL,
    type       VARCHAR(10) NOT NULL COMMENT 'FIXED | VARIABLE | ETC',
    created_at DATETIME    DEFAULT CURRENT_TIMESTAMP
);

-- 가맹점-카테고리 룰 (Rule-based 지출 분류)
CREATE TABLE IF NOT EXISTS merchant_category_rule (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    keyword     VARCHAR(200) NOT NULL,
    category_id BIGINT       NOT NULL,
    match_type  VARCHAR(10)  NOT NULL COMMENT 'EXACT | CONTAINS',
    priority    INT          NOT NULL DEFAULT 0,
    INDEX idx_rule_match_type_priority (match_type, priority DESC),
    FOREIGN KEY (category_id) REFERENCES category (id)
);

-- CODEF 연동 계정 (connected_id AES-256 암호화 저장)
-- ※ 테이블명: codef_connection (@Table(name="codef_connection") 기준)
CREATE TABLE IF NOT EXISTS codef_connection (
    id                BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    connected_id      VARCHAR(255) NOT NULL,
    organization_code VARCHAR(100) NOT NULL,
    account_type      VARCHAR(10)  NOT NULL COMMENT 'CARD | STOCK',
    account_number    VARCHAR(50),
    is_active         TINYINT(1)   NOT NULL DEFAULT 1,
    created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 카드 지출 내역
CREATE TABLE IF NOT EXISTS expense (
    id                 BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT       NOT NULL,
    card_company       VARCHAR(50),
    amount             BIGINT       NOT NULL,
    merchant_name      VARCHAR(255) NOT NULL,
    expense_date       DATETIME     NOT NULL,
    category_id        BIGINT,
    classified_by      VARCHAR(10)  COMMENT 'RULE | AI | USER',
    category_confidence INT,
    used_card          VARCHAR(50)  NOT NULL DEFAULT '',
    is_user_modified   TINYINT(1)   NOT NULL DEFAULT 0,
    is_excluded        TINYINT(1)   NOT NULL DEFAULT 0,
    created_at         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    expense_type        ENUM('FIXED','VARIABLE','IRREGULAR') DEFAULT 'VARIABLE',
    UNIQUE KEY uq_expense (user_id, expense_date, merchant_name, amount, used_card),
    FOREIGN KEY (user_id)     REFERENCES users (id),
    FOREIGN KEY (category_id) REFERENCES category (id)
);

-- 증권 계좌 일별 스냅샷
CREATE TABLE IF NOT EXISTS stock_account_snapshot (
    id                    BIGINT  AUTO_INCREMENT PRIMARY KEY,
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
    FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 증권 계좌 (account_no AES-256 암호화 저장)
CREATE TABLE IF NOT EXISTS asset_account (
    id               INT         AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT      NOT NULL,
    broker_code      VARCHAR(20),
    account_no       VARCHAR(50) ,
    total_asset      BIGINT,
    deposit_received BIGINT,
    updated_at       DATETIME,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 증권 보유 종목
CREATE TABLE IF NOT EXISTS asset_item (
    id              INT          AUTO_INCREMENT PRIMARY KEY,
    account_id      INT          NOT NULL,
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
    FOREIGN KEY (account_id) REFERENCES asset_account (id),
    FOREIGN KEY (user_id)    REFERENCES users (id)
);

-- 포트폴리오
CREATE TABLE IF NOT EXISTS portfolio (
    id                  INT         AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT      NOT NULL,
    recommended_assets  JSON,
    investable_amount   BIGINT,
    created_at          DATETIME    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 커뮤니티 게시글
CREATE TABLE IF NOT EXISTS community (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(255) NOT NULL,
    content    TEXT         NOT NULL,
    category   VARCHAR(20),
    views      INT          NOT NULL DEFAULT 0,
    like_count INT          NOT NULL DEFAULT 0,
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 커뮤니티 좋아요
CREATE TABLE IF NOT EXISTS community_like (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    community_id BIGINT NOT NULL,
    UNIQUE KEY uq_like (user_id, community_id),
    FOREIGN KEY (user_id)      REFERENCES users (id),
    FOREIGN KEY (community_id) REFERENCES community (id)
);

-- 댓글
CREATE TABLE IF NOT EXISTS comment (
    id           BIGINT     AUTO_INCREMENT PRIMARY KEY,
    community_id BIGINT     NOT NULL,
    user_id      BIGINT     NOT NULL,
    content      TEXT       NOT NULL,
    is_anonymous TINYINT(1) NOT NULL DEFAULT 0,
    is_deleted   TINYINT(1) NOT NULL DEFAULT 0,
    created_at   DATETIME   DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (community_id) REFERENCES community (id),
    FOREIGN KEY (user_id)      REFERENCES users (id)
);

-- ============================================================
-- 시드 데이터
-- ============================================================

-- 카테고리 11개 (DB 명세서 v3.3 기준)
INSERT IGNORE INTO category (id, name, type) VALUES
(1,  '주거비',      'FIXED'),
(2,  '보험비',      'FIXED'),
(3,  '통신비',      'FIXED'),
(4,  '교육비',      'FIXED'),
(5,  '식비',        'VARIABLE'),
(6,  '생활비',      'VARIABLE'),
(7,  '교통비',      'VARIABLE'),
(8,  '의류비',      'VARIABLE'),
(9,  '문화/여가비', 'VARIABLE'),
(10, '의료비',      'ETC'),
(11, '기타지출',    'ETC');

-- 가맹점 분류 룰 (우선순위: EXACT=100 고정, CONTAINS=70~95)
INSERT IGNORE INTO merchant_category_rule (keyword, category_id, match_type, priority) VALUES

-- ============================================================
-- 주거비 (1) — 아파트 관리/공과금 앱 및 고지서 키워드
-- ============================================================
('한국전력',        1, 'EXACT',   100),
('한전',            1, 'CONTAINS', 90),
('도시가스',        1, 'CONTAINS', 90),
('관리비',          1, 'CONTAINS', 90),
('아파트관리',      1, 'CONTAINS', 90),
('아파트아이',      1, 'CONTAINS', 90),
('카카오페이아파트',1, 'CONTAINS', 90),
('수도요금',        1, 'CONTAINS', 85),
('전기요금',        1, 'CONTAINS', 85),
('가스요금',        1, 'CONTAINS', 85),
('월세',            1, 'CONTAINS', 80),
('난방비',          1, 'CONTAINS', 80),
('인터넷요금',      1, 'CONTAINS', 75),

-- ============================================================
-- 보험비 (2) — 생명·손해·실손 보험사 브랜드
-- ============================================================
('삼성생명',        2, 'EXACT',   100),
('한화생명',        2, 'EXACT',   100),
('교보생명',        2, 'EXACT',   100),
('DB손해보험',      2, 'EXACT',   100),
('메리츠화재',      2, 'EXACT',   100),
('현대해상',        2, 'EXACT',   100),
('KB손해보험',      2, 'EXACT',   100),
('흥국화재',        2, 'EXACT',   100),
('동양생명',        2, 'EXACT',   100),
('ABL생명',         2, 'EXACT',   100),
('라이나생명',      2, 'EXACT',   100),
('신한생명',        2, 'EXACT',   100),
('롯데손해보험',    2, 'EXACT',   100),
('하나생명',        2, 'EXACT',   100),
('생명보험',        2, 'CONTAINS', 95),
('손해보험',        2, 'CONTAINS', 95),
('화재보험',        2, 'CONTAINS', 90),
('보험료',          2, 'CONTAINS', 90),
('보험',            2, 'CONTAINS', 80),

-- ============================================================
-- 통신비 (3) — 이동통신·케이블·알뜰폰
-- ============================================================
('SKT',             3, 'EXACT',   100),
('KT',              3, 'EXACT',   100),
('LG유플러스',      3, 'EXACT',   100),
('LGU+',            3, 'EXACT',   100),
('SK브로드밴드',    3, 'EXACT',   100),
('SK텔레콤',        3, 'EXACT', 100),
('KT인터넷',        3, 'CONTAINS', 90),
('U+인터넷',        3, 'CONTAINS', 90),
('알뜰폰',          3, 'CONTAINS', 85),
('스카이라이프',    3, 'CONTAINS', 85),
('헬로TV',          3, 'CONTAINS', 85),
('딜라이브',        3, 'CONTAINS', 80),
('케이블TV',        3, 'CONTAINS', 80),
('KT',              3, 'CONTAINS',   80),
('인터넷전화',      3, 'CONTAINS', 75),
('통신요금',        3, 'CONTAINS', 80),
('데이터요금',      3, 'CONTAINS', 80),


-- ============================================================
-- 교육비 (4) — 온오프라인 강의, 학원, 서점
-- ============================================================
('클래스101',       4, 'EXACT',   100),
('에듀윌',          4, 'EXACT',   100),
('메가스터디',      4, 'CONTAINS', 95),
('이투스',          4, 'EXACT',   100),
('공단기',          4, 'EXACT',   100),
('해커스',          4, 'CONTAINS', 90),
('대성학원',        4, 'EXACT',   100),
('종로학원',        4, 'EXACT',   100),
('시대인재',        4, 'EXACT',   100),
('인터넷수능방송', 4, 'CONTAINS', 90),
('반디앤루니스',    4, 'EXACT',   100),
('밀리의서재',      4, 'EXACT',   100),

('시원스쿨',        4, 'EXACT',   100),
('스픽',            4, 'EXACT',   100),
('영단기',          4, 'EXACT',   100),
('리디',            4, 'CONTAINS',   90),
('학원',            4, 'CONTAINS', 90),
('교습소',          4, 'CONTAINS', 85),
('과외',            4, 'CONTAINS', 80),
('서점',            4, 'CONTAINS', 80),
('교육',            4, 'CONTAINS', 75),

-- ============================================================
-- 식비 (5) — 배달앱, 카페, 패스트푸드, 편의점, 외식 프랜차이즈
-- ============================================================
-- 배달 앱
('배달의민족',      5, 'EXACT',   100),
('배민',            5, 'CONTAINS', 95),
('요기요',          5, 'EXACT',   100),
('우아한형제들', 5, 'CONTAINS', 95),  -- 배달의민족 법인명
('위대한상상', 5, 'CONTAINS', 95),    -- 요기요 법인명
-- 카페 (EXACT 우선, 고유 브랜드명)
('스타벅스',        5, 'EXACT',   100),
('SCK컴퍼니', 5, 'CONTAINS', 95),     -- 스타벅스 변경 법인명
('이디야',          5, 'EXACT',   100),
('메가커피',        5, 'EXACT',   100),
('빽다방',          5, 'EXACT',   100),
('투썸플레이스',    5, 'EXACT',   100),
('커피빈',          5, 'EXACT',   100),
('할리스',          5, 'EXACT',   100),
('폴바셋',          5, 'EXACT',   100),
('탐앤탐스',        5, 'EXACT',   100),
('공차',            5, 'EXACT',   100),
('더벤티',          5, 'EXACT',   100),
('컴포즈커피',      5, 'EXACT',   100),
('카페베네',        5, 'EXACT',   100),
('파스쿠찌',        5, 'EXACT',   100),
('엔제리너스',      5, 'EXACT',   100),
('드롭탑',          5, 'EXACT',   100),
('쥬씨',            5, 'EXACT',   100),

-- 패스트푸드
('맥도날드',        5, 'EXACT',   100),
('롯데리아',        5, 'EXACT',   100),
('버거킹',          5, 'EXACT',   100),
('KFC',             5, 'EXACT',   100),
('노브랜드버거',    5, 'EXACT',   100),
('맘스터치',        5, 'EXACT',   100),
('서브웨이',        5, 'EXACT',   100),
('도미노피자',      5, 'EXACT',   100),
('피자헛',          5, 'EXACT',   100),
('파파존스',        5, 'EXACT',   100),
('피자알볼로',      5, 'EXACT',   100),
('피자',      5, 'CONTAINS',   95),
('치킨',      5, 'CONTAINS',   95),
('BBQ',             5, 'CONTAINS',   95),
('굽네치킨',        5, 'EXACT',   100),
('교촌치킨',        5, 'EXACT',   100),
('BBQ치킨',         5, 'EXACT',   100),
('BHC',             5, 'EXACT',   100),
('처갓집양념치킨',  5, 'EXACT',   100),
('네네치킨',        5, 'EXACT',   100),
('60계치킨',        5, 'EXACT',   100),
('깐부치킨',        5, 'EXACT',   100),
-- 편의점
('GS25',            5, 'CONTAINS',   95),
('CU',              5, 'CONTAINS',   95),
('세븐일레븐',      5, 'CONTAINS',   95),
('이마트24',        5, 'CONTAINS',   95),
('미니스톱',        5, 'CONTAINS',   95),
('편의점',        5, 'CONTAINS',   90),
-- 외식 프랜차이즈
('본죽',            5, 'EXACT',   100),
('한솥',            5, 'EXACT',   100),
('김밥천국',        5, 'CONTAINS', 90),
('반올림피자',      5, 'EXACT',   100),
('청년다방',        5, 'EXACT',   100),
('이삭토스트',      5, 'EXACT',   100),
('동대문엽기떡볶이',5, 'EXACT',   100),
('국밥',            5, 'CONTAINS', 75),
('냉면',            5, 'CONTAINS', 75),
('설렁탕',          5, 'CONTAINS', 75),
('삼계탕',          5, 'CONTAINS', 75),
('초밥',            5, 'CONTAINS', 75),
('스시',            5, 'CONTAINS', 75),
('분식',            5, 'CONTAINS', 75),
-- 업종 키워드 (낮은 우선순위)
('카페',            5, 'CONTAINS', 70),
('커피',        5, 'CONTAINS',   70),
('베이커리',        5, 'CONTAINS', 70),
('빵집',            5, 'CONTAINS', 70),
('식당',            5, 'CONTAINS', 70),
('음식점',          5, 'CONTAINS', 70),
('포차',            5, 'CONTAINS', 70),

('스타벅스코리아',  5, 'EXACT',   100),  -- 법인명으로 찍히는 경우
('파리바게뜨',      5, 'EXACT',   100),  -- 베이커리 대표 브랜드
('뚜레쥬르',        5, 'EXACT',   100),
('배스킨라빈스',    5, 'EXACT',   100),
('던킨',            5, 'EXACT',   100),
('쉐이크쉑',        5, 'EXACT',   100),
('행복추풍령',      5, 'EXACT',   100),  -- 고속도로 휴게소 주요 운영사
-- ============================================================
-- 생활비 (6) — 대형마트, 이커머스, 잡화, 신선식품
-- ============================================================
-- 대형마트 (이마트는 이마트24보다 낮은 우선순위로 CONTAINS)
('롯데마트',        6, 'EXACT',   100),
('홈플러스',        6, 'EXACT',   100),
('코스트코',        6, 'EXACT',   100),
('이마트',          6, 'CONTAINS', 90),
('트레이더스',      6, 'EXACT',   100),
('노브랜드',        6, 'EXACT',   100),
-- 드럭스토어·잡화
('다이소',          6, 'EXACT',   100),
('올리브영',        6, 'EXACT',   100),
('랄라블라',        6, 'EXACT',   100),
('롭스',            6, 'EXACT',   100),
('무인양품',          6, 'CONTAINS',   90),
('다이소',          6, 'CONTAINS',   90),
('올리브영',        6, 'CONTAINS',   90),

-- 신선식품·이커머스 (생활용품 포함)
('마켓컬리',        6, 'EXACT',   100),
('오아시스마켓',    6, 'EXACT',   100),
('SSG닷컴',         6, 'EXACT',   100),
('오늘의집',        6, 'EXACT',   100),
('이케아',          6, 'EXACT',   100),
('현대리바트',      6, 'EXACT',   100),



-- ============================================================
-- 교통비 (7) — 모빌리티, 주유소, 고속버스, 철도, 항공
-- ============================================================
-- 모빌리티
('카카오택시',      7, 'EXACT',   100),
('카카오T',         7, 'CONTAINS', 95),
('티머니',          7, 'EXACT',   100),
('킥고잉',          7, 'EXACT',   100),
('라임',            7, 'EXACT',   100),
('지쿠',            7, 'EXACT',   100),
('씽씽',            7, 'EXACT',   100),
('따릉이',          7, 'EXACT',   100),
('카카오바이크',    7, 'EXACT',   100),
('일레클',          7, 'EXACT',   100),
('우버',            7, 'EXACT',   100),
('타다',            7, 'EXACT',   100),
('아이엠택시',      7, 'EXACT',   100),
('UT',              7, 'EXACT',   100),  -- 우버택시 앱
('국토교통부',      7, 'CONTAINS', 80),  -- 하이패스 충전
('한국도로공사',    7, 'CONTAINS', 80),  -- 하이패스·통행료
-- 주유소
('SK에너지',        7, 'EXACT',   100),
('GS칼텍스',        7, 'EXACT',   100),
('현대오일뱅크',    7, 'EXACT',   100),
('S-OIL',           7, 'EXACT',   100),
('에쓰오일',        7, 'EXACT',   100),
('알뜰주유소',      7, 'CONTAINS', 85),
-- 고속버스·철도
('코레일',          7, 'EXACT',   100),
('SRT',             7, 'EXACT',   100),
('KTX',             7, 'CONTAINS', 95),
('패스카드',          7, 'CONTAINS', 90),
('기후동행',         7, 'CONTAINS', 90),
('코버스',          7, 'EXACT',   100),
('이티켓',          7, 'EXACT',   100),
('고속버스',        7, 'CONTAINS', 90),
('동양고속',        7, 'EXACT',   100),
('금호고속',        7, 'EXACT',   100),
-- 항공
('대한항공',        7, 'EXACT',   100),
('아시아나',        7, 'EXACT',   100),
('제주항공',        7, 'EXACT',   100),
('티웨이항공',      7, 'EXACT',   100),
('에어부산',        7, 'EXACT',   100),
('에어서울',        7, 'EXACT',   100),
('진에어',          7, 'EXACT',   100),
-- 업종 키워드
('지하철',          7, 'CONTAINS', 90),
('티머니',          7, 'CONTAINS', 90),
('버스',            7, 'CONTAINS', 80),
('택시',            7, 'CONTAINS', 85),
('주유소',          7, 'CONTAINS', 80),
('주유',            7, 'CONTAINS', 75),
('오일',            7, 'CONTAINS', 75),
('통행료',          7, 'CONTAINS', 80),
('하이패스',        7, 'CONTAINS', 80),
('주차',            7, 'CONTAINS', 75),

-- ============================================================
-- 의류비 (8) — 플랫폼, 글로벌 브랜드, 백화점·아울렛
-- ============================================================
-- 패션 플랫폼
('무신사',          8, 'EXACT',   100),
('에이블리',        8, 'EXACT',   100),
('지그재그',        8, 'EXACT',   100),
('브랜디',          8, 'EXACT',   100),
('W컨셉',           8, 'EXACT',   100),
('카카오스타일',    8, 'EXACT',   100),
('29CM',            8, 'EXACT',   100),
-- 글로벌 SPA·스포츠
('나이키',          8, 'EXACT',   100),
('아디다스',        8, 'EXACT',   100),
('뉴발란스',        8, 'EXACT',   100),
('자라',            8, 'EXACT',   100),
('H&M',             8, 'EXACT',   100),
('유니클로',        8, 'EXACT',   100),
('스파오',          8, 'EXACT',   100),
('탑텐',            8, 'EXACT',   100),
('휠라',            8, 'EXACT',   100),
('리복',            8, 'EXACT',   100),
('아식스',          8, 'EXACT',   100),
('MLB',             8, 'EXACT',   100),
('빈폴',            8, 'EXACT',   100),
('캘빈클라인',      8, 'EXACT',   100),
('코오롱스포츠',    8, 'EXACT',   100),
('노스페이스',      8, 'EXACT',   100),
('네파',            8, 'EXACT',   100),
('K2',              8, 'EXACT',   100),
-- 백화점·아울렛 (CONTAINS — 지점명 포함)
('신세계백화점',    8, 'CONTAINS', 90),
('롯데백화점',      8, 'CONTAINS', 90),
('현대백화점',      8, 'CONTAINS', 90),
('갤러리아백화점',  8, 'CONTAINS', 90),
('플라자',        8, 'CONTAINS', 85),
('프리미엄아울렛',  8, 'CONTAINS', 85),
('신세계아울렛',    8, 'CONTAINS', 85),
('롯데아울렛',      8, 'CONTAINS', 85),
('패션',            8, 'CONTAINS', 70),

-- ============================================================
-- 문화여가비 (9) — OTT, 음악, 숙박, 영화관, 게임, 스포츠
-- ============================================================
-- OTT
('넷플릭스',        9, 'EXACT',   100),
('NETFLIX',        9, 'EXACT',   100),
('유튜브프리미엄',  9, 'EXACT',   100),
('왓챠',            9, 'EXACT',   100),
('웨이브',          9, 'EXACT',   100),
('티빙',            9, 'EXACT',   100),
('디즈니플러스',    9, 'EXACT',   100),
('애플TV',          9, 'EXACT',   100),
('GOOGLE',          9, 'CONTAINS', 80),  -- 단, 구글 결제는 다양해서 신중히
('구글',            9, 'CONTAINS', 80),

-- 음악 구독
('멜론',            9, 'EXACT',   100),
('스포티파이',      9, 'EXACT',   100),
('플로',            9, 'EXACT',   100),
('지니뮤직',        9, 'EXACT',   100),
('벅스',            9, 'EXACT',   100),
('유튜브',          9, 'CONTAINS', 90),
-- 숙박 예약
('야놀자',          9, 'EXACT',   100),
('여기어때',        9, 'EXACT',   100),
('에어비앤비',      9, 'EXACT',   100),
('호텔스닷컴',      9, 'EXACT',   100),
('아고다',          9, 'EXACT',   100),
('부킹닷컴',        9, 'EXACT',   100),
('호텔',            9, 'CONTAINS', 80),
('모텔',            9, 'CONTAINS', 80),
('펜션',            9, 'CONTAINS', 80),
('게스트하우스',    9, 'CONTAINS', 80),
-- 영화관
('CGV',             9, 'EXACT',   100),
('롯데시네마',      9, 'EXACT',   100),
('메가박스',        9, 'EXACT',   100),
-- 게임
('스팀',            9, 'EXACT',   100),
('넥슨',            9, 'EXACT',   100),
('엔씨소프트',      9, 'EXACT',   100),
('카카오게임즈',    9, 'EXACT',   100),
('넷마블',          9, 'EXACT',   100),
('게임',            9, 'CONTAINS', 70),
-- 스포츠·레저
('스포츠',      9, 'CONTAINS', 85),
('헬스장',          9, 'CONTAINS', 85),
('헬스',          9, 'CONTAINS', 85),
('휘트니스',          9, 'CONTAINS', 85),
('피트니스',        9, 'CONTAINS', 85),
('짐',          9, 'CONTAINS', 80),
('GYM',          9, 'CONTAINS', 80),
('수영장',          9, 'CONTAINS', 80),
('스크린골프',      9, 'CONTAINS', 85),
('골프',            9, 'CONTAINS', 75),
('볼링',            9, 'CONTAINS', 80),
('당구',            9, 'CONTAINS', 75),
-- 테마파크·기타 여가
('에버랜드',        9, 'EXACT',   100),
('롯데월드',        9, 'EXACT',   100),
('인터파크',        9, 'EXACT',   100),  -- 공연·여행 예매
('키즈카페',        9, 'CONTAINS', 85),
('노래방',          9, 'CONTAINS', 85),
('방탈출',          9, 'CONTAINS', 85),
('독서실',          9, 'CONTAINS', 80),
('독서카페',        9, 'CONTAINS', 80),
('테마파크',        9, 'CONTAINS', 80),
('공연',            9, 'CONTAINS', 75),
('콘서트',          9, 'CONTAINS', 75),
('뮤지컬',          9, 'CONTAINS', 75),
('전시',            9, 'CONTAINS', 70),
-- 도서
('교보문고',        9, 'EXACT',   100),
('YES24',           9, 'EXACT',   100),
('알라딘',          9, 'EXACT',   100),
-- 미용
('헤어', 9, 'CONTAINS', 90),
('미용실', 9, 'CONTAINS', 90),

('PC방', 9, 'CONTAINS', 80),

-- ============================================================
-- 의료비 (10) — 의원·병원·약국·검진
-- ============================================================
('병원',           10, 'CONTAINS', 90),
('의원',           10, 'CONTAINS', 90),
('약국',           10, 'CONTAINS', 90),
('치과',           10, 'CONTAINS', 90),
('한의원',         10, 'CONTAINS', 90),
('안과',           10, 'CONTAINS', 90),
('피부과',         10, 'CONTAINS', 90),
('정형외과',       10, 'CONTAINS', 90),
('내과',           10, 'CONTAINS', 90),
('소아과',         10, 'CONTAINS', 90),
('제약',         10, 'CONTAINS', 90),
('산부인과',       10, 'CONTAINS', 90),
('이비인후과',     10, 'CONTAINS', 90),
('정신건강의학과', 10, 'CONTAINS', 90),
('성형외과',       10, 'CONTAINS', 90),
('검진센터',       10, 'CONTAINS', 85),
('건강검진',       10, 'CONTAINS', 85),
('보건소',         10, 'CONTAINS', 85),
('응급실',         10, 'CONTAINS', 85),
('요양병원',       10, 'CONTAINS', 85),
('한방병원',       10, 'CONTAINS', 85),
('마취통증의학과', 10, 'CONTAINS', 85),
('재활의학과',     10, 'CONTAINS', 85),
('GC녹십자',       10, 'CONTAINS', 85),
('의약품',         10, 'CONTAINS', 80);


-- 기타/간편결제 (11) - 상호명 파악 불가
INSERT IGNORE INTO merchant_category_rule (keyword, category_id, match_type, priority) VALUES
('비바리퍼블리카', 11, 'CONTAINS', 70), -- 토스
('네이버파이낸셜', 11, 'CONTAINS', 70), -- 네이버페이
('카카오페이', 11, 'CONTAINS', 60),
('네이버페이', 11, 'CONTAINS', 60),
('나이스페이', 11, 'CONTAINS', 60),    -- PG사 (MCC 확인 필요 구간)
('KCP', 11, 'CONTAINS', 60),          -- PG사
('쿠팡', 11, 'CONTAINS', 75);             -- 쿠팡, 쿠팡이츠

