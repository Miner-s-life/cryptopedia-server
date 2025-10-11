use crate::models::exchange_rate::{NewExchangeRate, KoreaEximbankResponse};
use crate::utils::error::AppError;
use bigdecimal::BigDecimal;
use reqwest::header::{HeaderMap, HeaderValue, ACCEPT, REFERER, USER_AGENT};
use reqwest::Client;
use sqlx::MySqlPool;
use std::str::FromStr;

#[derive(Clone)]
pub struct ExchangeRateService {
    client: Client,
    pool: MySqlPool,
    auth_key: String,
}

impl ExchangeRateService {
    pub fn new(pool: MySqlPool, auth_key: String) -> Self {
        Self {
            client: Client::new(),
            pool,
            auth_key,
        }
    }

    pub async fn fetch_and_save_usd_krw_rate(&self) -> Result<BigDecimal, AppError> {
        match self.fetch_usd_krw_from_naver().await {
            Ok(rate) => {
                let exchange_rate = NewExchangeRate {
                    currency_code: "USD".to_string(),
                    rate: rate.clone(),
                    ttb_rate: None,
                    tts_rate: None,
                };
                self.save_exchange_rate(&exchange_rate).await?;
                return Ok(rate);
            }
            Err(e) => {
                log::warn!("Naver FX fetch failed, falling back to Eximbank: {}", e);
            }
        }

        let today = chrono::Utc::now().format("%Y%m%d").to_string();
        let url = format!(
            "https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON?authkey={}&searchdate={}&data=AP01",
            self.auth_key, today
        );

        let response = self
            .client
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

        let exchange_rate = usd_rate
            .to_exchange_rate()
            .map_err(|e| AppError::ExternalApiError(format!("Failed to convert exchange rate: {}", e)))?;

        self.save_exchange_rate(&exchange_rate).await?;

        Ok(exchange_rate.rate)
    }

    pub async fn get_latest_usd_krw_rate(&self) -> Result<BigDecimal, AppError> {
        let rate = sqlx::query!(
            "SELECT rate FROM exchange_rates \
             WHERE currency_code = 'USD' \
             ORDER BY created_at DESC \
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
            "INSERT INTO exchange_rates (currency_code, rate, ttb_rate, tts_rate, created_at) VALUES (?, ?, ?, ?, NOW())",
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

    async fn fetch_usd_krw_from_naver(&self) -> Result<BigDecimal, AppError> {
        let url = "https://m.search.naver.com/p/csearch/content/qapirender.nhn?key=calculator&pkid=141&q=%ED%99%98%EC%9C%A8&where=m&u1=keb&u6=standardUnit&u7=0&u3=USD&u4=KRW&u8=down&u2=1";

        let mut headers = HeaderMap::new();
        headers.insert(
            USER_AGENT,
            HeaderValue::from_static(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0 Safari/537.36",
            ),
        );
        headers.insert(REFERER, HeaderValue::from_static("https://m.search.naver.com/"));
        headers.insert(ACCEPT, HeaderValue::from_static("*/*"));

        let resp = self
            .client
            .get(url)
            .headers(headers)
            .send()
            .await
            .map_err(|e| AppError::ExternalApiError(format!("Failed to fetch Naver FX: {}", e)))?;

        let text = resp
            .text()
            .await
            .map_err(|e| AppError::ExternalApiError(format!("Failed to read Naver FX body: {}", e)))?;

        let mut best: Option<f64> = None;
        let mut cur = String::new();
        for ch in text.chars() {
            if ch.is_ascii_digit() || ch == '.' || ch == ',' {
                cur.push(ch);
            } else {
                if !cur.is_empty() {
                    let cleaned = cur.replace(",", "");
                    if let Ok(v) = cleaned.parse::<f64>() {
                        if (900.0..=2000.0).contains(&v) {
                            best = Some(best.map_or(v, |b| b.max(v)));
                        }
                    }
                    cur.clear();
                }
            }
        }
        if !cur.is_empty() {
            let cleaned = cur.replace(",", "");
            if let Ok(v) = cleaned.parse::<f64>() {
                if (900.0..=2000.0).contains(&v) {
                    best = Some(best.map_or(v, |b| b.max(v)));
                }
            }
        }

        let rate = best.ok_or_else(|| AppError::ExternalApiError("Failed to parse USD/KRW from Naver response".to_string()))?;
        Ok(BigDecimal::from_str(&format!("{:.4}", rate)).unwrap())
    }
}
