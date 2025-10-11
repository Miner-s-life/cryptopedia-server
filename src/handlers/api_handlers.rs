use actix_web::{web, HttpResponse, Result};
use log::{error, info};
use serde::Deserialize;

use crate::services::ArbitrageService;

#[derive(Deserialize)]
pub struct ArbitrageQuery {
    from: String,
    to: String,
    // optional controls
    fx: Option<String>,     // usdkrw | usdtkrw
    fees: Option<String>,   // include | exclude
}

#[derive(Deserialize)]
pub struct ArbitrageListQuery {
    from: String,
    to: String,
    // optional controls
    fx: Option<String>,     // usdkrw | usdtkrw (default: usdkrw)
    fees: Option<String>,   // include | exclude (default: exclude)
    limit: Option<i64>,
}

pub async fn get_arbitrage_list(
    query: web::Query<ArbitrageListQuery>,
    arbitrage_service: web::Data<ArbitrageService>,
) -> Result<HttpResponse> {
    let from_exchange = normalize_exchange_name(&query.from);
    let to_exchange = normalize_exchange_name(&query.to);
    let fx_source = match query.fx.as_deref().unwrap_or("usdkrw").to_lowercase().as_str() {
        "usdtkrw" => crate::services::arbitrage_service::FxSource::UsdtKrw,
        _ => crate::services::arbitrage_service::FxSource::UsdKrw,
    };
    let include_fees = matches!(query.fees.as_deref(), Some("include"));

    info!(
        "Fetching arbitrage list for {} -> {} (fx={}, fees={}, limit={:?})",
        from_exchange,
        to_exchange,
        if matches!(fx_source, crate::services::arbitrage_service::FxSource::UsdKrw) { "usdkrw" } else { "usdtkrw" },
        if include_fees { "include" } else { "exclude" },
        query.limit
    );

    match arbitrage_service
        .get_directional_arbitrage_list(&from_exchange, &to_exchange, fx_source, include_fees, query.limit)
        .await
    {
        Ok(list) => Ok(HttpResponse::Ok().json(list)),
        Err(e) => {
            error!(
                "Failed to get arbitrage list ({} -> {}): {}",
                from_exchange, to_exchange, e
            );
            Ok(HttpResponse::InternalServerError().json(format!("Error: {}", e)))
        }
    }
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
    let fx_source = match query.fx.as_deref().unwrap_or("usdtkrw").to_lowercase().as_str() {
        "usdkrw" => crate::services::arbitrage_service::FxSource::UsdKrw,
        _ => crate::services::arbitrage_service::FxSource::UsdtKrw,
    };
    let include_fees = match query.fees.as_deref().unwrap_or("include").to_lowercase().as_str() {
        "exclude" => false,
        _ => true,
    };
    
    info!("Fetching arbitrage for {} from {} to {}", symbol, from_exchange, to_exchange);
    
    match arbitrage_service.get_directional_arbitrage_with_options(
        &symbol,
        &from_exchange,
        &to_exchange,
        fx_source,
        include_fees,
    ).await {
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
