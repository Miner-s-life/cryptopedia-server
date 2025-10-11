-- MySQL initial schema for cryptopedia
-- Engine/charset settings
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Exchanges
CREATE TABLE IF NOT EXISTS exchanges (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL UNIQUE,
  country VARCHAR(10) NOT NULL,
  api_base_url VARCHAR(255) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Coins
CREATE TABLE IF NOT EXISTS coins (
  id INT AUTO_INCREMENT PRIMARY KEY,
  symbol VARCHAR(20) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  is_active BOOLEAN DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Price data (fine-grained timestamps)
CREATE TABLE IF NOT EXISTS price_data (
  id INT AUTO_INCREMENT PRIMARY KEY,
  exchange_id INT NOT NULL,
  coin_id INT NOT NULL,
  price DECIMAL(20,8) NOT NULL,
  volume_24h DECIMAL(20,8) NULL,
  price_change_24h DECIMAL(10,4) NULL,
  timestamp DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uniq_exchange_coin_ts (exchange_id, coin_id, timestamp),
  KEY idx_price_data_exchange_coin_time (exchange_id, coin_id, timestamp),
  KEY idx_price_data_timestamp (timestamp),
  CONSTRAINT fk_price_data_exchange FOREIGN KEY (exchange_id) REFERENCES exchanges(id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_price_data_coin FOREIGN KEY (coin_id) REFERENCES coins(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Exchange rates
CREATE TABLE IF NOT EXISTS exchange_rates (
  id INT AUTO_INCREMENT PRIMARY KEY,
  currency_code VARCHAR(3) NOT NULL,
  rate DECIMAL(15,4) NOT NULL,
  ttb_rate DECIMAL(15,4) NULL,
  tts_rate DECIMAL(15,4) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  KEY idx_exchange_rates_currency_created (currency_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Exchange fees
CREATE TABLE IF NOT EXISTS exchange_fees (
  id INT AUTO_INCREMENT PRIMARY KEY,
  exchange_id INT NOT NULL,
  coin_id INT NOT NULL,
  trading_fee DECIMAL(6,4) NOT NULL,
  withdrawal_fee DECIMAL(20,8) NOT NULL,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  KEY idx_fees_exchange_coin (exchange_id, coin_id),
  CONSTRAINT fk_fees_exchange FOREIGN KEY (exchange_id) REFERENCES exchanges(id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_fees_coin FOREIGN KEY (coin_id) REFERENCES coins(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Seed exchanges
INSERT INTO exchanges (name, country, api_base_url) VALUES
  ('Binance', 'US', 'https://api.binance.com'),
  ('Upbit', 'KR', 'https://api.upbit.com'),
  ('Bithumb', 'KR', 'https://api.bithumb.com')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Seed coins
INSERT INTO coins (symbol, name) VALUES
  ('BTC', 'Bitcoin'),
  ('ETH', 'Ethereum'),
  ('XRP', 'Ripple'),
  ('SOL', 'Solana'),
  ('USDT', 'Tether')
ON DUPLICATE KEY UPDATE symbol = VALUES(symbol);
