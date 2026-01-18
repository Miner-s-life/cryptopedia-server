package me.hajoo.cryptopediaserver.api.market.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class SymbolResponse(
    val exchange: String,
    val symbol: String,
    val baseAsset: String,
    val quoteAsset: String,
    val status: String
)

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

data class CandleResponse(
    val openTime: LocalDateTime,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal,
    val quoteVolume: BigDecimal,
    val trades: Long
)
