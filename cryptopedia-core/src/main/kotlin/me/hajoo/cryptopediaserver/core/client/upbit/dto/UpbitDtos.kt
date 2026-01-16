package me.hajoo.cryptopediaserver.core.client.upbit.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class UpbitMarket(
    val market: String,
    @JsonProperty("korean_name")
    val koreanName: String,
    @JsonProperty("english_name")
    val englishName: String
)

data class UpbitCandle(
    val market: String,
    @JsonProperty("candle_date_time_utc")
    val candleDateTimeUtc: String,
    @JsonProperty("opening_price")
    val openingPrice: BigDecimal,
    @JsonProperty("high_price")
    val highPrice: BigDecimal,
    @JsonProperty("low_price")
    val lowPrice: BigDecimal,
    @JsonProperty("trade_price")
    val tradePrice: BigDecimal,
    val timestamp: Long,
    @JsonProperty("candle_acc_trade_price")
    val candleAccTradePrice: BigDecimal,
    @JsonProperty("candle_acc_trade_volume")
    val candleAccTradeVolume: BigDecimal,
    val unit: Int
)
