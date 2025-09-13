use anyhow::Result;
use bigdecimal::BigDecimal;
use sqlx::PgPool;
use std::str::FromStr;

use crate::models::DirectionalArbitrage;
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
            SELECT DISTINCT ON (e.name) e.name, pd.price
            FROM price_data pd
            JOIN exchanges e ON pd.exchange_id = e.id
            JOIN coins c ON pd.coin_id = c.id
            WHERE c.symbol = $1
            AND pd.timestamp >= NOW() - INTERVAL '30 minutes'
            ORDER BY e.name, pd.timestamp DESC
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

    pub async fn get_directional_arbitrage(&self, symbol: &str, from_exchange: &str, to_exchange: &str) -> Result<DirectionalArbitrage> {
        let prices = self.get_latest_prices_for_symbol(symbol).await?;
        
        let mut from_price: Option<BigDecimal> = None;
        let mut to_price: Option<BigDecimal> = None;
        
        for (exchange_name, price) in prices {
            if exchange_name == from_exchange {
                from_price = Some(price);
            } else if exchange_name == to_exchange {
                to_price = Some(price);
            }
        }
        
        let from_price = from_price.ok_or_else(|| anyhow::anyhow!("Price not found for {}", from_exchange))?;
        let to_price = to_price.ok_or_else(|| anyhow::anyhow!("Price not found for {}", to_exchange))?;
        
        let (adjusted_from_price, adjusted_to_price) = self.adjust_prices_for_currency(&from_price, &to_price, from_exchange, to_exchange).await?;
        
        let price_difference = &adjusted_to_price - &adjusted_from_price;
        let profit_percentage = (&price_difference / &adjusted_from_price) * BigDecimal::from(100);
        
        let total_fees = self.calculate_total_fees(symbol, from_exchange, to_exchange).await?;
        let estimated_profit_after_fees = &profit_percentage - &total_fees;
        let is_profitable = estimated_profit_after_fees > BigDecimal::from(0);
        
        Ok(DirectionalArbitrage {
            symbol: symbol.to_string(),
            from_exchange: from_exchange.to_string(),
            to_exchange: to_exchange.to_string(),
            from_price: adjusted_from_price,
            to_price: adjusted_to_price,
            price_difference,
            profit_percentage,
            estimated_profit_after_fees,
            total_fees,
            is_profitable,
        })
    }
    
    async fn adjust_prices_for_currency(&self, from_price: &BigDecimal, to_price: &BigDecimal, from_exchange: &str, to_exchange: &str) -> Result<(BigDecimal, BigDecimal)> {
        let usdt_krw_price = match self.get_usdt_krw_price().await {
            Ok(price) => price,
            Err(_) => self.exchange_rate_service.get_latest_usd_krw_rate().await.unwrap_or_else(|_| ExchangeRateService::get_fallback_usd_krw_rate())
        };
        
        let adjusted_from = if from_exchange == "Binance" {
            from_price * &usdt_krw_price
        } else {
            from_price.clone()
        };
        
        let adjusted_to = if to_exchange == "Binance" {
            to_price * &usdt_krw_price
        } else {
            to_price.clone()
        };
        
        Ok((adjusted_from, adjusted_to))
    }

    async fn get_usdt_krw_price(&self) -> Result<BigDecimal> {
        let usdt_prices = self.get_latest_prices_for_symbol("USDT").await?;
        
        for (exchange_name, price) in usdt_prices {
            match exchange_name.as_str() {
                "Upbit" | "Bithumb" => {
                    return Ok(price);
                }
                _ => {}
            }
        }
        
        Err(anyhow::anyhow!("USDT price not found"))
    }
}
