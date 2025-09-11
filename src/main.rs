use actix_web::{web, App, HttpServer, middleware::Logger};
use actix_cors::Cors;
use anyhow::Result;
use log::info;
use std::sync::Arc;

mod config;
mod database;
mod handlers;
mod models;
mod services;
mod utils;

use config::Config;
use database::create_connection_pool;
use handlers::*;
use services::{ArbitrageService, ExchangeService, ExchangeRateService, SchedulerService};

#[actix_web::main]
async fn main() -> Result<()> {
    let env = std::env::var("ENVIRONMENT").unwrap_or_else(|_| "development".to_string());
    let env_file = format!(".env.{}", env);
    
    if std::path::Path::new(&env_file).exists() {
        dotenvy::from_filename(&env_file).ok();
    } else {
        dotenvy::dotenv().ok();
    }
    
    let config = Config::from_env().expect("Failed to load configuration");
    
    std::env::set_var("RUST_LOG", &config.rust_log);
    env_logger::init();
    
    info!("Starting crypto arbitrage backend server");
    info!("Database URL: {}", config.database_url);
    info!("Server will run on {}:{}", config.server_host, config.server_port);
    
    let pool = create_connection_pool(&config.database_url).await
        .expect("Failed to create database connection pool");
    
    info!("Database connection established");
    
    let exchange_rate_api_key = std::env::var("EXCHANGE_RATE_API_KEY")
        .unwrap_or_else(|_| "YOUR_API_KEY_HERE".to_string());
    
    let exchange_service = Arc::new(ExchangeService::new(pool.clone()));
    let exchange_rate_service = Arc::new(ExchangeRateService::new(pool.clone(), exchange_rate_api_key.clone()));
    let arbitrage_service = Arc::new(ArbitrageService::new(pool.clone(), exchange_rate_api_key));
    
    let mut scheduler = SchedulerService::new(Arc::clone(&exchange_service), Arc::clone(&exchange_rate_service)).await
        .expect("Failed to create scheduler");
    
    scheduler.start().await.expect("Failed to start scheduler");
    info!("Scheduler started successfully");
    
    let server_address = format!("{}:{}", config.server_host, config.server_port);
    info!("Starting HTTP server on {}", server_address);
    
    let exchange_service_data = web::Data::new((*exchange_service).clone());
    let exchange_rate_service_data = web::Data::new((*exchange_rate_service).clone());
    let arbitrage_service_data = web::Data::new((*arbitrage_service).clone());
    
    HttpServer::new(move || {
        let cors = Cors::default()
            .allow_any_origin()
            .allow_any_method()
            .allow_any_header()
            .max_age(3600);
            
        App::new()
            .app_data(exchange_service_data.clone())
            .app_data(exchange_rate_service_data.clone())
            .app_data(arbitrage_service_data.clone())
            .wrap(cors)
            .wrap(Logger::default())
            .service(
                web::scope("/api/v1")
                    .route("/arbitrage", web::get().to(get_arbitrage_opportunities))
                    .route("/kimchi-premium/{symbol}", web::get().to(get_kimchi_premium))
                    .route("/prices/{symbol}", web::get().to(get_exchange_prices))
                    .route("/fees/{symbol}/{amount}", web::get().to(calculate_fees))
                    .route("/exchange-rate", web::get().to(get_current_exchange_rate))
            )
    })
    .bind(&server_address)?
    .run()
    .await?;
    
    Ok(())
}
