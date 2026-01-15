package me.hajoo.cryptopediaserver.batch.adapter.`in`.binance

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import me.hajoo.cryptopediaserver.batch.application.service.MarketDataIngestionService
import okhttp3.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class BinanceWebSocketClient(
    private val objectMapper: ObjectMapper,
    private val marketDataIngestionService: MarketDataIngestionService
) : WebSocketListener() {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val baseUrl = "wss://stream.binance.com:9443/stream?streams="

    fun connect(symbols: List<String>) {
        if (symbols.isEmpty()) return

        val streams = symbols.joinToString("/") { symbol ->
            val s = symbol.lowercase()
            "$s@kline_1m/$s@ticker"
        }
        
        val request = Request.Builder()
            .url(baseUrl + streams)
            .build()

        webSocket = client.newWebSocket(request, this)
        logger.info("Connecting to Binance WebSocket with streams for ${symbols.size} symbols")
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        logger.info("Connected to Binance WebSocket")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            val node = objectMapper.readTree(text)
            if (node.has("data")) {
                val data = node.get("data")
                val eventType = data.get("e")?.asText()

                when (eventType) {
                    "kline" -> handleKline(data)
                    "24hrTicker" -> handleTicker(data)
                }
            }
        } catch (e: Exception) {
            logger.error("Error parsing message: ${e.message}", e)
        }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        logger.info("Binance WebSocket closed: $code / $reason")
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        logger.error("Binance WebSocket failure", t)
        // Simple reconnect logic could go here
    }

    private fun handleKline(node: JsonNode) {
        val symbol = node.get("s").asText()
        val kline = node.get("k")
        val isClosed = kline.get("x").asBoolean()

        
        marketDataIngestionService.processKline(
             symbol = symbol,
             openTime = kline.get("t").asLong(),
             open = kline.get("o").asText().toBigDecimal(),
             high = kline.get("h").asText().toBigDecimal(),
             low = kline.get("l").asText().toBigDecimal(),
             close = kline.get("c").asText().toBigDecimal(),
             volume = kline.get("v").asText().toBigDecimal(),
             quoteVolume = kline.get("q").asText().toBigDecimal(),
             trades = kline.get("n").asLong()
        )
    }

    private fun handleTicker(node: JsonNode) {
        marketDataIngestionService.processTicker(
            symbol = node.get("s").asText(),
            lastPrice = node.get("c").asText().toBigDecimal(),
            priceChangePercent = node.get("P").asText().toBigDecimal(),
            volume24h = node.get("v").asText().toBigDecimal(),
            quoteVolume24h = node.get("q").asText().toBigDecimal()
        )
    }
}
