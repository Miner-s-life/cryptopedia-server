use anyhow::Result;
use log::{error, info};
use std::sync::Arc;
use tokio_cron_scheduler::{Job, JobScheduler};

use crate::services::ExchangeService;

pub struct SchedulerService {
    exchange_service: Arc<ExchangeService>,
    scheduler: JobScheduler,
}

impl SchedulerService {
    pub async fn new(exchange_service: Arc<ExchangeService>) -> Result<Self> {
        let scheduler = JobScheduler::new().await?;
        
        Ok(Self {
            exchange_service,
            scheduler,
        })
    }

    pub async fn start(&mut self) -> Result<()> {
        info!("Starting scheduler service");

        let exchange_service = Arc::clone(&self.exchange_service);
        let price_collection_job = Job::new_async("0 * * * * *", move |_uuid, _l| {
            let service = Arc::clone(&exchange_service);
            Box::pin(async move {
                info!("Starting scheduled price collection");
                
                match service.fetch_all_prices().await {
                    Ok(_) => info!("Successfully completed price collection"),
                    Err(e) => error!("Failed to collect prices: {}", e),
                }
            })
        })?;

        self.scheduler.add(price_collection_job).await?;

        self.scheduler.start().await?;
        
        info!("Scheduler service started successfully");
        Ok(())
    }

    pub async fn stop(&mut self) -> Result<()> {
        info!("Stopping scheduler service");
        self.scheduler.shutdown().await?;
        info!("Scheduler service stopped");
        Ok(())
    }
}
