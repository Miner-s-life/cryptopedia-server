package me.hajoo.cryptopediaserver.core.client.binance

import me.hajoo.cryptopediaserver.core.client.binance.dto.FuturesBookTicker
import me.hajoo.cryptopediaserver.core.client.binance.dto.FuturesDepth
import me.hajoo.cryptopediaserver.core.client.binance.dto.FuturesExchangeInfo
import me.hajoo.cryptopediaserver.core.client.binance.dto.FuturesKline
import me.hajoo.cryptopediaserver.core.client.binance.dto.FuturesRecentTrade
import me.hajoo.cryptopediaserver.core.client.binance.dto.FuturesTicker24h
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
    name = "binanceFuturesMarketClient",
    url = "\${binance.futures.base-url}"
)
interface BinanceFuturesMarketClient {

    @GetMapping("/fapi/v1/exchangeInfo")
    fun getExchangeInfo(): FuturesExchangeInfo

    @GetMapping("/fapi/v1/depth")
    fun getDepth(
        @RequestParam("symbol") symbol: String,
        @RequestParam("limit") limit: Int? = null
    ): FuturesDepth

    @GetMapping("/fapi/v1/trades")
    fun getRecentTrades(
        @RequestParam("symbol") symbol: String,
        @RequestParam("limit") limit: Int? = null
    ): List<FuturesRecentTrade>

    @GetMapping("/fapi/v1/klines")
    fun getKlines(
        @RequestParam("symbol") symbol: String,
        @RequestParam("interval") interval: String,
        @RequestParam("startTime") startTime: Long? = null,
        @RequestParam("endTime") endTime: Long? = null,
        @RequestParam("limit") limit: Int? = null
    ): List<FuturesKline>

    @GetMapping("/fapi/v1/ticker/24hr")
    fun getAll24hTickers(): List<FuturesTicker24h>

    @GetMapping("/fapi/v1/ticker/bookTicker")
    fun getBookTicker(
        @RequestParam("symbol") symbol: String
    ): FuturesBookTicker
}
