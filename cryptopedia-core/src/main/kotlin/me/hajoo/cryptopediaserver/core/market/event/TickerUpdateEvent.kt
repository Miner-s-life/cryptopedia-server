package me.hajoo.cryptopediaserver.core.market.event

import me.hajoo.cryptopediaserver.core.market.dto.TickerWithMetricsResponse

data class TickerUpdateEvent(
    val tickers: List<TickerWithMetricsResponse>
)
