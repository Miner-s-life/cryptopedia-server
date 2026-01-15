package me.hajoo.cryptopediaserver.batch.adapter.out.binance

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class BinanceRestClient(
    private val binanceApi: BinanceApi
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    data class DailyKline(
        val openTime: Long,
        val volume: BigDecimal,
        val quoteVolume: BigDecimal
    )

    fun getDailyKlines(symbol: String, limit: Int = 60): List<DailyKline> {
        return try {
            val response = binanceApi.getKlines(symbol.uppercase(), "1d", limit)
            
            response.map { node ->
                // Index 0: OpenTime, Index 5: Volume, Index 7: QuoteVolume
                DailyKline(
                    openTime = node[0].toLong(),
                    volume = BigDecimal(node[5]),
                    quoteVolume = BigDecimal(node[7])
                )
            }
        } catch (e: Exception) {
            logger.error("Error fetching historical klines for $symbol", e)
            emptyList()
        }
    }
}
