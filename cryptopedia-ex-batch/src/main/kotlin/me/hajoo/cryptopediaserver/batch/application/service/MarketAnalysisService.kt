package me.hajoo.cryptopediaserver.batch.application.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import me.hajoo.cryptopediaserver.core.client.binance.BinanceFuturesMarketClient
import me.hajoo.cryptopediaserver.core.domain.*
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*

@Service
class MarketAnalysisService(
    private val symbolRepository: SymbolRepository,
    private val candle1mRepository: Candle1mRepository,
    private val dailyVolumeStatsRepository: DailyVolumeStatsRepository,
    private val symbolMetricsRepository: SymbolMetricsRepository,
    private val binanceFuturesMarketClient: BinanceFuturesMarketClient,
    private val redisTemplate: RedisTemplate<String, String>,
    private val alertService: AlertService,
    private val jdbcTemplate: JdbcTemplate
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
        val today = LocalDate.now(ZoneId.of("UTC"))
        val yesterday = today.minusDays(1)
        val now = LocalDateTime.now(ZoneId.of("UTC"))
        val startOfDay = today.atStartOfDay()

        val symbols = symbolRepository.findAllByStatus("TRADING")
        
        // 0. Ensure we have data for today (Backfill if missing) - Batch optimized
        backfillTodayCandlesBulk(symbols)

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
                
                val elapsedMinutes = Duration.between(startOfDay, now).toMinutes().coerceAtLeast(1)
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
        
        redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(5))
    }

    @Transactional
    fun backfillTodayCandlesBulk(symbols: List<Symbol>) {
        val now = LocalDateTime.now(ZoneId.of("UTC"))
        val startOfDay = now.toLocalDate().atStartOfDay()
        
        // Identify symbols that need backfill
        val symbolsToBackfill = symbols.filter {
            !candle1mRepository.existsByExchangeAndSymbolAndOpenTime(it.exchange, it.symbol, startOfDay)
        }
        
        if (symbolsToBackfill.isEmpty()) return

        logger.info("Starting bulk backfill for ${symbolsToBackfill.size} symbols from $startOfDay")

        val allCandlesToSave = runBlocking {
            symbolsToBackfill.chunked(10).flatMap { chunk ->
                chunk.map { symbol ->
                    async(Dispatchers.IO) {
                        fetchTodayCandles(symbol, startOfDay)
                    }
                }.awaitAll().flatten()
            }
        }

        if (allCandlesToSave.isNotEmpty()) {
            batchInsertCandles(allCandlesToSave)
            logger.info("Inserted total ${allCandlesToSave.size} candles via global bulk insert")
        }
    }

    private fun fetchTodayCandles(symbol: Symbol, startOfDay: LocalDateTime): List<me.hajoo.cryptopediaserver.core.domain.Candle1m> {
        return try {
            when (symbol.exchange) {
                "BINANCE" -> {
                    val startTime = startOfDay.atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
                    val klines = binanceFuturesMarketClient.getKlines(symbol.symbol, "1m", startTime = startTime, limit = 1500)
                    klines.map { k ->
                        Candle1m(
                            exchange = symbol.exchange,
                            symbol = symbol.symbol,
                            openTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(k.openTime), ZoneId.of("UTC")),
                            openPrice = k.openPrice,
                            highPrice = k.highPrice,
                            lowPrice = k.lowPrice,
                            closePrice = k.closePrice,
                            volume = k.volume,
                            quoteVolume = k.quoteAssetVolume,
                            trades = k.numberOfTrades
                        )
                    }
                }
                "UPBIT" -> {
                    // ... Upbit logic ...
                    emptyList()
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch candles for ${symbol.exchange}:${symbol.symbol}", e)
            emptyList()
        }
    }

    private fun batchInsertCandles(candles: List<Candle1m>) {
        if (candles.isEmpty()) return

        val sql = """
            INSERT IGNORE INTO candles_1m (exchange, symbol, open_time, open_price, high_price, low_price, close_price, volume, quote_volume, trades)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        jdbcTemplate.batchUpdate(sql, candles, 1000) { ps, candle ->
            ps.setString(1, candle.exchange)
            ps.setString(2, candle.symbol)
            ps.setTimestamp(3, java.sql.Timestamp.valueOf(candle.openTime))
            ps.setBigDecimal(4, candle.openPrice)
            ps.setBigDecimal(5, candle.highPrice)
            ps.setBigDecimal(6, candle.lowPrice)
            ps.setBigDecimal(7, candle.closePrice)
            ps.setBigDecimal(8, candle.volume)
            ps.setBigDecimal(9, candle.quoteVolume)
            ps.setLong(10, candle.trades)
        }
    }

    @Transactional
    fun backfillHistoryBulk(symbols: List<Symbol>) {
        if (symbols.isEmpty()) return

        val today = LocalDate.now(ZoneId.of("UTC"))
        val yesterday = today.minusDays(1)

        // Only backfill for symbols that don't have yesterday's stats
        val toBackfill = symbols.filter {
            dailyVolumeStatsRepository.findByExchangeAndSymbolAndDate(it.exchange, it.symbol, yesterday) == null
        }

        if (toBackfill.isEmpty()) return

        logger.info("Starting bulk history backfill for ${toBackfill.size} symbols...")

        runBlocking {
            toBackfill.chunked(10).forEach { chunk ->
                chunk.map { symbol ->
                    async(Dispatchers.IO) {
                        try {
                            backfillHistory(symbol.exchange, symbol.symbol)
                        } catch (e: Exception) {
                            logger.error("Failed to backfill history for ${symbol.exchange}:${symbol.symbol}", e)
                        }
                    }
                }.awaitAll()
            }
        }
    }

    @Transactional
    fun backfillHistory(exchange: String, symbol: String) {
        // Double check if we already have stats to avoid spamming API on every restart
        val today = LocalDate.now(ZoneId.of("UTC"))
        val yesterday = today.minusDays(1)
        if (dailyVolumeStatsRepository.findByExchangeAndSymbolAndDate(exchange, symbol, yesterday) != null) {
            return
        }

        logger.info("Backfilling history for $exchange:$symbol...")
        
        val statsList = mutableListOf<DailyVolumeStats>()

        when (exchange) {
            "BINANCE" -> {
                val klines = binanceFuturesMarketClient.getKlines(symbol, "1d", limit = 60)
                if (klines.isEmpty()) return

                val sortedKlines = klines.sortedBy { it.openTime }
                sortedKlines.forEach { kline ->
                    val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(kline.openTime), ZoneId.of("UTC")).toLocalDate()
                    val volume = kline.volume
                    val quoteVolume = kline.quoteAssetVolume
                    
                    val maWindow7 = statsList.takeLast(6).map { it.volumeSum } + volume
                    val ma7 = maWindow7.reduce { acc, v -> acc.add(v) }.divide(BigDecimal(maWindow7.size), 8, RoundingMode.HALF_UP)

                    val maWindow30 = statsList.takeLast(29).map { it.volumeSum } + volume
                    val ma30 = maWindow30.reduce { acc, v -> acc.add(v) }.divide(BigDecimal(maWindow30.size), 8, RoundingMode.HALF_UP)

                    val stats = DailyVolumeStats(
                        exchange = exchange,
                        symbol = symbol,
                        date = date,
                        volumeSum = volume,
                        quoteVolumeSum = quoteVolume,
                        volumeMa7d = ma7,
                        volumeMa30d = ma30
                    )
                    statsList.add(stats)
                    
                    if (dailyVolumeStatsRepository.findByExchangeAndSymbolAndDate(exchange, symbol, date) == null) {
                        dailyVolumeStatsRepository.save(stats)
                    }
                }
            }
            "UPBIT" -> {
                // Handle Upbit history if needed
            }
        }
        logger.info("Backfill complete for $exchange:$symbol: ${statsList.size} days processed.")
    }
}
