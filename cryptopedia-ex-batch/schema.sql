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
    open_price DECIMAL(18,8) NOT NULL,
    high_price DECIMAL(18,8) NOT NULL,
    low_price DECIMAL(18,8) NOT NULL,
    close_price DECIMAL(18,8) NOT NULL,
    volume DECIMAL(18,8) NOT NULL,
    quote_volume DECIMAL(18,8) NOT NULL,
    trades BIGINT NOT NULL,
    UNIQUE KEY idx_ex_sy_ot (exchange, symbol, open_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 24h Ticker Table
CREATE TABLE IF NOT EXISTS ticker_24h_latest (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exchange VARCHAR(20) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    last_price DECIMAL(18,8) NOT NULL,
    price_change_percent DECIMAL(10,4) NOT NULL,
    volume24h DECIMAL(18,8) NOT NULL,
    quote_volume24h DECIMAL(18,8) NOT NULL,
    last_updated DATETIME(6) NOT NULL,
    UNIQUE KEY uk_ticker_ex_symbol (exchange, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Daily Volume Stats Table (Phase 2)
CREATE TABLE IF NOT EXISTS daily_volume_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exchange VARCHAR(20) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    date DATE NOT NULL,
    volume_sum DECIMAL(18,8) NOT NULL,
    quote_volume_sum DECIMAL(18,8) NOT NULL,
    volume_ma_7d DECIMAL(18,8) NULL,
    volume_ma_30d DECIMAL(18,8) NULL,
    UNIQUE KEY uk_stats_ex_sy_date (exchange, symbol, date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Symbol Metrics Table (Phase 2)
CREATE TABLE IF NOT EXISTS symbol_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exchange VARCHAR(20) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    rvol DECIMAL(10,4) NOT NULL,
    price_change_percent24h DECIMAL(10,4) NOT NULL,
    is_surging BIT(1) NOT NULL,
    last_updated DATETIME(6) NOT NULL,
    UNIQUE KEY uk_metrics_ex_sy (exchange, symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
