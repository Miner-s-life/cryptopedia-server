use anyhow::Result;
 
use std::sync::Arc;
use tokio_cron_scheduler::{Job, JobScheduler};

use crate::services::{ExchangeService, ExchangeRateService, ArbitrageService};
use crate::services::arbitrage_service::FxSource;

pub struct SchedulerService {
    exchange_service: Arc<ExchangeService>,
    exchange_rate_service: Arc<ExchangeRateService>,
    arbitrage_service: Arc<ArbitrageService>,
    scheduler: JobScheduler,
}

impl SchedulerService {
    pub async fn new(exchange_service: Arc<ExchangeService>, exchange_rate_service: Arc<ExchangeRateService>, arbitrage_service: Arc<ArbitrageService>) -> Result<Self> {
        let scheduler = JobScheduler::new().await?;
        
        Ok(Self {
            exchange_service,
            exchange_rate_service,
            arbitrage_service,
            scheduler,
        })
    }

    pub async fn start(&mut self) -> Result<()> {
        let _ = self.exchange_service.sync_coins(Some("all")).await;
        let _ = self.exchange_service.fetch_all_prices().await;
        let _ = self.exchange_rate_service.fetch_and_save_usd_krw_rate().await;

        let exchange_service = Arc::clone(&self.exchange_service);
        let price_collection_job = Job::new_async("*/2 * * * * *", move |_uuid, _l| {
            let service = Arc::clone(&exchange_service);
            Box::pin(async move {
                let _ = service.fetch_all_prices().await;
            })
        })?;

        let exchange_service_for_sync = Arc::clone(&self.exchange_service);
        let coin_sync_job = Job::new_async("0 */10 * * * *", move |_uuid, _l| {
            let service = Arc::clone(&exchange_service_for_sync);
            Box::pin(async move {
                let _ = service.sync_coins(Some("all")).await;
            })
        })?;

        let exchange_rate_service = Arc::clone(&self.exchange_rate_service);
        let exchange_rate_job = Job::new_async("*/10 * * * * *", move |_uuid, _l| {
            let service = Arc::clone(&exchange_rate_service);
            Box::pin(async move {
                let _ = service.fetch_and_save_usd_krw_rate().await;
            })
        })?;

        // Every 1 minute: record ETH kimchi snapshot (Binance -> Upbit, usdkrw)
        let arbitrage_service = Arc::clone(&self.arbitrage_service);
        let kimchi_job = Job::new_async("0 * * * * *", move |_uuid, _l| {
            let service = Arc::clone(&arbitrage_service);
            Box::pin(async move {
                let _ = service.record_kimchi_snapshot("ETH", "Binance", "Upbit", FxSource::UsdKrw).await;
            })
        })?;

        self.scheduler.add(price_collection_job).await?;
        self.scheduler.add(coin_sync_job).await?;
        self.scheduler.add(exchange_rate_job).await?;
        self.scheduler.add(kimchi_job).await?;

        self.scheduler.start().await?;
        
        Ok(())
    }

    #[allow(dead_code)]
    pub async fn stop(&mut self) -> Result<()> {
        self.scheduler.shutdown().await?;
        
        Ok(())
    }
}
