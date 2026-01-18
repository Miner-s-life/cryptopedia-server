package me.hajoo.cryptopediaserver.batch.application.service

import me.hajoo.cryptopediaserver.core.domain.Candle1m
import me.hajoo.cryptopediaserver.core.domain.Candle1mRepository
import me.hajoo.cryptopediaserver.core.domain.Ticker24hLatest
import me.hajoo.cryptopediaserver.core.domain.Ticker24hLatestRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Service
@Transactional
class MarketDataIngestionServiceImpl(
    private val candle1mRepository: Candle1mRepository,
    private val ticker24hLatestRepository: Ticker24hLatestRepository,
    private val jdbcTemplate: JdbcTemplate
) : MarketDataIngestionService {

    override fun processKlines(klines: List<MarketDataIngestionService.KlineData>) {
        if (klines.isEmpty()) return

        val exchange = "BINANCE"
        val sql = """
            INSERT INTO candles_1m (exchange, symbol, open_time, open_price, high_price, low_price, close_price, volume, quote_volume, trades)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                open_price = VALUES(open_price),
                high_price = VALUES(high_price),
                low_price = VALUES(low_price),
                close_price = VALUES(close_price),
                volume = VALUES(volume),
                quote_volume = VALUES(quote_volume),
                trades = VALUES(trades)
        """.trimIndent()

        jdbcTemplate.batchUpdate(sql, klines, klines.size) { ps, kline ->
            val openTimeLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(kline.openTime), ZoneId.of("UTC"))
            ps.setString(1, exchange)
            ps.setString(2, kline.symbol)
            ps.setTimestamp(3, Timestamp.valueOf(openTimeLdt))
            ps.setBigDecimal(4, kline.open)
            ps.setBigDecimal(5, kline.high)
            ps.setBigDecimal(6, kline.low)
            ps.setBigDecimal(7, kline.close)
            ps.setBigDecimal(8, kline.volume)
            ps.setBigDecimal(9, kline.quoteVolume)
            ps.setLong(10, kline.trades)
        }
    }

    @Deprecated("Use processKlines for better performance")
    override fun processKline(
        symbol: String,
        openTime: Long,
        open: BigDecimal,
        high: BigDecimal,
        low: BigDecimal,
        close: BigDecimal,
        volume: BigDecimal,
        quoteVolume: BigDecimal,
        trades: Long
    ) {
        val openTimeLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(openTime), ZoneId.of("UTC"))
        val exchange = "BINANCE"

        candle1mRepository.upsert(
            exchange = exchange,
            symbol = symbol,
            openTime = openTimeLdt,
            openPrice = open,
            highPrice = high,
            lowPrice = low,
            closePrice = close,
            volume = volume,
            quoteVolume = quoteVolume,
            trades = trades
        )
    }

    override fun processTickers(tickers: List<MarketDataIngestionService.TickerData>) {
        if (tickers.isEmpty()) return
        
        val exchange = "BINANCE"
        val sql = """
            INSERT INTO ticker_24h_latest (exchange, symbol, last_price, price_change_percent, volume24h, quote_volume24h, last_updated)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                last_price = VALUES(last_price),
                price_change_percent = VALUES(price_change_percent),
                volume24h = VALUES(volume24h),
                quote_volume24h = VALUES(quote_volume24h),
                last_updated = VALUES(last_updated)
        """.trimIndent()

        val now = LocalDateTime.now()
        jdbcTemplate.batchUpdate(sql, tickers, tickers.size) { ps, ticker ->
            ps.setString(1, exchange)
            ps.setString(2, ticker.symbol)
            ps.setBigDecimal(3, ticker.lastPrice)
            ps.setBigDecimal(4, ticker.priceChangePercent)
            ps.setBigDecimal(5, ticker.volume24h)
            ps.setBigDecimal(6, ticker.quoteVolume24h)
            ps.setTimestamp(7, Timestamp.valueOf(now))
        }
    }

    @Deprecated("Use processTickers for better performance")
    fun processTicker(
        symbol: String,
        lastPrice: BigDecimal,
        priceChangePercent: BigDecimal,
        volume24h: BigDecimal,
        quoteVolume24h: BigDecimal
    ) {
        val exchange = "BINANCE"
        
        ticker24hLatestRepository.upsert(
            exchange = exchange,
            symbol = symbol,
            lastPrice = lastPrice,
            priceChangePercent = priceChangePercent,
            volume24h = volume24h,
            quoteVolume24h = quoteVolume24h,
            lastUpdated = LocalDateTime.now()
        )
    }
}
