package me.hajoo.cryptopediaserver.batch.adapter.`in`.scheduler

import me.hajoo.cryptopediaserver.batch.application.service.MarketAnalysisService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

@Component
class DailyMetricsScheduler(
    private val marketAnalysisService: MarketAnalysisService
) {

    // Runs every day at 00:10 UTC (Give 10m buffer for candles to settle)
    @Scheduled(cron = "0 10 0 * * *", zone = "UTC")
    fun runDailyVolumeAggregation() {
        val yesterday = LocalDate.now(ZoneId.of("UTC")).minusDays(1)
        marketAnalysisService.aggregateDailyStats(yesterday)
    }
}
