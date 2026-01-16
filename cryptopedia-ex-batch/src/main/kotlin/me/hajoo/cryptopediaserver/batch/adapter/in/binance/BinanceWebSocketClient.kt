package me.hajoo.cryptopediaserver.batch.adapter.`in`.binance

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import me.hajoo.cryptopediaserver.batch.application.service.MarketDataIngestionService
import okhttp3.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import jakarta.annotation.PreDestroy
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
    private val baseUrl = "wss://stream.binance.com:9443/stream"
    private var isConnected = false
    private var isShuttingDown = false

    fun connect(symbols: List<String>) {
        val request = Request.Builder()
            .url(baseUrl)
            .build()

        webSocket = client.newWebSocket(request, this)
        // Wait for connection to open? Or just queue subscription? 
        // Binance usually connects fast. We can subscribe in onOpen or send immediately if ready.
        // For simplicity, we'll store initial symbols and subscribe in onOpen.
        this.pendingSymbols.addAll(symbols)
    }

    private val pendingSymbols = mutableSetOf<String>()

    fun subscribe(symbols: List<String>) {
        if (symbols.isEmpty()) return

        if (!isConnected) {
            pendingSymbols.addAll(symbols)
            return
        }

        // Chunking to avoid "too many params" if any (though standard limit is high)
        symbols.chunked(50).forEach { chunk ->
            val params = chunk.map { symbol ->
                val s = symbol.lowercase()
                listOf("$s@kline_1m", "$s@ticker")
            }.flatten()

            val payload = """
            {
                "method": "SUBSCRIBE",
                "params": ${objectMapper.writeValueAsString(params)},
                "id": ${System.currentTimeMillis()}
            }
            """.trimIndent()
            
            webSocket?.send(payload)
            logger.info("Sent SUBSCRIBE for ${chunk.size} symbols")
        }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        logger.info("Connected to Binance WebSocket")
        isConnected = true
        
        if (pendingSymbols.isNotEmpty()) {
            subscribe(pendingSymbols.toList())
            pendingSymbols.clear()
        }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        if (isShuttingDown) return

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
        isConnected = false
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        logger.error("Binance WebSocket failure", t)
        isConnected = false
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

    @PreDestroy
    fun shutdown() {
        logger.info("Shutting down Binance WebSocket Client...")
        isShuttingDown = true
        isConnected = false
        webSocket?.close(1000, "Application shutdown")
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
