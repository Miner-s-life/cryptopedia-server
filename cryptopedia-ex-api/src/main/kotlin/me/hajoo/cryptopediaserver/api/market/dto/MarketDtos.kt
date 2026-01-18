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
