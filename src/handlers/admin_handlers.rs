use actix_web::{web, HttpResponse, Result};
use serde::Deserialize;
use log::{info, error};

use crate::services::ExchangeService;

#[derive(Deserialize)]
pub struct SyncCoinsQuery {
    pub exchange: Option<String>, // all | upbit | bithumb | binance
}

pub async fn sync_coins(
    query: web::Query<SyncCoinsQuery>,
    exchange_service: web::Data<ExchangeService>,
) -> Result<HttpResponse> {
    let ex = query.exchange.as_deref();
    info!("Sync coins request: {:?}", ex);
    match exchange_service.sync_coins(ex).await {
        Ok(summary) => Ok(HttpResponse::Ok().json(summary)),
        Err(e) => {
            error!("Sync coins failed: {}", e);
            Ok(HttpResponse::InternalServerError().json(format!("Error: {}", e)))
        }
    }
}

pub async fn ingest_now(
    exchange_service: web::Data<ExchangeService>,
) -> Result<HttpResponse> {
    let started = std::time::Instant::now();
    match exchange_service.fetch_all_prices().await {
        Ok(_) => {
            let ms = started.elapsed().as_millis();
            Ok(HttpResponse::Ok().json(serde_json::json!({"ok": true, "elapsed_ms": ms})))
        }
        Err(e) => {
            error!("Ingest-now failed: {}", e);
            Ok(HttpResponse::InternalServerError().json(format!("Error: {}", e)))
        }
    }
}
