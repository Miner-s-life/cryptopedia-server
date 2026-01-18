package me.hajoo.cryptopediaserver.batch.adapter.`in`.scheduler

import me.hajoo.cryptopediaserver.batch.application.service.MarketAnalysisService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RealTimeMetricsScheduler(
    private val marketAnalysisService: MarketAnalysisService
) {

    // Run every second
    @Scheduled(fixedDelay = 1000)
    fun runRealTimeUpdate() {
        marketAnalysisService.updateRealTimeMetrics()
    }
}
