use anyhow::Result;
use log::{error, info};
use std::sync::Arc;
use tokio_cron_scheduler::{Job, JobScheduler};

use crate::services::{ExchangeService, ExchangeRateService};

pub struct SchedulerService {
    exchange_service: Arc<ExchangeService>,
    exchange_rate_service: Arc<ExchangeRateService>,
    scheduler: JobScheduler,
}

impl SchedulerService {
    pub async fn new(exchange_service: Arc<ExchangeService>, exchange_rate_service: Arc<ExchangeRateService>) -> Result<Self> {
        let scheduler = JobScheduler::new().await?;
        
        Ok(Self {
            exchange_service,
            exchange_rate_service,
            scheduler,
        })
    }

    pub async fn start(&mut self) -> Result<()> {
        info!("Starting scheduler service");

        let exchange_service = Arc::clone(&self.exchange_service);
        let price_collection_job = Job::new_async("*/2 * * * * *", move |_uuid, _l| {
            let service = Arc::clone(&exchange_service);
            Box::pin(async move {
                info!("Starting scheduled price collection");
                
                match service.fetch_all_prices().await {
                    Ok(_) => info!("Successfully completed price collection"),
                    Err(e) => error!("Failed to collect prices: {}", e),
                }
            })
        })?;

        let exchange_rate_service = Arc::clone(&self.exchange_rate_service);
        let exchange_rate_job = Job::new_async("*/10 * * * * *", move |_uuid, _l| {
            let service = Arc::clone(&exchange_rate_service);
            Box::pin(async move {
                info!("Starting scheduled exchange rate update");
                
                match service.fetch_and_save_usd_krw_rate().await {
                    Ok(rate) => info!("Successfully updated exchange rate: {}", rate),
                    Err(e) => error!("Failed to update exchange rate: {}", e),
                }
            })
        })?;

        self.scheduler.add(price_collection_job).await?;
        self.scheduler.add(exchange_rate_job).await?;

        self.scheduler.start().await?;
        
        info!("Scheduler service started successfully");
        Ok(())
    }

    #[allow(dead_code)]
    pub async fn stop(&mut self) -> Result<()> {
        self.scheduler.shutdown().await?;
        info!("Scheduler service stopped");
        Ok(())
    }
}
