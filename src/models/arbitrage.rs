use serde::{Deserialize, Serialize};
use bigdecimal::BigDecimal;
use crate::models::ExchangeType;

#[derive(Debug, Serialize, Deserialize)]
pub struct ArbitrageOpportunity {
    pub symbol: String,
    pub kimchi_premium: BigDecimal,
    pub buy_exchange: ExchangeType,
    pub sell_exchange: ExchangeType,
    pub buy_price: BigDecimal,
    pub sell_price: BigDecimal,
    pub estimated_profit: BigDecimal,
    pub total_fees: BigDecimal,
    pub recommendation: ArbitrageRecommendation,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct DirectionalArbitrage {
    pub symbol: String,
    pub from_exchange: String,
    pub to_exchange: String,
    pub from_price: BigDecimal,
    pub to_price: BigDecimal,
    pub price_difference: BigDecimal,
    pub profit_percentage: BigDecimal,
    pub estimated_profit_after_fees: BigDecimal,
    pub total_fees: BigDecimal,
    pub is_profitable: bool,
}

#[derive(Debug, Serialize, Deserialize)]
pub enum ArbitrageRecommendation {
    Buy { profit_percentage: BigDecimal },
    Sell { profit_percentage: BigDecimal },
    Hold { reason: String },
}

#[derive(Debug, Serialize, Deserialize)]
pub struct KimchiPremium {
    pub symbol: String,
    pub domestic_price: BigDecimal,
    pub foreign_price: BigDecimal,
    pub premium_percentage: BigDecimal,
    pub exchange_rate: BigDecimal,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct FeeCalculation {
    pub trading_fee: BigDecimal,
    pub withdrawal_fee: BigDecimal,
    pub exchange_fee: BigDecimal,
    pub total_fee: BigDecimal,
}

