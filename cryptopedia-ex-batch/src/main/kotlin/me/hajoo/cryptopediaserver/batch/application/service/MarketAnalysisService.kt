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
    fun backfillMissingCandles(symbols: List<Symbol>) {
        val now = LocalDateTime.now(ZoneId.of("UTC"))
        
        symbols.forEach { symbol ->
            try {
                val latestTime = candle1mRepository.findLatestOpenTime(symbol.exchange, symbol.symbol)
                val startTime = latestTime?.plusMinutes(1) ?: now.minusHours(1)
                
                if (Duration.between(startTime, now).toMinutes() > 1) {
                    logger.info("Backfilling missing candles for ${symbol.symbol} from $startTime to $now")
                    val missingCandles = fetchCandlesInRange(symbol, startTime, now)
                    if (missingCandles.isNotEmpty()) {
                        batchInsertCandles(missingCandles)
                        logger.info("Restored ${missingCandles.size} missing candles for ${symbol.symbol}")
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to backfill for ${symbol.symbol}", e)
            }
        }
    }

    private fun fetchCandlesInRange(symbol: Symbol, start: LocalDateTime, end: LocalDateTime): List<Candle1m> {
        val startTime = start.atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
        return try {
            when (symbol.exchange) {
                "BINANCE" -> {
                    binanceFuturesMarketClient.getKlines(symbol.symbol, "1m", startTime = startTime, limit = 1500)
                        .map { k ->
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
                else -> emptyList()
            }
        } catch (e: Exception) {
            logger.error("Error fetching candles for backfill: ${symbol.symbol}", e)
            emptyList()
        }
    }

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

        // 1. Fetch all 24h tickers once to share across symbols
        val allTickers = try {
            binanceFuturesMarketClient.getAll24hTickers().associateBy { it.symbol }
        } catch (e: Exception) {
            logger.error("Failed to fetch 24h tickers for metrics update", e)
            emptyMap()
        }

        symbols.forEach { symbol ->
            try {
                // 1. Get Baseline (Yesterday's Stats with MA)
                val stats = dailyVolumeStatsRepository.findByExchangeAndSymbolAndDate(symbol.exchange, symbol.symbol, yesterday)
                
                // If no stats (new symbol?), skip or use fallback
                if (stats == null || stats.volumeMa7d == null || stats.volumeMa30d == null) return@forEach

                // 2. Calculate Multi-Timeframe RVOL
                val dailyMa = stats.volumeMa30d!!
                if (dailyMa.compareTo(BigDecimal.ZERO) == 0) return@forEach

                // 2-1. Today's RVOL (existing logic)
                val currentVol = candle1mRepository.getVolumeSum(
                    symbol.exchange, symbol.symbol, startOfDay, now
                ) ?: BigDecimal.ZERO
                val elapsedMinutes = Duration.between(startOfDay, now).toMinutes().coerceAtLeast(1)
                val expectedVol = dailyMa.multiply(BigDecimal(elapsedMinutes)).divide(BigDecimal(1440), 8, RoundingMode.HALF_UP)
                val rvolToday = if (expectedVol > BigDecimal.ZERO) {
                    currentVol.divide(expectedVol, 4, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                // 2-2. 1-minute RVOL (ultra-fast detection)
                // Use the most recent completed candle instead of the currently forming one
                val candleEnd = now.withSecond(0).withNano(0)
                val candleStart = candleEnd.minusMinutes(1)
                
                val vol1m = candle1mRepository.getVolumeSum(
                    symbol.exchange, symbol.symbol, candleStart, candleEnd
                ) ?: BigDecimal.ZERO
                val expected1m = dailyMa.multiply(BigDecimal(1)).divide(BigDecimal(1440), 8, RoundingMode.HALF_UP)
                val rvol1m = if (expected1m > BigDecimal.ZERO) {
                    vol1m.divide(expected1m, 4, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                // 2-3. 5-minute RVOL
                val vol5m = candle1mRepository.getVolumeSum(
                    symbol.exchange, symbol.symbol, candleEnd.minusMinutes(5), candleEnd
                ) ?: BigDecimal.ZERO
                val expected5m = dailyMa.multiply(BigDecimal(5)).divide(BigDecimal(1440), 8, RoundingMode.HALF_UP)
                val rvol5m = if (expected5m > BigDecimal.ZERO) {
                    vol5m.divide(expected5m, 4, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                // 2-3. 15-minute RVOL
                val vol15m = candle1mRepository.getVolumeSum(
                    symbol.exchange, symbol.symbol, candleEnd.minusMinutes(15), candleEnd
                ) ?: BigDecimal.ZERO
                val expected15m = dailyMa.multiply(BigDecimal(15)).divide(BigDecimal(1440), 8, RoundingMode.HALF_UP)
                val rvol15m = if (expected15m > BigDecimal.ZERO) {
                    vol15m.divide(expected15m, 4, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                // 2-4. 30-minute RVOL
                val vol30m = candle1mRepository.getVolumeSum(
                    symbol.exchange, symbol.symbol, candleEnd.minusMinutes(30), candleEnd
                ) ?: BigDecimal.ZERO
                val expected30m = dailyMa.multiply(BigDecimal(30)).divide(BigDecimal(1440), 8, RoundingMode.HALF_UP)
                val rvol30m = if (expected30m > BigDecimal.ZERO) {
                    vol30m.divide(expected30m, 4, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                // 2-5. 1-hour RVOL
                val vol1h = candle1mRepository.getVolumeSum(
                    symbol.exchange, symbol.symbol, candleEnd.minusHours(1), candleEnd
                ) ?: BigDecimal.ZERO
                val expected1h = dailyMa.multiply(BigDecimal(60)).divide(BigDecimal(1440), 8, RoundingMode.HALF_UP)
                val rvol1h = if (expected1h > BigDecimal.ZERO) {
                    vol1h.divide(expected1h, 4, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                // 2-6. 4-hour RVOL
                val vol4h = candle1mRepository.getVolumeSum(
                    symbol.exchange, symbol.symbol, candleEnd.minusHours(4), candleEnd
                ) ?: BigDecimal.ZERO
                val expected4h = dailyMa.multiply(BigDecimal(240)).divide(BigDecimal(1440), 8, RoundingMode.HALF_UP)
                val rvol4h = if (expected4h > BigDecimal.ZERO) {
                    vol4h.divide(expected4h, 4, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO

                // 3. Update Metrics Table
                val metrics = symbolMetricsRepository.findByExchangeAndSymbol(symbol.exchange, symbol.symbol)
                    ?: SymbolMetrics(
                        exchange = symbol.exchange,
                        symbol = symbol.symbol
                    )
                
                metrics.apply {
                    // Update all RVOL values
                    this.rvol1m = rvol1m
                    this.rvol5m = rvol5m
                    this.rvol15m = rvol15m
                    this.rvol30m = rvol30m
                    this.rvol1h = rvol1h
                    this.rvol4h = rvol4h
                    this.rvolToday = rvolToday
                    
                    // Multi-timeframe surge detection (1m + 5m + 15m for strong confirmation)
                    this.isSurging = rvol1m > BigDecimal("8.0") && rvol5m > BigDecimal("4.0") && rvol15m > BigDecimal("3.0")
                    this.lastUpdated = now

                    // Update Price Change from Ticker data
                    val ticker = allTickers[symbol.symbol]
                    ticker?.priceChangePercent?.let {
                        this.priceChangePercent24h = it
                    }

                    // Calculate Today's Price Change (since UTC 00:00)
                    val firstCandle = candle1mRepository.findFirstByExchangeAndSymbolAndOpenTimeGreaterThanEqualOrderByOpenTimeAsc(
                        symbol.exchange, symbol.symbol, startOfDay
                    )
                    if (firstCandle != null && ticker?.lastPrice != null) {
                        val openPrice = firstCandle.openPrice
                        if (openPrice > BigDecimal.ZERO) {
                            val currentPrice = ticker.lastPrice!!
                            val diff = currentPrice.subtract(openPrice)
                            this.priceChangePercentToday = diff.divide(openPrice, 8, RoundingMode.HALF_UP)
                                .multiply(BigDecimal("100"))
                        }
                    }
                }
                symbolMetricsRepository.save(metrics)
                
                // 4. Cache to Redis
                cacheMetricsToRedis(metrics)
                
                // 5. Alerting (Multi-timeframe surge)
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
                "rvol1m": ${metrics.rvol1m},
                "rvol5m": ${metrics.rvol5m},
                "rvol15m": ${metrics.rvol15m},
                "rvol30m": ${metrics.rvol30m},
                "rvol1h": ${metrics.rvol1h},
                "rvol4h": ${metrics.rvol4h},
                "rvolToday": ${metrics.rvolToday},
                "priceChangePercent24h": ${metrics.priceChangePercent24h},
                "priceChangePercentToday": ${metrics.priceChangePercentToday},
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

    private fun fetchTodayCandles(symbol: Symbol, startOfDay: LocalDateTime): List<Candle1m> {
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
