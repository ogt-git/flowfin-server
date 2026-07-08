-- exchange_rate table DDL.
-- Run manually when an existing local/prod database was created before this table was added.
-- Same schema as docker/init.sql. Safe to run repeatedly.

CREATE TABLE IF NOT EXISTS exchange_rate
(
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    currency_code          VARCHAR(10)    NOT NULL COMMENT 'Normalized currency code (USD, CNY, JPY)',
    base_currency          VARCHAR(10)    NOT NULL DEFAULT 'KRW',
    rate                   DECIMAL(15, 4) NOT NULL COMMENT '1 currency_code = rate KRW',
    base_date              DATE           NOT NULL COMMENT 'Exchange-rate base date',
    provider               VARCHAR(50)    NOT NULL,
    fetched_at             DATETIME       NOT NULL,
    original_currency_code VARCHAR(20)    COMMENT 'Source currency code (CNH, JPY(100), etc.)',
    original_rate_str      VARCHAR(50)    COMMENT 'Source deal_bas_r string',
    created_at             DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_er_currency_date (currency_code, base_date),
    INDEX idx_er_currency_date (currency_code, base_date),
    INDEX idx_er_currency_fetched (currency_code, fetched_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
