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

    fun processTicker(
        symbol: String,
        lastPrice: BigDecimal,
        priceChangePercent: BigDecimal,
        volume24h: BigDecimal,
        quoteVolume24h: BigDecimal
    )
}
