package me.hajoo.cryptopediaserver.batch.application.service

import me.hajoo.cryptopediaserver.market.domain.*
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
        val openTimeLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(openTime), ZoneId.of("UTC"))
        val exchange = "BINANCE"

        val existing = candle1mRepository.findByExchangeAndSymbolAndOpenTime(exchange, symbol, openTimeLdt)
        
        if (existing != null) {
            val updated = Candle1m(
                id = existing.id,
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
            candle1mRepository.save(updated)
        } else {
             candle1mRepository.save(
                Candle1m(
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
            )
        }
    }

    override fun processTicker(
        symbol: String,
        lastPrice: BigDecimal,
        priceChangePercent: BigDecimal,
        volume24h: BigDecimal,
        quoteVolume24h: BigDecimal
    ) {
        val exchange = "BINANCE"
        val existing = ticker24hLatestRepository.findByExchangeAndSymbol(exchange, symbol)

        if (existing != null) {
             existing.apply {
                 this.lastPrice = lastPrice
                 this.priceChangePercent = priceChangePercent
                 this.volume24h = volume24h
                 this.quoteVolume24h = quoteVolume24h
                 this.lastUpdated = LocalDateTime.now()
             }
             ticker24hLatestRepository.save(existing)
        } else {
            ticker24hLatestRepository.save(
                Ticker24hLatest(
                    exchange = exchange,
                    symbol = symbol,
                    lastPrice = lastPrice,
                    priceChangePercent = priceChangePercent,
                    volume24h = volume24h,
                    quoteVolume24h = quoteVolume24h
                )
            )
        }
    }
}
