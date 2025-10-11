use anyhow::Result;
use bigdecimal::BigDecimal;
use sqlx::{MySqlPool, Row};
use std::str::FromStr;

use crate::models::DirectionalArbitrage;
use crate::services::ExchangeRateService;

#[derive(Clone)]
pub struct ArbitrageService {
    db: MySqlPool,
    exchange_rate_service: ExchangeRateService,
}

#[derive(Clone, Copy)]
pub enum FxSource {
    UsdtKrw,
    UsdKrw,
}

impl ArbitrageService {
    pub fn new(db: MySqlPool, auth_key: String) -> Self {
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
        let rows = sqlx::query(
            r#"
            SELECT name, price FROM (
              SELECT e.name AS name, pd.price,
                     ROW_NUMBER() OVER (PARTITION BY e.name ORDER BY pd.timestamp DESC) AS rn
              FROM price_data pd
              JOIN exchanges e ON pd.exchange_id = e.id
              JOIN coins c ON pd.coin_id = c.id
              WHERE c.symbol = ?
                AND pd.timestamp >= NOW() - INTERVAL 30 MINUTE
            ) t
            WHERE rn = 1
            "#
        )
        .bind(symbol)
        .fetch_all(&self.db)
        .await?;

        let mut prices: Vec<(String, BigDecimal)> = Vec::with_capacity(rows.len());
        for row in rows {
            let name: String = row.try_get("name")?;
            let price: BigDecimal = row.try_get("price")?;
            prices.push((name, price));
        }
        Ok(prices)
    }

    pub async fn get_directional_arbitrage(&self, symbol: &str, from_exchange: &str, to_exchange: &str) -> Result<DirectionalArbitrage> {
        self.get_directional_arbitrage_with_options(symbol, from_exchange, to_exchange, FxSource::UsdtKrw, true).await
    }

    pub async fn get_directional_arbitrage_with_options(
        &self,
        symbol: &str,
        from_exchange: &str,
        to_exchange: &str,
        fx_source: FxSource,
        include_fees: bool,
    ) -> Result<DirectionalArbitrage> {
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
        
        let (adjusted_from_price, adjusted_to_price, fx_rate, fx_type) = self
            .adjust_prices_for_currency(&from_price, &to_price, from_exchange, to_exchange, &fx_source)
            .await?;
        
        let price_difference = &adjusted_to_price - &adjusted_from_price;
        let profit_percentage = (&price_difference / &adjusted_from_price) * BigDecimal::from(100);
        
        let total_fees = if include_fees {
            self.calculate_total_fees(symbol, from_exchange, to_exchange).await?
        } else {
            BigDecimal::from(0)
        };
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
            fx_type,
            fx_rate,
        })
    }
    
    async fn adjust_prices_for_currency(
        &self,
        from_price: &BigDecimal,
        to_price: &BigDecimal,
        from_exchange: &str,
        to_exchange: &str,
        fx_source: &FxSource,
    ) -> Result<(BigDecimal, BigDecimal, BigDecimal, String)> {
        let fx_rate = match fx_source {
            FxSource::UsdtKrw => {
                match self.get_usdt_krw_price().await {
                    Ok(price) => price,
                    Err(_) => self
                        .exchange_rate_service
                        .get_latest_usd_krw_rate()
                        .await
                        .unwrap_or_else(|_| ExchangeRateService::get_fallback_usd_krw_rate()),
                }
            }
            FxSource::UsdKrw => {
                self
                    .exchange_rate_service
                    .get_latest_usd_krw_rate()
                    .await
                    .unwrap_or_else(|_| ExchangeRateService::get_fallback_usd_krw_rate())
            }
        };

        let adjusted_from = if from_exchange == "Binance" {
            from_price * &fx_rate
        } else {
            from_price.clone()
        };
        
        let adjusted_to = if to_exchange == "Binance" {
            to_price * &fx_rate
        } else {
            to_price.clone()
        };
        let fx_type = match fx_source { FxSource::UsdKrw => "usdkrw", FxSource::UsdtKrw => "usdtkrw" }.to_string();
        Ok((adjusted_from, adjusted_to, fx_rate, fx_type))
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

    pub async fn get_directional_arbitrage_list(
        &self,
        from_exchange: &str,
        to_exchange: &str,
        fx_source: FxSource,
        include_fees: bool,
        limit: Option<i64>,
    ) -> Result<Vec<DirectionalArbitrage>> {
        // 1) 심볼 목록 조회
        let symbols: Vec<String> = if let Some(lim) = limit {
            let rows = sqlx::query!(
                "SELECT symbol FROM coins WHERE is_active = true ORDER BY symbol LIMIT ?",
                lim
            )
            .fetch_all(&self.db)
            .await?;
            rows.into_iter().map(|r| r.symbol).collect()
        } else {
            let rows = sqlx::query!(
                "SELECT symbol FROM coins WHERE is_active = true ORDER BY symbol"
            )
            .fetch_all(&self.db)
            .await?;
            rows.into_iter().map(|r| r.symbol).collect()
        };

        // 2) 각 심볼에 대해 계산 (초기 버전: 순차 실행)
        let mut out: Vec<DirectionalArbitrage> = Vec::with_capacity(symbols.len());
        for sym in symbols {
            match self
                .get_directional_arbitrage_with_options(&sym, from_exchange, to_exchange, fx_source, include_fees)
                .await
            {
                Ok(item) => out.push(item),
                Err(_) => {
                    // 개별 실패는 건너뜀
                }
            }
        }

        // 3) 프리미엄 내림차순 정렬
        out.sort_by(|a, b| b.profit_percentage.cmp(&a.profit_percentage));
        Ok(out)
    }
}
