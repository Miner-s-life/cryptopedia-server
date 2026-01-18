package me.hajoo.cryptopediaserver.core.market.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class TickerWithMetricsResponse(
    val exchange: String,
    val symbol: String,
    val lastPrice: BigDecimal,
    val priceChangePercent: BigDecimal,
    val volume24h: BigDecimal,
    val quoteVolume24h: BigDecimal,
    val rvol1m: BigDecimal,
    val rvol5m: BigDecimal,
    val rvol15m: BigDecimal,
    val rvol30m: BigDecimal,
    val rvol1h: BigDecimal,
    val rvol4h: BigDecimal,
    val rvolToday: BigDecimal,
    val priceChangePercentToday: BigDecimal,
    val isSurging: Boolean,
    val lastUpdated: LocalDateTime
)
