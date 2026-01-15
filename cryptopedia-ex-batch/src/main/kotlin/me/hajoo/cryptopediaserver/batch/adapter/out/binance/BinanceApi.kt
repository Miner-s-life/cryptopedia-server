package me.hajoo.cryptopediaserver.batch.adapter.out.binance

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(name = "binance-api", url = "https://api.binance.com")
interface BinanceApi {
    
    // Response: [[OpenTime, Open, High, Low, Close, Volume, CloseTime, QuoteVolume, ...], ...]
    // We bind to List<List<String>> to handle mixed types (numbers are strings in Binance API)
    @GetMapping("/api/v3/klines")
    fun getKlines(
        @RequestParam("symbol") symbol: String,
        @RequestParam("interval") interval: String,
        @RequestParam("limit") limit: Int
    ): List<List<String>>
}
