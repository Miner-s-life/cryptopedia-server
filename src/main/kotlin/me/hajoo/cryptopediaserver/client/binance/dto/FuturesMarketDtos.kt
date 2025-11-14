package me.hajoo.cryptopediaserver.client.binance.dto

import java.math.BigDecimal

data class OrderBookEntry(
    val price: BigDecimal,
    val quantity: BigDecimal
)

data class FuturesDepth(
    val lastUpdateId: Long,
    val bids: List<List<BigDecimal>>,
    val asks: List<List<BigDecimal>>
)

data class FuturesRecentTrade(
    val id: Long,
    val price: BigDecimal,
    val qty: BigDecimal,
    val quoteQty: BigDecimal,
    val time: Long,
    val isBuyerMaker: Boolean,
    val isBestMatch: Boolean
)

data class FuturesKline(
    val openTime: Long,
    val openPrice: BigDecimal,
    val highPrice: BigDecimal,
    val lowPrice: BigDecimal,
    val closePrice: BigDecimal,
    val volume: BigDecimal,
    val closeTime: Long,
    val quoteAssetVolume: BigDecimal,
    val numberOfTrades: Long,
    val takerBuyBaseVolume: BigDecimal,
    val takerBuyQuoteVolume: BigDecimal
)

data class FuturesTicker24h(
    val symbol: String,
    val priceChange: BigDecimal,
    val priceChangePercent: BigDecimal,
    val weightedAvgPrice: BigDecimal,
    val prevClosePrice: BigDecimal,
    val lastPrice: BigDecimal,
    val lastQty: BigDecimal,
    val bidPrice: BigDecimal,
    val bidQty: BigDecimal,
    val askPrice: BigDecimal,
    val askQty: BigDecimal,
    val openPrice: BigDecimal,
    val highPrice: BigDecimal,
    val lowPrice: BigDecimal,
    val volume: BigDecimal,
    val quoteVolume: BigDecimal,
    val openTime: Long,
    val closeTime: Long,
    val firstId: Long,
    val lastId: Long,
    val count: Long
)

data class FuturesBookTicker(
    val symbol: String,
    val bidPrice: BigDecimal,
    val bidQty: BigDecimal,
    val askPrice: BigDecimal,
    val askQty: BigDecimal
)
