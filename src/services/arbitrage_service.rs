use anyhow::Result;
use bigdecimal::BigDecimal;
use log::{error, info};
use sqlx::PgPool;
use std::str::FromStr;

use crate::models::{
    ArbitrageOpportunity, ExchangeType, KimchiPremium, FeeCalculation
};
use crate::services::ExchangeRateService;

#[derive(Clone)]
pub struct ArbitrageService {
    db: PgPool,
    exchange_rate_service: ExchangeRateService,
}

impl ArbitrageService {
    pub fn new(db: PgPool, auth_key: String) -> Self {
        let exchange_rate_service = ExchangeRateService::new(db.clone(), auth_key);
        Self { 
            db,
            exchange_rate_service,
        }
    }

    pub async fn find_arbitrage_opportunities(&self) -> Result<Vec<ArbitrageOpportunity>> {
        let symbols = vec!["BTC", "ETH", "XRP", "ADA", "DOT"];
        let mut opportunities = Vec::new();

        for symbol in symbols {
            match self.calculate_arbitrage_for_symbol(&symbol).await {
                Ok(Some(opportunity)) => opportunities.push(opportunity),
                Ok(None) => {},
                Err(e) => error!("Failed to calculate arbitrage for {}: {}", symbol, e),
            }
        }

        info!("Found {} arbitrage opportunities", opportunities.len());
        Ok(opportunities)
    }

    async fn calculate_arbitrage_for_symbol(&self, symbol: &str) -> Result<Option<ArbitrageOpportunity>> {
        let prices = self.get_latest_prices_for_symbol(symbol).await?;
        
        if prices.len() < 2 {
            return Ok(None);
        }

        let exchange_rate = self.exchange_rate_service.get_latest_usd_krw_rate().await
            .unwrap_or_else(|_| ExchangeRateService::get_fallback_usd_krw_rate());

        let mut domestic_prices = Vec::new();
        let mut foreign_prices = Vec::new();

        for (exchange_name, price) in &prices {
            match exchange_name.as_str() {
                "Binance" => {
                    let krw_price = price * &exchange_rate;
                    foreign_prices.push(("Binance".to_string(), price.clone(), krw_price));
                }
                "Upbit" | "Bithumb" => {
                    domestic_prices.push((exchange_name.clone(), price.clone()));
                }
                _ => {}
            }
        }

        if domestic_prices.is_empty() || foreign_prices.is_empty() {
            return Ok(None);
        }

        let mut best_opportunity: Option<ArbitrageOpportunity> = None;
        let mut max_profit = BigDecimal::from(-100);

        for (domestic_exchange, domestic_price) in &domestic_prices {
            for (foreign_exchange, foreign_price_usd, foreign_price_krw) in &foreign_prices {
                let kimchi_premium = self.calculate_kimchi_premium_value(
                    domestic_price, 
                    foreign_price_krw
                );

                let total_fees = self.calculate_total_fees(symbol, domestic_exchange, foreign_exchange).await?;

                let (buy_exchange, sell_exchange, buy_price, sell_price) = 
                    if kimchi_premium > BigDecimal::from(0) {
                        // 해외 매수,국내 매도
                        (
                            ExchangeType::Binance,
                            self.exchange_name_to_type(domestic_exchange),
                            foreign_price_usd.clone(),
                            domestic_price.clone()
                        )
                    } else {
                        // 국내 매수, 해외 매도
                        (
                            self.exchange_name_to_type(domestic_exchange),
                            ExchangeType::Binance,
                            domestic_price.clone(),
                            foreign_price_usd.clone()
                        )
                    };

                let opportunity = ArbitrageOpportunity::new(
                    symbol.to_string(),
                    kimchi_premium,
                    buy_exchange,
                    sell_exchange,
                    buy_price,
                    sell_price,
                    total_fees,
                );

                if opportunity.estimated_profit > max_profit {
                    max_profit = opportunity.estimated_profit.clone();
                    best_opportunity = Some(opportunity);
                }
            }
        }

        Ok(best_opportunity)
    }

    pub async fn calculate_kimchi_premium(&self, symbol: &str) -> Result<KimchiPremium> {
        let prices = self.get_latest_prices_for_symbol(symbol).await?;
        let exchange_rate = self.exchange_rate_service.get_latest_usd_krw_rate().await
            .unwrap_or_else(|_| ExchangeRateService::get_fallback_usd_krw_rate());

        let mut domestic_price = BigDecimal::from(0);
        let mut foreign_price = BigDecimal::from(0);

        for (exchange_name, price) in prices {
            match exchange_name.as_str() {
                "Binance" => foreign_price = price,
                "Upbit" | "Bithumb" => domestic_price = price,
                _ => {}
            }
        }

        if domestic_price == BigDecimal::from(0) || foreign_price == BigDecimal::from(0) {
            return Err(anyhow::anyhow!("Insufficient price data for {}", symbol));
        }

        let foreign_price_krw = &foreign_price * &exchange_rate;
        
        let premium_percentage = self.calculate_kimchi_premium_value(&domestic_price, &foreign_price_krw);

        Ok(KimchiPremium {
            symbol: symbol.to_string(),
            domestic_price,
            foreign_price: foreign_price_krw,
            premium_percentage,
            exchange_rate,
        })
    }

    fn calculate_kimchi_premium_value(&self, domestic_price: &BigDecimal, foreign_price_krw: &BigDecimal) -> BigDecimal {
        if foreign_price_krw == &BigDecimal::from(0) {
            return BigDecimal::from(0);
        }
        
        ((domestic_price - foreign_price_krw) / foreign_price_krw) * BigDecimal::from(100)
    }

    async fn calculate_total_fees(&self, symbol: &str, _buy_exchange: &str, _sell_exchange: &str) -> Result<BigDecimal> {
        // TODO: 기본 수수료 (실제로는 DB에서 조회해야 함)
        let trading_fee = BigDecimal::from_str("0.1")?; // 0.1%
        let _withdrawal_fee = match symbol {
            "BTC" => BigDecimal::from_str("0.0005")?,
            "ETH" => BigDecimal::from_str("0.005")?,
            _ => BigDecimal::from_str("0.001")?,
        };
        let exchange_fee = BigDecimal::from_str("0.05")?; // 환전 수수료 0.05%

        // 총 수수료 = 매수 수수료 + 출금 수수료 + 매도 수수료 + 환전 수수료
        let total_fee = &trading_fee * BigDecimal::from(2) + &exchange_fee; // 매수/매도 각각 + 환전

        Ok(total_fee)
    }

    async fn get_latest_prices_for_symbol(&self, symbol: &str) -> Result<Vec<(String, BigDecimal)>> {
        let results = sqlx::query!(
            r#"
            SELECT e.name, pd.price
            FROM price_data pd
            JOIN exchanges e ON pd.exchange_id = e.id
            JOIN coins c ON pd.coin_id = c.id
            WHERE c.symbol = $1
            AND pd.timestamp >= NOW() - INTERVAL '30 minutes'
            ORDER BY pd.timestamp DESC
            LIMIT 10
            "#,
            symbol
        )
        .fetch_all(&self.db)
        .await?;

        let prices: Vec<(String, BigDecimal)> = results
            .into_iter()
            .map(|row| (row.name, row.price))
            .collect();

        Ok(prices)
    }


    fn exchange_name_to_type(&self, name: &str) -> ExchangeType {
        match name {
            "Binance" => ExchangeType::Binance,
            "Upbit" => ExchangeType::Upbit,
            "Bithumb" => ExchangeType::Bithumb,
            _ => ExchangeType::Binance, // 기본값
        }
    }

    pub async fn get_fee_calculation(&self, symbol: &str, amount: BigDecimal) -> Result<FeeCalculation> {
        let trading_fee = BigDecimal::from_str("0.1")? * &amount / BigDecimal::from(100);
        let withdrawal_fee = match symbol {
            "BTC" => BigDecimal::from_str("0.0005")?,
            "ETH" => BigDecimal::from_str("0.005")?,
            _ => BigDecimal::from_str("0.001")?,
        };
        let exchange_fee = BigDecimal::from_str("0.05")? * &amount / BigDecimal::from(100);
        let total_fee = &trading_fee + &withdrawal_fee + &exchange_fee;

        Ok(FeeCalculation {
            trading_fee,
            withdrawal_fee,
            exchange_fee,
            total_fee,
        })
    }
}
