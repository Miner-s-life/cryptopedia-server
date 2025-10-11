use anyhow::Result;
use bigdecimal::{BigDecimal, FromPrimitive};
use chrono::Utc;
use log::{error, info};
use reqwest::Client;
use sqlx::MySqlPool;
use std::str::FromStr;
use std::collections::HashSet;
use sqlx::QueryBuilder;

use crate::models::{
    BinanceTickerResponse, UpbitTickerResponse, NewPriceData
};

#[derive(Clone)]
pub struct ExchangeService {
    client: Client,
    db: MySqlPool,
}

impl ExchangeService {
    pub fn new(db: MySqlPool) -> Self {
        Self {
            client: Client::new(),
            db,
        }
    }
    async fn process_exchange(&self, ex_name: &str, symbols: HashSet<String>, quote: &str) -> Result<(i64, i64, usize)> {
        let ex_id = self.get_exchange_id_by_name(ex_name).await?;

        // Upsert coins and listings
        let mut upserts = 0i64;
        for sym in &symbols {
            // coins table
            let _ = sqlx::query!(
                r#"INSERT INTO coins (symbol, name, is_active) VALUES (?, ?, TRUE)
                    ON DUPLICATE KEY UPDATE name=VALUES(name)"#,
                sym,
                sym
            )
            .execute(&self.db)
            .await?;

            // coin_id
            let coin_id = match self.get_coin_id(sym).await? { Some(id) => id, None => continue };

            // market_symbol per exchange
            let market_symbol = match ex_name {
                "Binance" => format!("{}USDT", sym),
                "Upbit" => format!("KRW-{}", sym),
                "Bithumb" => format!("{}_KRW", sym),
                _ => sym.to_string(),
            };

            let res = sqlx::query!(
                r#"INSERT INTO coin_listings (exchange_id, coin_id, market_symbol, base, quote, is_active)
                    VALUES (?, ?, ?, ?, ?, TRUE)
                    ON DUPLICATE KEY UPDATE market_symbol=VALUES(market_symbol), base=VALUES(base), quote=VALUES(quote), is_active=TRUE"#,
                ex_id, coin_id, market_symbol, sym, quote
            )
            .execute(&self.db)
            .await?;
            upserts += res.rows_affected() as i64;
        }

        // Deactivate listings not present for this exchange
        let mut qb = QueryBuilder::new("UPDATE coin_listings SET is_active = FALSE WHERE exchange_id = ");
        qb.push_bind(ex_id);
        qb.push(" AND is_active = TRUE");
        if !symbols.is_empty() {
            qb.push(" AND coin_id NOT IN (SELECT id FROM coins WHERE symbol IN (");
            let mut sep = qb.separated(", ");
            for s in &symbols { sep.push_bind(s); }
            sep.push_unseparated("))");
        }
        let deact_res = qb.build().execute(&self.db).await?;
        let deactivated = deact_res.rows_affected() as i64;

        Ok((upserts, deactivated, symbols.len()))
    }

    // Sync coin catalog per exchange using coin_listings
    pub async fn sync_coins(&self, exchange: Option<&str>) -> Result<serde_json::Value> {
        let target = exchange.unwrap_or("all").to_lowercase();
        let mut total_upserts: i64 = 0;
        let mut total_deactivated: i64 = 0;
        let mut total_active: usize = 0;

        // process each exchange sequentially

        // Run per exchange based on target
        if target == "all" || target == "binance" {
            let set = self.list_binance_symbols().await.unwrap_or_default();
            let (u, d, a) = self.process_exchange("Binance", set, "USDT").await?; total_upserts += u; total_deactivated += d; total_active += a;
        }
        if target == "all" || target == "upbit" {
            let set = self.list_upbit_symbols().await.unwrap_or_default();
            let (u, d, a) = self.process_exchange("Upbit", set, "KRW").await?; total_upserts += u; total_deactivated += d; total_active += a;
        }
        if target == "all" || target == "bithumb" {
            let set = self.list_bithumb_symbols().await.unwrap_or_default();
            let (u, d, a) = self.process_exchange("Bithumb", set, "KRW").await?; total_upserts += u; total_deactivated += d; total_active += a;
        }

        Ok(serde_json::json!({
            "active_total": total_active,
            "upserts": total_upserts,
            "deactivated": total_deactivated
        }))
    }

    async fn get_exchange_id_by_name(&self, name: &str) -> Result<i32> {
        let row = sqlx::query!("SELECT id FROM exchanges WHERE name = ?", name)
            .fetch_one(&self.db)
            .await?;
        Ok(row.id)
    }

    async fn get_listed_symbols(&self, exchange_id: i32) -> Result<HashSet<String>> {
        let rows = sqlx::query!(
            r#"SELECT c.symbol FROM coin_listings cl JOIN coins c ON cl.coin_id = c.id
               WHERE cl.exchange_id = ? AND cl.is_active = TRUE"#,
            exchange_id
        )
        .fetch_all(&self.db)
        .await?;
        Ok(rows.into_iter().map(|r| r.symbol).collect())
    }

    async fn get_coin_id_for_exchange(&self, exchange_id: i32, symbol: &str) -> Result<Option<i32>> {
        let row = sqlx::query!(
            r#"SELECT cl.coin_id FROM coin_listings cl
                JOIN coins c ON cl.coin_id = c.id
              WHERE cl.exchange_id = ? AND cl.is_active = TRUE AND c.symbol = ?"#,
            exchange_id, symbol
        )
        .fetch_optional(&self.db)
        .await?;
        Ok(row.map(|r| r.coin_id))
    }

    async fn list_binance_symbols(&self) -> Result<HashSet<String>> {
        #[derive(serde::Deserialize)]
        struct ExInfo { symbols: Vec<ExSym> }
        #[derive(serde::Deserialize)]
        struct ExSym {
            status: String,
            #[serde(rename = "baseAsset")] base_asset: String,
            #[serde(rename = "quoteAsset")] quote_asset: String,
        }
        let url = "https://api.binance.com/api/v3/exchangeInfo";
        let ex: ExInfo = self.client.get(url).send().await?.json().await?;
        let set = ex.symbols.into_iter()
            .filter(|s| s.status == "TRADING" && s.quote_asset == "USDT")
            .map(|s| s.base_asset)
            .collect::<HashSet<_>>();
        Ok(set)
    }

    async fn list_upbit_symbols(&self) -> Result<HashSet<String>> {
        // Upbit markets list; filter KRW-*
        #[derive(serde::Deserialize)]
        struct UpbitMarket { market: String }
        let url = "https://api.upbit.com/v1/market/all";
        let markets: Vec<UpbitMarket> = self.client.get(url).send().await?.json().await?;
        let set = markets.into_iter()
            .filter(|m| m.market.starts_with("KRW-"))
            .map(|m| m.market.replace("KRW-", ""))
            .collect::<HashSet<_>>();
        Ok(set)
    }

    async fn list_bithumb_symbols(&self) -> Result<HashSet<String>> {
        // Bithumb ALL_KRW returns data with keys per symbol
        #[derive(serde::Deserialize)]
        struct BtResp { status: String, data: serde_json::Value }
        let url = "https://api.bithumb.com/public/ticker/ALL_KRW";
        let resp: BtResp = self.client.get(url).send().await?.json().await?;
        let mut set = HashSet::new();
        if resp.status == "0000" {
            if let serde_json::Value::Object(map) = resp.data {
                for (k, v) in map.into_iter() {
                    if k == "date" { continue; }
                    if v.is_object() { set.insert(k); }
                }
            }
        }
        Ok(set)
    }

    pub async fn fetch_all_prices(&self) -> Result<()> {
        let started = std::time::Instant::now();
        let (binance_result, upbit_result, bithumb_result) = tokio::join!(
            self.fetch_binance_prices(),
            self.fetch_upbit_prices(),
            self.fetch_bithumb_prices()
        );

        let mut binance_count = 0usize;
        let mut upbit_count = 0usize;
        let mut bithumb_count = 0usize;

        match binance_result {
            Ok(prices) => {
                binance_count = prices.len();
                if let Err(e) = self.save_prices(prices, 1).await {
                    error!("Failed to save Binance prices: {}", e);
                }
            }
            Err(e) => error!("Failed to fetch Binance prices: {}", e),
        }

        match upbit_result {
            Ok(prices) => {
                upbit_count = prices.len();
                if let Err(e) = self.save_prices(prices, 2).await {
                    error!("Failed to save Upbit prices: {}", e);
                }
            }
            Err(e) => error!("Failed to fetch Upbit prices: {}", e),
        }

        match bithumb_result {
            Ok(prices) => {
                bithumb_count = prices.len();
                if let Err(e) = self.save_prices(prices, 3).await {
                    error!("Failed to save Bithumb prices: {}", e);
                }
            }
            Err(e) => error!("Failed to fetch Bithumb prices: {}", e),
        }

        let elapsed = started.elapsed().as_millis();
        let total = binance_count + upbit_count + bithumb_count;
        info!(
            "Fetched prices: binance={}, upbit={}, bithumb={}, total={}, elapsed_ms={}",
            binance_count, upbit_count, bithumb_count, total, elapsed
        );
        Ok(())
    }

    async fn fetch_binance_prices(&self) -> Result<Vec<NewPriceData>> {
        let url = "https://api.binance.com/api/v3/ticker/24hr";
        let response: Vec<BinanceTickerResponse> = self.client
            .get(url)
            .send()
            .await?
            .json()
            .await?;

        let mut prices = Vec::new();
        let timestamp = Utc::now();
        let binance_id = self.get_exchange_id_by_name("Binance").await?;
        let listed = self.get_listed_symbols(binance_id).await?;

        for ticker in response {
            if ticker.symbol.ends_with("USDT") {
                let symbol = ticker.symbol.replace("USDT", "");
                if !listed.contains(&symbol) { continue; }
                if let Some(coin_id) = self.get_coin_id_for_exchange(binance_id, &symbol).await? {
                    let price = BigDecimal::from_str(&ticker.last_price)?;
                    let volume = BigDecimal::from_str(&ticker.volume).ok();
                    let price_change = BigDecimal::from_str(&ticker.price_change_percent).ok();

                    prices.push(NewPriceData {
                        exchange_id: 1, // TODO: Binance
                        coin_id,
                        price,
                        volume_24h: volume,
                        price_change_24h: price_change,
                        timestamp,
                    });
                }
            }
        }

        Ok(prices)
    }

    async fn fetch_upbit_prices(&self) -> Result<Vec<NewPriceData>> {
        // Fetch only symbols listed on Upbit (from coin_listings)
        let upbit_id = self.get_exchange_id_by_name("Upbit").await?;
        let symbols: Vec<String> = self.get_listed_symbols(upbit_id).await?.into_iter().collect();
        let markets: Vec<String> = symbols.iter().map(|s| format!("KRW-{}", s)).collect();
        let markets_param = markets.join(",");
        let url = format!("https://api.upbit.com/v1/ticker?markets={}", markets_param);
        
        let response: Vec<UpbitTickerResponse> = self.client
            .get(url)
            .send()
            .await?
            .json()
            .await?;

        let mut prices = Vec::new();
        let timestamp = Utc::now();

        for ticker in response {
            let symbol = ticker.market.replace("KRW-", "");
            
            if let Some(coin_id) = self.get_coin_id_for_exchange(upbit_id, &symbol).await? {
                let price = BigDecimal::from_f64(ticker.trade_price).unwrap_or_default();
                let volume = BigDecimal::from_f64(ticker.acc_trade_volume_24h).unwrap_or_default();
                let price_change = BigDecimal::from_f64(ticker.signed_change_rate * 100.0).unwrap_or_default();

                prices.push(NewPriceData {
                    exchange_id: 2, // TODO: Upbit
                    coin_id,
                    price,
                    volume_24h: Some(volume),
                    price_change_24h: Some(price_change),
                    timestamp,
                });
            }
        }

        Ok(prices)
    }

    async fn fetch_bithumb_prices(&self) -> Result<Vec<NewPriceData>> {
        // Single-shot fetch for all KRW markets to avoid per-symbol failures
        #[derive(serde::Deserialize)]
        struct BtResp { status: String, data: serde_json::Value }
        let url = "https://api.bithumb.com/public/ticker/ALL_KRW";
        let resp: BtResp = self.client.get(url).send().await?.json().await?;

        let mut out = Vec::new();
        let timestamp = Utc::now();

        if resp.status != "0000" { return Ok(out); }
        let data = match resp.data { serde_json::Value::Object(map) => map, _ => return Ok(out) };

        // Only keep listed symbols
        let bithumb_id = self.get_exchange_id_by_name("Bithumb").await?;
        let listed: HashSet<String> = self.get_listed_symbols(bithumb_id).await?;
        for (sym, v) in data.into_iter() {
            if sym == "date" { continue; }
            if let serde_json::Value::Object(obj) = v {
                // Required: closing_price; Optional: units_traded_24h, fluctate_rate_24h
                let cp_opt = obj.get("closing_price").and_then(|x| x.as_str());
                let vol_opt = obj
                    .get("units_traded_24H").and_then(|x| x.as_str())
                    .or_else(|| obj.get("units_traded_24h").and_then(|x| x.as_str()));
                let chg_opt = obj
                    .get("fluctate_rate_24H").and_then(|x| x.as_str())
                    .or_else(|| obj.get("fluctate_rate_24h").and_then(|x| x.as_str()));

                if let Some(cp) = cp_opt {
                    if !listed.contains(&sym) { continue; }
                    if let Some(coin_id) = self.get_coin_id_for_exchange(bithumb_id, &sym).await? {
                        if let Ok(price) = BigDecimal::from_str(cp) {
                            let volume = vol_opt.and_then(|s| BigDecimal::from_str(s).ok());
                            let price_change = chg_opt.and_then(|s| BigDecimal::from_str(s).ok());
                            out.push(NewPriceData {
                                exchange_id: 3,
                                coin_id,
                                price,
                                volume_24h: volume,
                                price_change_24h: price_change,
                                timestamp,
                            });
                        }
                    }
                }
            }
        }

        Ok(out)
    }


    async fn get_coin_id(&self, symbol: &str) -> Result<Option<i32>> {
        let result = sqlx::query!(
            "SELECT id FROM coins WHERE symbol = ? AND is_active = true",
            symbol
        )
        .fetch_optional(&self.db)
        .await?;

        Ok(result.map(|row| row.id))
    }

    

    async fn save_prices(&self, prices: Vec<NewPriceData>, _exchange_id: i32) -> Result<()> {
        let _price_count = prices.len();
        for price in prices {
            // sanitize volume to avoid DECIMAL overflow
            let volume_sanitized = price.volume_24h.as_ref().and_then(|v| {
                let s = v.to_string();
                // if representation is too long, likely out of DECIMAL range; drop it
                if s.len() > 30 { None } else { Some(v.clone()) }
            });
            sqlx::query!(
                r#"
                INSERT INTO price_data (exchange_id, coin_id, price, volume_24h, price_change_24h, timestamp)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    price = VALUES(price),
                    volume_24h = VALUES(volume_24h),
                    price_change_24h = VALUES(price_change_24h)
                "#,
                price.exchange_id,
                price.coin_id,
                price.price,
                volume_sanitized,
                price.price_change_24h,
                price.timestamp
            )
            .execute(&self.db)
            .await?;
        }

        Ok(())
    }


}
