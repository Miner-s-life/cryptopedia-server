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
use services::{ArbitrageService, ExchangeService, SchedulerService};

#[actix_web::main]
async fn main() -> Result<()> {
    dotenvy::dotenv().ok();
    
    let config = Config::from_env().expect("Failed to load configuration");
    
    std::env::set_var("RUST_LOG", &config.rust_log);
    env_logger::init();
    
    info!("Starting crypto arbitrage backend server");
    info!("Database URL: {}", config.database_url);
    info!("Server will run on {}:{}", config.server_host, config.server_port);
    
    let pool = create_connection_pool(&config.database_url).await
        .expect("Failed to create database connection pool");
    
    info!("Database connection established");
    
    let exchange_service = Arc::new(ExchangeService::new(pool.clone()));
    let arbitrage_service = Arc::new(ArbitrageService::new(pool.clone()));
    
    let mut scheduler = SchedulerService::new(Arc::clone(&exchange_service)).await
        .expect("Failed to create scheduler");
    
    scheduler.start().await.expect("Failed to start scheduler");
    info!("Scheduler started successfully");
    
    let server_address = format!("{}:{}", config.server_host, config.server_port);
    info!("Starting HTTP server on {}", server_address);
    
    let exchange_service_data = web::Data::new((*exchange_service).clone());
    let arbitrage_service_data = web::Data::new((*arbitrage_service).clone());
    
    HttpServer::new(move || {
        let cors = Cors::default()
            .allow_any_origin()
            .allow_any_method()
            .allow_any_header()
            .max_age(3600);
            
        App::new()
            .app_data(exchange_service_data.clone())
            .app_data(arbitrage_service_data.clone())
            .wrap(cors)
            .wrap(Logger::default())
            .service(
                web::scope("/api/v1")
                    .route("/arbitrage", web::get().to(get_arbitrage_opportunities))
                    .route("/kimchi-premium/{symbol}", web::get().to(get_kimchi_premium))
                    .route("/prices/{symbol}", web::get().to(get_exchange_prices))
                    .route("/fees/{symbol}/{amount}", web::get().to(calculate_fees))
            )
    })
    .bind(&server_address)?
    .run()
    .await?;
    
    Ok(())
}
