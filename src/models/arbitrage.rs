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

impl ArbitrageOpportunity {
    pub fn new(
        symbol: String,
        kimchi_premium: BigDecimal,
        buy_exchange: ExchangeType,
        sell_exchange: ExchangeType,
        buy_price: BigDecimal,
        sell_price: BigDecimal,
        total_fees: BigDecimal,
    ) -> Self {
        let estimated_profit = ((&sell_price - &buy_price) / &buy_price * BigDecimal::from(100)) - &total_fees;
        
        let recommendation = if estimated_profit > BigDecimal::from(1) {
            ArbitrageRecommendation::Buy {
                profit_percentage: estimated_profit.clone(),
            }
        } else if estimated_profit < BigDecimal::from(-1) {
            ArbitrageRecommendation::Sell {
                profit_percentage: estimated_profit.abs(),
            }
        } else {
            ArbitrageRecommendation::Hold {
                reason: "수수료 대비 수익성 부족".to_string(),
            }
        };

        Self {
            symbol,
            kimchi_premium,
            buy_exchange,
            sell_exchange,
            buy_price,
            sell_price,
            estimated_profit,
            total_fees,
            recommendation,
        }
    }
}
