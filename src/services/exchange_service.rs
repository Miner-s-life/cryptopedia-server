use anyhow::Result;
use bigdecimal::{BigDecimal, FromPrimitive};
use chrono::Utc;
use log::{error, info};
use reqwest::Client;
use sqlx::MySqlPool;
use std::str::FromStr;

use crate::models::{
    BinanceTickerResponse, UpbitTickerResponse, BithumbTickerResponse, NewPriceData
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

    pub async fn fetch_all_prices(&self) -> Result<()> {
        info!("Starting to fetch all exchange prices");
        
        let (binance_result, upbit_result, bithumb_result) = tokio::join!(
            self.fetch_binance_prices(),
            self.fetch_upbit_prices(),
            self.fetch_bithumb_prices()
        );

        match binance_result {
            Ok(prices) => {
                if let Err(e) = self.save_prices(prices, 1).await {
                    error!("Failed to save Binance prices: {}", e);
                }
            }
            Err(e) => error!("Failed to fetch Binance prices: {}", e),
        }

        match upbit_result {
            Ok(prices) => {
                if let Err(e) = self.save_prices(prices, 2).await {
                    error!("Failed to save Upbit prices: {}", e);
                }
            }
            Err(e) => error!("Failed to fetch Upbit prices: {}", e),
        }

        match bithumb_result {
            Ok(prices) => {
                if let Err(e) = self.save_prices(prices, 3).await {
                    error!("Failed to save Bithumb prices: {}", e);
                }
            }
            Err(e) => error!("Failed to fetch Bithumb prices: {}", e),
        }


        info!("Completed fetching all exchange prices");
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

        for ticker in response {
            if ticker.symbol.ends_with("USDT") {
                let symbol = ticker.symbol.replace("USDT", "");
                
                if let Some(coin_id) = self.get_coin_id(&symbol).await? {
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

        info!("Fetched {} Binance prices", prices.len());
        Ok(prices)
    }

    async fn fetch_upbit_prices(&self) -> Result<Vec<NewPriceData>> {
        let symbols = self.get_all_coin_symbols().await?;
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
            
            if let Some(coin_id) = self.get_coin_id(&symbol).await? {
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

        info!("Fetched {} Upbit prices", prices.len());
        Ok(prices)
    }

    async fn fetch_bithumb_prices(&self) -> Result<Vec<NewPriceData>> {
        let symbols = self.get_all_coin_symbols().await?;
        let mut prices = Vec::new();
        let timestamp = Utc::now();
        
        for symbol in &symbols {
            let url = format!("https://api.bithumb.com/public/ticker/{}_KRW", symbol);
            
            match self.client.get(&url).send().await {
                Ok(response) => {
                    match response.json::<BithumbTickerResponse>().await {
                        Ok(ticker) => {
                            if ticker.status == "0000" {
                                if let Some(coin_id) = self.get_coin_id(symbol).await? {
                                    let price = BigDecimal::from_str(&ticker.data.closing_price)?;
                                    let volume = BigDecimal::from_str(&ticker.data.units_traded_24h).ok();
                                    let price_change = BigDecimal::from_str(&ticker.data.fluctate_rate_24h).ok();

                                    prices.push(NewPriceData {
                                        exchange_id: 3, // TODO: Bithumb
                                        coin_id,
                                        price,
                                        volume_24h: volume,
                                        price_change_24h: price_change,
                                        timestamp,
                                    });
                                }
                            }
                        }
                        Err(e) => error!("Failed to parse Bithumb response for {}: {}", symbol, e),
                    }
                }
                Err(e) => error!("Failed to fetch Bithumb price for {}: {}", symbol, e),
            }
        }

        info!("Fetched {} Bithumb prices", prices.len());
        Ok(prices)
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

    async fn get_all_coin_symbols(&self) -> Result<Vec<String>> {
        let results = sqlx::query!(
            "SELECT symbol FROM coins WHERE is_active = true ORDER BY symbol"
        )
        .fetch_all(&self.db)
        .await?;

        Ok(results.into_iter().map(|row| row.symbol).collect())
    }

    async fn save_prices(&self, prices: Vec<NewPriceData>, exchange_id: i32) -> Result<()> {
        let price_count = prices.len();
        for price in prices {
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
                price.volume_24h,
                price.price_change_24h,
                price.timestamp
            )
            .execute(&self.db)
            .await?;
        }

        info!("Saved {} prices for exchange {}", price_count, exchange_id);
        Ok(())
    }


}
