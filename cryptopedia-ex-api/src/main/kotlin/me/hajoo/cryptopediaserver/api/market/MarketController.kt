package me.hajoo.cryptopediaserver.api.market

import me.hajoo.cryptopediaserver.api.market.dto.CandleResponse
import me.hajoo.cryptopediaserver.api.market.dto.SymbolResponse
import me.hajoo.cryptopediaserver.api.market.dto.TickerWithMetricsResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/market")
class MarketController(
    private val marketService: MarketService
) {

    @GetMapping("/symbols")
    fun getSymbols(): List<SymbolResponse> {
        return marketService.getActiveSymbols()
    }

    @GetMapping("/tickers")
    fun getTickers(): List<TickerWithMetricsResponse> {
        return marketService.getTickersWithMetrics()
    }

    @GetMapping("/candles")
    fun getCandles(
        @RequestParam exchange: String,
        @RequestParam symbol: String,
        @RequestParam(required = false, defaultValue = "100") limit: Int
    ): List<CandleResponse> {
        return marketService.getCandles(exchange, symbol, limit)
    }
}
