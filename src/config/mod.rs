use serde::Deserialize;

#[derive(Debug, Deserialize)]
pub struct Config {
    pub database_url: String,
    pub server_host: String,
    pub server_port: u16,
    pub rust_log: String,
}

impl Config {
    pub fn from_env() -> Result<Self, std::env::VarError> {
        Ok(Config {
            database_url: std::env::var("DATABASE_URL")?,
            server_host: std::env::var("SERVER_HOST").unwrap_or_else(|_| "127.0.0.1".to_string()),
            server_port: std::env::var("SERVER_PORT")
                .unwrap_or_else(|_| "8080".to_string())
                .parse()
                .unwrap_or(8080),
            rust_log: std::env::var("RUST_LOG").unwrap_or_else(|_| "info".to_string()),
        })
    }
}
