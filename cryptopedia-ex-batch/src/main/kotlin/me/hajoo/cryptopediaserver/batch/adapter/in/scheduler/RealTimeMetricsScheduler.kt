package me.hajoo.cryptopediaserver.batch.adapter.`in`.scheduler

import me.hajoo.cryptopediaserver.batch.application.service.MarketAnalysisService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RealTimeMetricsScheduler(
    private val marketAnalysisService: MarketAnalysisService
) {

    // Run every minute (cron: "0 * * * * *")
    @Scheduled(cron = "0 * * * * *", zone = "UTC")
    fun runRealTimeUpdate() {
        marketAnalysisService.updateRealTimeMetrics()
    }
}
