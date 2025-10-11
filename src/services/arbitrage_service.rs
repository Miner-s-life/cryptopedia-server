use anyhow::Result;
use bigdecimal::BigDecimal;
use sqlx::{MySqlPool, Row};
use chrono::Utc;
use std::str::FromStr;

use crate::models::DirectionalArbitrage;
use crate::services::ExchangeRateService;
use crate::models::KimchiHistoryPoint;

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

    async fn get_latest_price_volume_for_symbol(&self, symbol: &str) -> Result<Vec<(String, BigDecimal, Option<BigDecimal>)>> {
        let rows = sqlx::query(
            r#"
            SELECT name, price, volume_24h FROM (
              SELECT e.name AS name, pd.price, pd.volume_24h,
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

        let mut out: Vec<(String, BigDecimal, Option<BigDecimal>)> = Vec::with_capacity(rows.len());
        for row in rows {
            let name: String = row.try_get("name")?;
            let price: BigDecimal = row.try_get("price")?;
            let volume: Option<BigDecimal> = row.try_get("volume_24h")?;
            out.push((name, price, volume));
        }
        Ok(out)
    }

    pub async fn record_kimchi_snapshot(&self, symbol: &str, from_exchange: &str, to_exchange: &str, fx_source: FxSource) -> Result<()> {
        let ar = self.get_directional_arbitrage_with_options(symbol, from_exchange, to_exchange, fx_source, false).await?;
        let ts = Utc::now().naive_utc();
        sqlx::query!(
            r#"
            INSERT INTO kimchi_history (
              symbol, from_exchange, to_exchange, fx_type, ts,
              from_price_krw, to_price_krw, profit_percentage,
              from_volume_24h, to_volume_24h, from_notional_24h, to_notional_24h
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            "#,
            ar.symbol,
            ar.from_exchange,
            ar.to_exchange,
            ar.fx_type,
            ts,
            ar.from_price,
            ar.to_price,
            ar.profit_percentage,
            ar.from_volume_24h,
            ar.to_volume_24h,
            ar.from_notional_24h,
            ar.to_notional_24h
        )
        .execute(&self.db)
        .await?;
        Ok(())
    }

    pub async fn get_kimchi_history(&self, symbol: &str, from_exchange: &str, to_exchange: &str, minutes: i64) -> Result<Vec<KimchiHistoryPoint>> {
        let rows = sqlx::query(
            r#"
            SELECT ts, from_price_krw, to_price_krw, profit_percentage,
                   from_notional_24h, to_notional_24h
            FROM kimchi_history
            WHERE symbol = ? AND from_exchange = ? AND to_exchange = ?
              AND ts >= NOW() - INTERVAL ? MINUTE
            ORDER BY ts ASC
            "#
        )
        .bind(symbol)
        .bind(from_exchange)
        .bind(to_exchange)
        .bind(minutes)
        .fetch_all(&self.db)
        .await?;

        let mut out = Vec::with_capacity(rows.len());
        for row in rows {
            let ts: chrono::NaiveDateTime = row.try_get("ts")?;
            let from_price: BigDecimal = row.try_get("from_price_krw")?;
            let to_price: BigDecimal = row.try_get("to_price_krw")?;
            let pct: BigDecimal = row.try_get("profit_percentage")?;
            let from_notional: Option<BigDecimal> = row.try_get("from_notional_24h")?;
            let to_notional: Option<BigDecimal> = row.try_get("to_notional_24h")?;
            out.push(KimchiHistoryPoint {
                ts: chrono::DateTime::<Utc>::from_naive_utc_and_offset(ts, Utc).to_rfc3339(),
                from_price_krw: from_price,
                to_price_krw: to_price,
                profit_percentage: pct,
                from_notional_24h: from_notional,
                to_notional_24h: to_notional,
            });
        }
        Ok(out)
    }

    pub async fn get_directional_arbitrage_with_options(
        &self,
        symbol: &str,
        from_exchange: &str,
        to_exchange: &str,
        fx_source: FxSource,
        include_fees: bool,
    ) -> Result<DirectionalArbitrage> {
        let rows = self.get_latest_price_volume_for_symbol(symbol).await?;
        let mut from_price: Option<BigDecimal> = None;
        let mut to_price: Option<BigDecimal> = None;
        let mut from_vol: Option<BigDecimal> = None;
        let mut to_vol: Option<BigDecimal> = None;
        
        for (exchange_name, price, volume) in rows {
            if exchange_name == from_exchange {
                from_price = Some(price);
                from_vol = volume;
            } else if exchange_name == to_exchange {
                to_price = Some(price);
                to_vol = volume;
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
        
        // Compute KRW notionals (price already adjusted to KRW when needed)
        let from_notional_24h = from_vol.as_ref().map(|v| &adjusted_from_price * v);
        let to_notional_24h = to_vol.as_ref().map(|v| &adjusted_to_price * v);

        Ok(DirectionalArbitrage {
            symbol: symbol.to_string(),
            from_exchange: from_exchange.to_string(),
            to_exchange: to_exchange.to_string(),
            from_price: adjusted_from_price,
            to_price: adjusted_to_price,
            from_volume_24h: from_vol,
            to_volume_24h: to_vol,
            from_notional_24h,
            to_notional_24h,
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
        // 1) 두 거래소 모두에 상장된 심볼 목록 조회 (coin_listings 교집합)
        let symbols: Vec<String> = self.get_common_symbols(from_exchange, to_exchange, limit).await?;

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

    async fn get_common_symbols(&self, from_exchange: &str, to_exchange: &str, limit: Option<i64>) -> Result<Vec<String>> {
        let base_sql = r#"
            SELECT c.symbol
            FROM coin_listings cl1
            JOIN exchanges e1 ON cl1.exchange_id = e1.id
            JOIN coin_listings cl2 ON cl2.coin_id = cl1.coin_id
            JOIN exchanges e2 ON cl2.exchange_id = e2.id
            JOIN coins c ON c.id = cl1.coin_id
            WHERE e1.name = ?
              AND e2.name = ?
              AND cl1.is_active = TRUE
              AND cl2.is_active = TRUE
            ORDER BY c.symbol
        "#;
        let rows = if let Some(lim) = limit {
            sqlx::query(&format!("{} LIMIT ?", base_sql))
                .bind(from_exchange)
                .bind(to_exchange)
                .bind(lim)
                .fetch_all(&self.db)
                .await?
        } else {
            sqlx::query(base_sql)
                .bind(from_exchange)
                .bind(to_exchange)
                .fetch_all(&self.db)
                .await?
        };
        let mut out = Vec::with_capacity(rows.len());
        for row in rows {
            let sym: String = row.try_get("symbol")?;
            out.push(sym);
        }
        Ok(out)
    }
}
