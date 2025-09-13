use actix_web::{web, HttpResponse, Result};
use log::{error, info};
use serde::Deserialize;

use crate::services::ArbitrageService;

#[derive(Deserialize)]
pub struct ArbitrageQuery {
    from: String,
    to: String,
}

fn normalize_exchange_name(name: &str) -> String {
    match name.to_lowercase().as_str() {
        "binance" => "Binance".to_string(),
        "upbit" => "Upbit".to_string(),
        "bithumb" => "Bithumb".to_string(),
        _ => name.to_string(),
    }
}

pub async fn get_directional_arbitrage(
    path: web::Path<String>,
    query: web::Query<ArbitrageQuery>,
    arbitrage_service: web::Data<ArbitrageService>,
) -> Result<HttpResponse> {
    let symbol = path.into_inner().to_uppercase();
    let from_exchange = normalize_exchange_name(&query.from);
    let to_exchange = normalize_exchange_name(&query.to);
    
    info!("Fetching arbitrage for {} from {} to {}", symbol, from_exchange, to_exchange);
    
    match arbitrage_service.get_directional_arbitrage(&symbol, &from_exchange, &to_exchange).await {
        Ok(arbitrage) => {
            info!("Directional arbitrage for {} ({} -> {}): {}% profit", 
                  symbol, from_exchange, to_exchange, arbitrage.profit_percentage);
            Ok(HttpResponse::Ok().json(arbitrage))
        }
        Err(e) => {
            error!("Failed to calculate directional arbitrage for {} ({} -> {}): {}", 
                   symbol, from_exchange, to_exchange, e);
            Ok(HttpResponse::InternalServerError().json(format!("Error: {}", e)))
        }
    }
}
