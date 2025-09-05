use actix_web::{HttpResponse, ResponseError};
use thiserror::Error;

#[derive(Error, Debug)]
pub enum AppError {
    #[error("Database error: {0}")]
    Database(#[from] sqlx::Error),
    
    #[error("HTTP client error: {0}")]
    Http(#[from] reqwest::Error),
    
    #[error("Serialization error: {0}")]
    Serialization(#[from] serde_json::Error),
    
    #[error("Decimal parsing error: {0}")]
    DecimalParsing(#[from] bigdecimal::ParseBigDecimalError),
    
    #[error("Internal server error: {0}")]
    Internal(String),
    
    #[error("Not found: {0}")]
    NotFound(String),
    
    #[error("Bad request: {0}")]
    BadRequest(String),
}

impl ResponseError for AppError {
    fn error_response(&self) -> HttpResponse {
        match self {
            AppError::Database(_) => {
                HttpResponse::InternalServerError().json("Database error occurred")
            }
            AppError::Http(_) => {
                HttpResponse::BadGateway().json("External service error")
            }
            AppError::Serialization(_) => {
                HttpResponse::BadRequest().json("Invalid data format")
            }
            AppError::DecimalParsing(_) => {
                HttpResponse::BadRequest().json("Invalid number format")
            }
            AppError::Internal(msg) => {
                HttpResponse::InternalServerError().json(msg)
            }
            AppError::NotFound(msg) => {
                HttpResponse::NotFound().json(msg)
            }
            AppError::BadRequest(msg) => {
                HttpResponse::BadRequest().json(msg)
            }
        }
    }
}
