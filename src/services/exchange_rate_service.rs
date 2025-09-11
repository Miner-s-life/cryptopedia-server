use crate::models::exchange_rate::{NewExchangeRate, KoreaEximbankResponse};
use crate::utils::error::AppError;
use bigdecimal::BigDecimal;
use reqwest::Client;
use sqlx::PgPool;
use std::str::FromStr;

#[derive(Clone)]
pub struct ExchangeRateService {
    client: Client,
    pool: PgPool,
    auth_key: String,
}

impl ExchangeRateService {
    pub fn new(pool: PgPool, auth_key: String) -> Self {
        Self {
            client: Client::new(),
            pool,
            auth_key,
        }
    }

    pub async fn fetch_and_save_usd_krw_rate(&self) -> Result<BigDecimal, AppError> {
        let today = chrono::Utc::now().format("%Y%m%d").to_string();
        
        let url = format!(
            "https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON?authkey={}&searchdate={}&data=AP01",
            self.auth_key, today
        );

        let response = self.client
            .get(&url)
            .send()
            .await
            .map_err(|e| AppError::ExternalApiError(format!("Failed to fetch exchange rate: {}", e)))?;

        let rates: Vec<KoreaEximbankResponse> = response
            .json()
            .await
            .map_err(|e| AppError::ExternalApiError(format!("Failed to parse exchange rate response: {}", e)))?;

        let usd_rate = rates
            .iter()
            .find(|rate| rate.cur_unit == "USD")
            .ok_or_else(|| AppError::ExternalApiError("USD rate not found".to_string()))?;

        let exchange_rate = usd_rate.to_exchange_rate()
            .map_err(|e| AppError::ExternalApiError(format!("Failed to convert exchange rate: {}", e)))?;

        self.save_exchange_rate(&exchange_rate).await?;

        Ok(exchange_rate.rate)
    }

    pub async fn get_latest_usd_krw_rate(&self) -> Result<BigDecimal, AppError> {
        let rate = sqlx::query!(
            "SELECT rate FROM exchange_rates 
             WHERE currency_code = 'USD' 
             ORDER BY created_at DESC 
             LIMIT 1"
        )
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| AppError::DatabaseError(e.to_string()))?;

        match rate {
            Some(rate) => Ok(rate.rate),
            None => {
                self.fetch_and_save_usd_krw_rate().await
            }
        }
    }

    pub async fn save_exchange_rate(&self, new_rate: &NewExchangeRate) -> Result<(), AppError> {
        sqlx::query!(
            "INSERT INTO exchange_rates (currency_code, rate, ttb_rate, tts_rate, created_at) VALUES ($1, $2, $3, $4, NOW())",
            new_rate.currency_code,
            new_rate.rate,
            new_rate.ttb_rate,
            new_rate.tts_rate
        )
        .execute(&self.pool)
        .await
        .map_err(|e| AppError::DatabaseError(e.to_string()))?;

        Ok(())
    }

    pub fn get_fallback_usd_krw_rate() -> BigDecimal {
        BigDecimal::from_str("1300.0").unwrap() // Approximate rate as fallback
    }
}
