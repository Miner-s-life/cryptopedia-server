package me.hajoo.cryptopediaserver.controller

import me.hajoo.cryptopediaserver.entity.Cryptocurrency
import me.hajoo.cryptopediaserver.service.CryptocurrencyService
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/api/v1")
class CryptopediaController(
    private val cryptocurrencyService: CryptocurrencyService
) {

    @GetMapping("/health")
    fun health(): Map<String, Any> {
        return mapOf(
            "status" to "UP",
            "service" to "cryptopedia-api",
            "timestamp" to System.currentTimeMillis()
        )
    }

    @GetMapping("/cryptocurrencies")
    fun getAllCryptocurrencies(): List<Cryptocurrency> {
        return cryptocurrencyService.getAllCryptocurrencies()
    }

    @PostMapping("/cryptocurrencies")
    fun saveCryptocurrency(@RequestBody request: SaveRequest): Cryptocurrency {
        val cryptocurrency = Cryptocurrency(
            symbol = request.symbol,
            name = request.name,
            currentPrice = request.currentPrice
        )
        return cryptocurrencyService.saveCryptocurrency(cryptocurrency)
    }

    data class SaveRequest(
        val symbol: String,
        val name: String,
        val currentPrice: BigDecimal? = null
    )
}
