use anyhow::Result;
use bigdecimal::{BigDecimal, FromPrimitive};
use chrono::Utc;
use log::{error, info};
use reqwest::Client;
use sqlx::PgPool;
use std::str::FromStr;

use crate::models::{
    BinanceTickerResponse, BithumbTickerResponse, UpbitTickerResponse, 
    NewPriceData, NewExchangeRate
};

#[derive(Clone)]
pub struct ExchangeService {
    client: Client,
    db: PgPool,
}

impl ExchangeService {
    pub fn new(db: PgPool) -> Self {
        Self {
            client: Client::new(),
            db,
        }
    }

    pub async fn fetch_all_prices(&self) -> Result<()> {
        info!("Starting to fetch all exchange prices");
        
        // 병렬로 모든 거래소 데이터 수집
        let (binance_result, upbit_result, bithumb_result, exchange_rate_result) = tokio::join!(
            self.fetch_binance_prices(),
            self.fetch_upbit_prices(),
            self.fetch_bithumb_prices(),
            self.fetch_exchange_rate()
        );

        // 바이낸스 결과 처리
        match binance_result {
            Ok(prices) => {
                if let Err(e) = self.save_prices(prices, 1).await {
                    error!("Failed to save Binance prices: {}", e);
                }
            }
            Err(e) => error!("Failed to fetch Binance prices: {}", e),
        }

        // 업비트 결과 처리
        match upbit_result {
            Ok(prices) => {
                if let Err(e) = self.save_prices(prices, 2).await {
                    error!("Failed to save Upbit prices: {}", e);
                }
            }
            Err(e) => error!("Failed to fetch Upbit prices: {}", e),
        }

        // 빗썸 결과 처리
        match bithumb_result {
            Ok(prices) => {
                if let Err(e) = self.save_prices(prices, 3).await {
                    error!("Failed to save Bithumb prices: {}", e);
                }
            }
            Err(e) => error!("Failed to fetch Bithumb prices: {}", e),
        }

        // 환율 정보 저장
        if let Ok(rate) = exchange_rate_result {
            if let Err(e) = self.save_exchange_rate(rate).await {
                error!("Failed to save exchange rate: {}", e);
            }
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
                        exchange_id: 1, // Binance
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
        let url = "https://api.upbit.com/v1/ticker?markets=KRW-BTC,KRW-ETH,KRW-XRP,KRW-ADA,KRW-DOT";
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
                    exchange_id: 2, // Upbit
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
        let symbols = ["BTC", "ETH", "XRP", "ADA", "DOT"];
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
                                        exchange_id: 3, // Bithumb
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

    async fn fetch_exchange_rate(&self) -> Result<NewExchangeRate> {
        // 한국은행 API 또는 다른 환율 API 사용
        // 여기서는 임시로 고정값 사용
        let rate = BigDecimal::from_str("1340.0")?; // USD/KRW
        
        Ok(NewExchangeRate {
            from_currency: "USD".to_string(),
            to_currency: "KRW".to_string(),
            rate,
            timestamp: Utc::now(),
        })
    }

    async fn get_coin_id(&self, symbol: &str) -> Result<Option<i32>> {
        let result = sqlx::query_scalar!(
            "SELECT id FROM coins WHERE symbol = $1 AND is_active = true",
            symbol
        )
        .fetch_optional(&self.db)
        .await?;

        Ok(result)
    }

    async fn save_prices(&self, prices: Vec<NewPriceData>, exchange_id: i32) -> Result<()> {
        let price_count = prices.len();
        for price in prices {
            sqlx::query!(
                r#"
                INSERT INTO price_data (exchange_id, coin_id, price, volume_24h, price_change_24h, timestamp)
                VALUES ($1, $2, $3, $4, $5, $6)
                ON CONFLICT (exchange_id, coin_id, timestamp) DO UPDATE SET
                    price = EXCLUDED.price,
                    volume_24h = EXCLUDED.volume_24h,
                    price_change_24h = EXCLUDED.price_change_24h
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

    async fn save_exchange_rate(&self, rate: NewExchangeRate) -> Result<()> {
        sqlx::query!(
            r#"
            INSERT INTO exchange_rates (from_currency, to_currency, rate, timestamp)
            VALUES ($1, $2, $3, $4)
            ON CONFLICT (from_currency, to_currency, timestamp) DO UPDATE SET
                rate = EXCLUDED.rate
            "#,
            rate.from_currency,
            rate.to_currency,
            rate.rate,
            rate.timestamp
        )
        .execute(&self.db)
        .await?;

        info!("Saved exchange rate: {} {} = {}", rate.rate, rate.from_currency, rate.to_currency);
        Ok(())
    }

    pub async fn get_latest_prices(&self, symbol: &str) -> Result<Vec<(String, BigDecimal)>> {
        let coin_id = self.get_coin_id(symbol).await?;
        
        if let Some(coin_id) = coin_id {
            let results = sqlx::query!(
                r#"
                SELECT e.name, pd.price
                FROM price_data pd
                JOIN exchanges e ON pd.exchange_id = e.id
                WHERE pd.coin_id = $1
                AND pd.timestamp >= NOW() - INTERVAL '1 hour'
                ORDER BY pd.timestamp DESC
                LIMIT 3
                "#,
                coin_id
            )
            .fetch_all(&self.db)
            .await?;

            let prices: Vec<(String, BigDecimal)> = results
                .into_iter()
                .map(|row| (row.name, row.price))
                .collect();

            Ok(prices)
        } else {
            Ok(Vec::new())
        }
    }
}
