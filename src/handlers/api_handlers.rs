use actix_web::{web, HttpResponse, Result};
use bigdecimal::BigDecimal;
use log::{error, info};
use std::str::FromStr;

use crate::services::{ArbitrageService, ExchangeService};

// 차익거래 기회 조회
pub async fn get_arbitrage_opportunities(
    arbitrage_service: web::Data<ArbitrageService>,
) -> Result<HttpResponse> {
    info!("Fetching arbitrage opportunities");
    
    match arbitrage_service.find_arbitrage_opportunities().await {
        Ok(opportunities) => {
            info!("Found {} arbitrage opportunities", opportunities.len());
            Ok(HttpResponse::Ok().json(opportunities))
        }
        Err(e) => {
            error!("Failed to fetch arbitrage opportunities: {}", e);
            Ok(HttpResponse::InternalServerError().json(format!("Error: {}", e)))
        }
    }
}

// 특정 코인의 김치 프리미엄 조회
pub async fn get_kimchi_premium(
    path: web::Path<String>,
    arbitrage_service: web::Data<ArbitrageService>,
) -> Result<HttpResponse> {
    let symbol = path.into_inner().to_uppercase();
    info!("Fetching kimchi premium for {}", symbol);
    
    match arbitrage_service.calculate_kimchi_premium(&symbol).await {
        Ok(premium) => {
            info!("Kimchi premium for {}: {}%", symbol, premium.premium_percentage);
            Ok(HttpResponse::Ok().json(premium))
        }
        Err(e) => {
            error!("Failed to calculate kimchi premium for {}: {}", symbol, e);
            Ok(HttpResponse::InternalServerError().json(format!("Error: {}", e)))
        }
    }
}

// 특정 코인의 최신 가격 조회
pub async fn get_exchange_prices(
    path: web::Path<String>,
    exchange_service: web::Data<ExchangeService>,
) -> Result<HttpResponse> {
    let symbol = path.into_inner().to_uppercase();
    info!("Fetching prices for {}", symbol);
    
    match exchange_service.get_latest_prices(&symbol).await {
        Ok(prices) => {
            info!("Found {} prices for {}", prices.len(), symbol);
            Ok(HttpResponse::Ok().json(prices))
        }
        Err(e) => {
            error!("Failed to fetch prices for {}: {}", symbol, e);
            Ok(HttpResponse::InternalServerError().json(format!("Error: {}", e)))
        }
    }
}

// 수수료 계산
pub async fn calculate_fees(
    path: web::Path<(String, String)>,
    arbitrage_service: web::Data<ArbitrageService>,
) -> Result<HttpResponse> {
    let (symbol, amount_str) = path.into_inner();
    let symbol = symbol.to_uppercase();
    
    info!("Calculating fees for {} with amount {}", symbol, amount_str);
    
    match BigDecimal::from_str(&amount_str) {
        Ok(amount) => {
            match arbitrage_service.get_fee_calculation(&symbol, amount).await {
                Ok(fees) => {
                    info!("Calculated fees for {}: total {}", symbol, fees.total_fee);
                    Ok(HttpResponse::Ok().json(fees))
                }
                Err(e) => {
                    error!("Failed to calculate fees for {}: {}", symbol, e);
                    Ok(HttpResponse::InternalServerError().json(format!("Error: {}", e)))
                }
            }
        }
        Err(e) => {
            error!("Invalid amount format: {}", e);
            Ok(HttpResponse::BadRequest().json("Invalid amount format"))
        }
    }
}

