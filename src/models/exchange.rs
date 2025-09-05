use serde::{Deserialize, Serialize};
use chrono::{DateTime, Utc};
use bigdecimal::BigDecimal;

#[derive(Debug, Serialize, Deserialize, Clone, sqlx::FromRow)]
pub struct Exchange {
    pub id: i32,
    pub name: String,
    pub country: String,
    pub api_base_url: String,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub enum ExchangeType {
    Binance,
    Upbit,
    Bithumb,
}

impl std::fmt::Display for ExchangeType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ExchangeType::Binance => write!(f, "Binance"),
            ExchangeType::Upbit => write!(f, "Upbit"),
            ExchangeType::Bithumb => write!(f, "Bithumb"),
        }
    }
}

#[derive(Debug, Serialize, Deserialize, Clone, sqlx::FromRow)]
pub struct PriceData {
    pub id: i32,
    pub exchange_id: i32,
    pub coin_id: i32,
    pub price: BigDecimal,
    pub volume_24h: Option<BigDecimal>,
    pub price_change_24h: Option<BigDecimal>,
    pub timestamp: DateTime<Utc>,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct NewPriceData {
    pub exchange_id: i32,
    pub coin_id: i32,
    pub price: BigDecimal,
    pub volume_24h: Option<BigDecimal>,
    pub price_change_24h: Option<BigDecimal>,
    pub timestamp: DateTime<Utc>,
}

#[derive(Debug, Serialize, Deserialize, Clone, sqlx::FromRow)]
pub struct ExchangeRate {
    pub id: i32,
    pub from_currency: String,
    pub to_currency: String,
    pub rate: BigDecimal,
    pub timestamp: DateTime<Utc>,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct NewExchangeRate {
    pub from_currency: String,
    pub to_currency: String,
    pub rate: BigDecimal,
    pub timestamp: DateTime<Utc>,
}

#[derive(Debug, Serialize, Deserialize, Clone, sqlx::FromRow)]
pub struct ExchangeFee {
    pub id: i32,
    pub exchange_id: i32,
    pub coin_id: i32,
    pub trading_fee: BigDecimal,
    pub withdrawal_fee: BigDecimal,
    pub updated_at: DateTime<Utc>,
}

// API 응답 구조체들
#[derive(Debug, Deserialize)]
pub struct BinanceTickerResponse {
    pub symbol: String,
    #[serde(rename = "lastPrice")]
    pub last_price: String,
    pub volume: String,
    #[serde(rename = "priceChangePercent")]
    pub price_change_percent: String,
}

#[derive(Debug, Deserialize)]
pub struct UpbitTickerResponse {
    pub market: String,
    pub trade_price: f64,
    #[serde(rename = "acc_trade_volume_24h")]
    pub acc_trade_volume_24h: f64,
    pub signed_change_rate: f64,
}

#[derive(Debug, Deserialize)]
pub struct BithumbTickerResponse {
    pub status: String,
    pub data: BithumbTickerData,
}

#[derive(Debug, Deserialize)]
pub struct BithumbTickerData {
    pub closing_price: String,
    #[serde(rename = "units_traded_24H")]
    pub units_traded_24h: String,
    #[serde(rename = "fluctate_rate_24H")]
    pub fluctate_rate_24h: String,
}
