package me.hajoo.cryptopediaserver.core.client.binance.dto

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
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

@JsonDeserialize(using = FuturesKlineDeserializer::class)
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

class FuturesKlineDeserializer : StdDeserializer<FuturesKline>(FuturesKline::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): FuturesKline {
        val node: JsonNode = p.codec.readTree(p)
        return FuturesKline(
            openTime = node.get(0).asLong(),
            openPrice = node.get(1).asText().toBigDecimal(),
            highPrice = node.get(2).asText().toBigDecimal(),
            lowPrice = node.get(3).asText().toBigDecimal(),
            closePrice = node.get(4).asText().toBigDecimal(),
            volume = node.get(5).asText().toBigDecimal(),
            closeTime = node.get(6).asLong(),
            quoteAssetVolume = node.get(7).asText().toBigDecimal(),
            numberOfTrades = node.get(8).asLong(),
            takerBuyBaseVolume = node.get(9).asText().toBigDecimal(),
            takerBuyQuoteVolume = node.get(10).asText().toBigDecimal()
        )
    }
}

data class FuturesTicker24h(
    val symbol: String,
    val priceChange: BigDecimal?,
    val priceChangePercent: BigDecimal?,
    val weightedAvgPrice: BigDecimal?,
    val prevClosePrice: BigDecimal?,
    val lastPrice: BigDecimal?,
    val lastQty: BigDecimal?,
    val bidPrice: BigDecimal?,
    val bidQty: BigDecimal?,
    val askPrice: BigDecimal?,
    val askQty: BigDecimal?,
    val openPrice: BigDecimal?,
    val highPrice: BigDecimal?,
    val lowPrice: BigDecimal?,
    val volume: BigDecimal?,
    val quoteVolume: BigDecimal?,
    val openTime: Long?,
    val closeTime: Long?,
    val firstId: Long?,
    val lastId: Long?,
    val count: Long?
)

data class FuturesBookTicker(
    val symbol: String,
    val bidPrice: BigDecimal,
    val bidQty: BigDecimal,
    val askPrice: BigDecimal,
    val askQty: BigDecimal
)
