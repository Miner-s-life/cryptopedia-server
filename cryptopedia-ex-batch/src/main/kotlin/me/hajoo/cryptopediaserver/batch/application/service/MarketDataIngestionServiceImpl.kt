package me.hajoo.cryptopediaserver.batch.application.service

import me.hajoo.cryptopediaserver.core.domain.Candle1m
import me.hajoo.cryptopediaserver.core.domain.Candle1mRepository
import me.hajoo.cryptopediaserver.core.domain.Ticker24hLatest
import me.hajoo.cryptopediaserver.core.domain.Ticker24hLatestRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Service
@Transactional
class MarketDataIngestionServiceImpl(
    private val candle1mRepository: Candle1mRepository,
    private val ticker24hLatestRepository: Ticker24hLatestRepository
) : MarketDataIngestionService {

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

    override fun processTicker(
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
