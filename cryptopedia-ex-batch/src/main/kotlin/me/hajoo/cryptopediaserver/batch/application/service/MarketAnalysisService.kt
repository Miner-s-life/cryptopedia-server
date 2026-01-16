package me.hajoo.cryptopediaserver.batch.application.service

import me.hajoo.cryptopediaserver.batch.adapter.out.binance.BinanceRestClient
import me.hajoo.cryptopediaserver.core.client.binance.BinanceFuturesMarketClient
import me.hajoo.cryptopediaserver.core.domain.Candle1mRepository
import me.hajoo.cryptopediaserver.core.domain.DailyVolumeStats
import me.hajoo.cryptopediaserver.core.domain.DailyVolumeStatsRepository
import me.hajoo.cryptopediaserver.core.domain.SymbolMetrics
import me.hajoo.cryptopediaserver.core.domain.SymbolMetricsRepository
import me.hajoo.cryptopediaserver.core.domain.SymbolRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class MarketAnalysisService(
    private val symbolRepository: SymbolRepository,
    private val candle1mRepository: Candle1mRepository,
    private val dailyVolumeStatsRepository: DailyVolumeStatsRepository,
    private val symbolMetricsRepository: SymbolMetricsRepository,
    private val binanceFuturesMarketClient: BinanceFuturesMarketClient,
    private val redisTemplate: RedisTemplate<String, String>,
    private val alertService: AlertService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun aggregateDailyStats(targetDate: LocalDate) {
        logger.info("Starting daily aggregation for date: $targetDate")
        
        val symbols = symbolRepository.findAllByStatus("TRADING")
        val startOfDay = targetDate.atStartOfDay() // 00:00:00
        val endOfDay = targetDate.plusDays(1).atStartOfDay() // Next day 00:00:00

        symbols.forEach { symbol ->
            try {
                // 1. Calculate Volume Sum for targetDate
                val volumeSum = candle1mRepository.getVolumeSum(
                    symbol.exchange, 
                    symbol.symbol, 
                    startOfDay, 
                    endOfDay
                ) ?: BigDecimal.ZERO

                // 2. Save or Update DailyStats
                val existing = dailyVolumeStatsRepository.findByExchangeAndSymbolAndDate(symbol.exchange, symbol.symbol, targetDate)
                val savedStats = if (existing != null) {
                    // Normally shouldn't happen for past dates unless re-running
                    dailyVolumeStatsRepository.save(
                        DailyVolumeStats(
                            id = existing.id,
                            exchange = symbol.exchange,
                            symbol = symbol.symbol,
                            date = targetDate,
                            volumeSum = volumeSum,
                            quoteVolumeSum = BigDecimal.ZERO // TODO: Add quote volume support later if needed
                        )
                    )
                } else {
                    dailyVolumeStatsRepository.save(
                        DailyVolumeStats(
                            exchange = symbol.exchange,
                            symbol = symbol.symbol,
                            date = targetDate,
                            volumeSum = volumeSum,
                            quoteVolumeSum = BigDecimal.ZERO
                        )
                    )
                }

                // 3. Calculate Moving Averages (using data BEFORE targetDate, or including? Usually MA includes current if closed)
                // Since this job runs AFTER the day is closed, we include targetDate's stats.
                // We fetch previous stats (excluding current processed one to start fresh list logic, or just fetch all including current)
                
                // Fetch last 29 days + current day = 30 days
                val history = dailyVolumeStatsRepository.findTop30ByExchangeAndSymbolAndDateLessThanOrderByDateDesc(
                    symbol.exchange, 
                    symbol.symbol, 
                    targetDate.plusDays(1) // Get everything before tomorrow
                ) // This should include the 'savedStats' we just saved

                val safeHistory = if (history.none { it.date == targetDate }) {
                     // If repository cache or transaction isolation hides it, add manually. 
                     // But save() should flush.
                     listOf(savedStats) + history
                } else {
                    history
                }

                if (safeHistory.isEmpty()) return@forEach

                // MA 7
                val last7 = safeHistory.take(7)
                val ma7 = if (last7.isNotEmpty()) {
                    last7.map { it.volumeSum }.reduce { acc, v -> acc.add(v) }
                        .divide(BigDecimal(last7.size), 8, RoundingMode.HALF_UP)
                } else null

                // MA 30
                val last30 = safeHistory.take(30)
                val ma30 = if (last30.isNotEmpty()) {
                    last30.map { it.volumeSum }.reduce { acc, v -> acc.add(v) }
                        .divide(BigDecimal(last30.size), 8, RoundingMode.HALF_UP)
                } else null

                // Update with MAs
                dailyVolumeStatsRepository.save(
                    DailyVolumeStats(
                        id = savedStats.id,
                        exchange = savedStats.exchange,
                        symbol = savedStats.symbol,
                        date = savedStats.date,
                        volumeSum = savedStats.volumeSum,
                        quoteVolumeSum = savedStats.quoteVolumeSum,
                        volumeMa7d = ma7,
                        volumeMa30d = ma30
                    )
                )

            } catch (e: Exception) {
                logger.error("Failed to aggregate for ${symbol.symbol}", e)
            }
        }
        logger.info("Daily aggregation finished.")
    }

    @Transactional
    fun updateRealTimeMetrics() {
        val today = LocalDate.now(java.time.ZoneId.of("UTC"))
        val yesterday = today.minusDays(1)
        val now = LocalDateTime.now(java.time.ZoneId.of("UTC"))
        val startOfDay = today.atStartOfDay()

        val symbols = symbolRepository.findAllByStatus("TRADING")

        symbols.forEach { symbol ->
            try {
                // 1. Get Baseline (Yesterday's Stats with MA)
                val stats = dailyVolumeStatsRepository.findByExchangeAndSymbolAndDate(symbol.exchange, symbol.symbol, yesterday)
                
                // If no stats (new symbol?), skip or use fallback
                if (stats == null || stats.volumeMa7d == null || stats.volumeMa30d == null) return@forEach

                // 2. Get Today's Accumulated Volume
                val currentVol = candle1mRepository.getVolumeSum(
                    symbol.exchange, 
                    symbol.symbol, 
                    startOfDay, 
                    now
                ) ?: BigDecimal.ZERO

                // 3. Calculate RVOL (Relative Volume)
                // Logic: Compare currentVol against (MA * TimeProgress%) ?? 
                // Simple version: RVOL = CurrentVol / MA_30D * (1440 / CurrentMinuteOfDay)? NO.
                // Better version for crypto: Compare against "Same time window avg" (Hard to do without hourly stats)
                // Quick proxy: 
                // ExpectedVol = MA_30D * (ElapsedMinutes / 1440)
                // RVOL = CurrentVol / ExpectedVol
                
                val elapsedMinutes = java.time.Duration.between(startOfDay, now).toMinutes().coerceAtLeast(1)
                val dailyMa = stats.volumeMa30d!!
                
                // Avoid division by zero
                if (dailyMa.compareTo(BigDecimal.ZERO) == 0) return@forEach

                val expectedVol = dailyMa.multiply(BigDecimal(elapsedMinutes)).divide(BigDecimal(1440), 8, RoundingMode.HALF_UP)
                
                val rvol = if (expectedVol.compareTo(BigDecimal.ZERO) > 0) {
                    currentVol.divide(expectedVol, 4, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                // 4. Update Metrics Table
                val metrics = symbolMetricsRepository.findByExchangeAndSymbol(symbol.exchange, symbol.symbol)
                    ?: SymbolMetrics(
                        exchange = symbol.exchange,
                        symbol = symbol.symbol,
                        rvol = BigDecimal.ZERO,
                        priceChangePercent24h = BigDecimal.ZERO
                    )

                metrics.apply {
                    this.rvol = rvol
                    this.isSurging = rvol > BigDecimal("1.5") // Threshold: 150% of expected
                    this.lastUpdated = now
                    
                    // TODO: Get Price Change from Ticker table if needed, or leave it for Ticker ingestion update
                    // For now let's just update RVOL
                }
                symbolMetricsRepository.save(metrics)
                
                // 5. Cache to Redis
                cacheMetricsToRedis(metrics)
                
                // 6. Alerting
                if (metrics.isSurging) {
                    alertService.sendSurgeAlert(metrics)
                }

            } catch (e: Exception) {
                logger.error("Real-time metrics error for ${symbol.symbol}", e)
            }
        }
    }

    private fun cacheMetricsToRedis(metrics: SymbolMetrics) {
        val key = "metrics:${metrics.exchange}:${metrics.symbol}"
        val value = """
            {
                "exchange": "${metrics.exchange}",
                "symbol": "${metrics.symbol}",
                "rvol": ${metrics.rvol},
                "priceChangePercent24h": ${metrics.priceChangePercent24h},
                "isSurging": ${metrics.isSurging},
                "lastUpdated": "${metrics.lastUpdated}"
            }
        """.trimIndent()
        
        redisTemplate.opsForValue().set(key, value, java.time.Duration.ofMinutes(5))
    }

    @Transactional
    fun backfillHistory(symbol: String) {
        // Double check if we already have stats to avoid spamming API on every restart
        val today = LocalDate.now(java.time.ZoneId.of("UTC"))
        val yesterday = today.minusDays(1)
        if (dailyVolumeStatsRepository.findByExchangeAndSymbolAndDate("BINANCE", symbol, yesterday) != null) {
            return
        }

        logger.info("Backfilling history for $symbol...")
        val klines = binanceFuturesMarketClient.getKlines(symbol, "1d", limit = 60) // Fetch 60 days to have enough for MA30
        if (klines.isEmpty()) return

        // Sort by date ascending to calculate MAs properly
        val sortedKlines = klines.sortedBy { it.openTime }
        
        // In-memory list to help calc MAs
        val statsList = mutableListOf<DailyVolumeStats>()

        sortedKlines.forEach { kline ->
            val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(kline.openTime), java.time.ZoneId.of("UTC")).toLocalDate()
            val volume = kline.volume
            val quoteVolume = kline.quoteAssetVolume
            
            // Calculate MA based on previous stats in statsList
            // MA7
            val last7 = statsList.takeLast(7)
            val ma7 = if (last7.isNotEmpty()) {
                val sum = last7.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.volumeSum) }
                // Include current? Usually MA is based on closed candles. 
                // Since 'kline' is a finished daily candle (mostly), we can include it or just strictly use past N.
                // Standard MA: Average of last N candles (including current if it's the reference point? No, usually Close Price MA includes current close. Volume MA includes current volume)
                // Let's include current volume in the window if we treat this data point as "closed day".
                
                val buffer = last7.map { it.volumeSum } + volume
                val window = buffer.takeLast(7)
                window.reduce { acc, v -> acc.add(v) }.divide(BigDecimal(window.size), 8, RoundingMode.HALF_UP)
            } else volume // Fallback?

            // MA30
            val last30 = statsList.takeLast(30)
            val ma30 = if (last30.isNotEmpty()) {
                val buffer = last30.map { it.volumeSum } + volume
                val window = buffer.takeLast(30)
                window.reduce { acc, v -> acc.add(v) }.divide(BigDecimal(window.size), 8, RoundingMode.HALF_UP)
            } else volume

            val stats = DailyVolumeStats(
                exchange = "BINANCE",
                symbol = symbol,
                date = date,
                volumeSum = volume,
                quoteVolumeSum = quoteVolume,
                volumeMa7d = ma7,
                volumeMa30d = ma30
            )
            
            // Save to list and DB
            statsList.add(stats)
            
            // Upsert to DB
            val existing = dailyVolumeStatsRepository.findByExchangeAndSymbolAndDate("BINANCE", symbol, date)
            if (existing != null) {
                // Skip or update? Skip for backfill speed usually
            } else {
                dailyVolumeStatsRepository.save(stats)
            }
        }
        logger.info("Backfill complete for $symbol: ${statsList.size} days processed.")
    }
}
