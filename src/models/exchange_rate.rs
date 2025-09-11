use serde::{Deserialize, Serialize};
use bigdecimal::BigDecimal;
use chrono::{DateTime, Utc};
use std::str::FromStr;

#[derive(Debug, Serialize, Deserialize)]
pub struct ExchangeRate {
    pub id: i32,
    pub currency_code: String,
    pub rate: BigDecimal,
    pub ttb_rate: Option<BigDecimal>,
    pub tts_rate: Option<BigDecimal>,
    pub created_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct NewExchangeRate {
    pub currency_code: String,
    pub rate: BigDecimal,
    pub ttb_rate: Option<BigDecimal>,
    pub tts_rate: Option<BigDecimal>,
}

#[derive(Debug, Deserialize)]
#[allow(dead_code)]
pub struct KoreaEximbankResponse {
    pub result: i32,
    pub cur_unit: String,
    pub ttb: String,
    pub tts: String,
    pub deal_bas_r: String,
    pub bkpr: String,
    pub yy_efee_r: String,
    pub ten_dd_efee_r: String,
    pub kftc_bkpr: String,
    pub kftc_deal_bas_r: String,
    pub cur_nm: String,
}

impl KoreaEximbankResponse {
    pub fn to_exchange_rate(&self) -> Result<NewExchangeRate, Box<dyn std::error::Error>> {
        let rate = BigDecimal::from_str(&self.deal_bas_r.replace(",", ""))?;
        let ttb_rate = if !self.ttb.is_empty() {
            Some(BigDecimal::from_str(&self.ttb.replace(",", ""))?)
        } else {
            None
        };
        let tts_rate = if !self.tts.is_empty() {
            Some(BigDecimal::from_str(&self.tts.replace(",", ""))?)
        } else {
            None
        };
        
        Ok(NewExchangeRate {
            currency_code: self.cur_unit.clone(),
            rate,
            ttb_rate,
            tts_rate,
        })
    }
}
