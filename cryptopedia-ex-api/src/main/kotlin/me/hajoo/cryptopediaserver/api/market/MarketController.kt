package me.hajoo.cryptopediaserver.api.market

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import me.hajoo.cryptopediaserver.api.market.dto.CandleResponse
import me.hajoo.cryptopediaserver.api.market.dto.SymbolResponse
import me.hajoo.cryptopediaserver.api.market.dto.TickerWithMetricsResponse
import me.hajoo.cryptopediaserver.core.common.response.ApiResponse
import org.springframework.http.ResponseEntity
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
    @SecurityRequirement(name = "bearerAuth")
    fun getSymbols(): ResponseEntity<ApiResponse<List<SymbolResponse>>> {
        return ResponseEntity.ok(ApiResponse.success(marketService.getActiveSymbols()))
    }

    @GetMapping("/tickers")
    @SecurityRequirement(name = "bearerAuth")
    fun getTickers(): ResponseEntity<ApiResponse<List<TickerWithMetricsResponse>>> {
        return ResponseEntity.ok(ApiResponse.success(marketService.getTickersWithMetrics()))
    }

    @GetMapping("/candles")
    @SecurityRequirement(name = "bearerAuth")
    fun getCandles(
        @RequestParam exchange: String,
        @RequestParam symbol: String,
        @RequestParam(required = false, defaultValue = "100") limit: Int
    ): ResponseEntity<ApiResponse<List<CandleResponse>>> {
        return ResponseEntity.ok(ApiResponse.success(marketService.getCandles(exchange, symbol, limit)))
    }
}
