-- Exchange information
CREATE TABLE exchanges (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    country VARCHAR(10) NOT NULL, -- 'KR' or 'US'
    api_base_url VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Coin information
CREATE TABLE coins (
    id SERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Price data (10-minute intervals)
CREATE TABLE price_data (
    id SERIAL PRIMARY KEY,
    exchange_id INTEGER REFERENCES exchanges(id),
    coin_id INTEGER REFERENCES coins(id),
    price DECIMAL(20, 8) NOT NULL,
    volume_24h DECIMAL(20, 8),
    price_change_24h DECIMAL(10, 4),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    UNIQUE(exchange_id, coin_id, timestamp)
);

-- Create exchange_rates table
CREATE TABLE exchange_rates (
    id SERIAL PRIMARY KEY,
    currency_code VARCHAR(10) NOT NULL,
    rate DECIMAL(20, 8) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Fee information
CREATE TABLE exchange_fees (
    id SERIAL PRIMARY KEY,
    exchange_id INTEGER REFERENCES exchanges(id),
    coin_id INTEGER REFERENCES coins(id),
    trading_fee DECIMAL(6, 4) NOT NULL, -- Trading fee (%)
    withdrawal_fee DECIMAL(20, 8) NOT NULL, -- Withdrawal fee (coin unit)
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Price data query optimization
CREATE INDEX idx_price_data_exchange_coin_time ON price_data(exchange_id, coin_id, timestamp DESC);
CREATE INDEX idx_price_data_timestamp ON price_data(timestamp DESC);

-- Exchange rate query optimization
CREATE INDEX idx_exchange_rates_currency_created ON exchange_rates(currency_code, created_at DESC);

-- Exchange data
INSERT INTO exchanges (name, country, api_base_url) VALUES
('Binance', 'US', 'https://api.binance.com'),
('Upbit', 'KR', 'https://api.upbit.com'),
('Bithumb', 'KR', 'https://api.bithumb.com');

-- Major coin data
INSERT INTO coins (symbol, name) VALUES
('BTC', 'Bitcoin'),
('ETH', 'Ethereum'),
('XRP', 'Ripple'),
('SOL', 'Solana')
