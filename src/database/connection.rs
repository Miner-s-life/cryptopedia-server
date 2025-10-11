use anyhow::Result;
use sqlx::{MySqlPool, mysql::MySqlPoolOptions};
use std::time::Duration;

pub async fn create_connection_pool(database_url: &str) -> Result<MySqlPool> {
    let pool = MySqlPoolOptions::new()
        .max_connections(10)
        .acquire_timeout(Duration::from_secs(30))
        .connect(database_url)
        .await?;

    Ok(pool)
}

