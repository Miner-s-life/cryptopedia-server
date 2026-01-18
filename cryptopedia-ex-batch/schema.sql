-- 1. Symbols Table
CREATE TABLE IF NOT EXISTS symbols (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exchange VARCHAR(20) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    base_asset VARCHAR(20) NOT NULL,
    quote_asset VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_symbols_ex_sy (exchange, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 1-Minute Candles Table
CREATE TABLE IF NOT EXISTS candles_1m (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exchange VARCHAR(20) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    open_time DATETIME(6) NOT NULL,
    open_price DECIMAL(32,8) NOT NULL,
    high_price DECIMAL(32,8) NOT NULL,
    low_price DECIMAL(32,8) NOT NULL,
    close_price DECIMAL(32,8) NOT NULL,
    volume DECIMAL(32,8) NOT NULL,
    quote_volume DECIMAL(32,8) NOT NULL,
    trades BIGINT NOT NULL,
    UNIQUE KEY idx_ex_sy_ot (exchange, symbol, open_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 24h Ticker Table
CREATE TABLE IF NOT EXISTS ticker_24h_latest (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exchange VARCHAR(20) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    last_price DECIMAL(32,8) NOT NULL,
    price_change_percent DECIMAL(32,8) NOT NULL,
    volume24h DECIMAL(32,8) NOT NULL,
    quote_volume24h DECIMAL(32,8) NOT NULL,
    last_updated DATETIME(6) NOT NULL,
    UNIQUE KEY uk_ticker_ex_symbol (exchange, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Daily Volume Stats Table (Phase 2)
CREATE TABLE IF NOT EXISTS daily_volume_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exchange VARCHAR(20) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    date DATE NOT NULL,
    volume_sum DECIMAL(32,8) NOT NULL,
    quote_volume_sum DECIMAL(32,8) NOT NULL,
    volume_ma_7d DECIMAL(32,8) NULL,
    volume_ma_30d DECIMAL(32,8) NULL,
    UNIQUE KEY uk_stats_ex_sy_date (exchange, symbol, date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Symbol Metrics Table (Phase 2)
CREATE TABLE IF NOT EXISTS symbol_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exchange VARCHAR(20) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    rvol_1m DECIMAL(32,8) NOT NULL DEFAULT 0,
    rvol_5m DECIMAL(32,8) NOT NULL DEFAULT 0,
    rvol_15m DECIMAL(32,8) NOT NULL DEFAULT 0,
    rvol_30m DECIMAL(32,8) NOT NULL DEFAULT 0,
    rvol_1h DECIMAL(32,8) NOT NULL DEFAULT 0,
    rvol_4h DECIMAL(32,8) NOT NULL DEFAULT 0,
    rvol_today DECIMAL(32,8) NOT NULL DEFAULT 0,
    price_change_percent24h DECIMAL(32,8) NOT NULL,
    price_change_percent_today DECIMAL(32,8) NOT NULL DEFAULT 0,
    is_surging BIT(1) NOT NULL,
    last_updated DATETIME(6) NOT NULL,
    UNIQUE KEY uk_metrics_ex_sy (exchange, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL,
    last_login_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. Refresh Tokens Table
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(512) NOT NULL UNIQUE,
    expired_at DATETIME(6) NOT NULL,
    revoked BIT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_refresh_tokens_user_id FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. Signup Requests Table
CREATE TABLE IF NOT EXISTS signup_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone_number VARCHAR(20),
    comment TEXT,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
