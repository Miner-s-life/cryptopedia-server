package me.hajoo.cryptopediaserver.batch.application.service

import java.math.BigDecimal

interface MarketDataIngestionService {
    fun processKline(
        symbol: String,
        openTime: Long,
        open: BigDecimal,
        high: BigDecimal,
        low: BigDecimal,
        close: BigDecimal,
        volume: BigDecimal,
        quoteVolume: BigDecimal,
        trades: Long
    )

    fun processTickers(tickers: List<TickerData>)

    data class TickerData(
        val symbol: String,
        val lastPrice: BigDecimal,
        val priceChangePercent: BigDecimal,
        val volume24h: BigDecimal,
        val quoteVolume24h: BigDecimal
    )
}
