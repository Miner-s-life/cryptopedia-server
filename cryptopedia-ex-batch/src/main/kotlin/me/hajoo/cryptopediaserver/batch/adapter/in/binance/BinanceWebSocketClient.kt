package me.hajoo.cryptopediaserver.batch.adapter.`in`.binance

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import me.hajoo.cryptopediaserver.batch.application.service.MarketDataIngestionService
import okhttp3.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
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
    
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val tickerBuffer = ConcurrentHashMap<String, MarketDataIngestionService.TickerData>()
    
    @PostConstruct
    fun init() {
        scheduler.scheduleAtFixedRate({
            flushTickerBuffer()
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun flushTickerBuffer() {
        if (tickerBuffer.isEmpty()) return

        val tickersToProcess = tickerBuffer.values.toList()
        tickerBuffer.clear()

        try {
            marketDataIngestionService.processTickers(tickersToProcess)
            logger.debug("Flushed ${tickersToProcess.size} tickers to DB")
        } catch (e: Exception) {
            logger.error("Failed to flush ticker buffer", e)
        }
    }

    private val pendingSymbols = ConcurrentHashMap.newKeySet<String>()
    private val subscribedSymbols = ConcurrentHashMap.newKeySet<String>()

    fun connect(symbols: List<String>) {
        if (isShuttingDown) return
        
        pendingSymbols.addAll(symbols)

        if (isConnected || webSocket != null) {
            logger.info("Binance WebSocket is already connecting or connected. Symbols added to pending.")
            if (isConnected) {
                val toSubscribe = pendingSymbols.toList()
                pendingSymbols.clear()
                subscribe(toSubscribe)
            }
            return
        }

        val request = Request.Builder()
            .url(baseUrl)
            .build()

        logger.info("Attempting to connect to Binance Futures WebSocket: $baseUrl")
        webSocket = client.newWebSocket(request, this)
    }

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
            subscribedSymbols.addAll(chunk) // Add to subscribed list
            logger.info("Sent SUBSCRIBE for ${chunk.size} symbols")
        }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        logger.info("Connected to Binance WebSocket")
        isConnected = true
        
        // Restore previous subscriptions if any (for logic like reconnect)
        if (subscribedSymbols.isNotEmpty()) {
            logger.info("Restoring ${subscribedSymbols.size} previous subscriptions")
            subscribe(subscribedSymbols.toList())
        }

        if (pendingSymbols.isNotEmpty()) {
            val toSubscribe = pendingSymbols.toList()
            pendingSymbols.clear() // Clear before subscribing to avoid duplicates if onOpen is called multiple times
            subscribe(toSubscribe)
        }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        if (isShuttingDown) return

        try {
            val node = objectMapper.readTree(text)
            
            // Binance Stream messages are wrapped in {"stream": "...", "data": {...}}
            if (node.has("data")) {
                val data = node.get("data")
                val eventType = data.get("e")?.asText()

                when (eventType) {
                    "kline" -> handleKline(data)
                    "24hrTicker" -> handleTicker(data)
                }
            } else {
                // Check if it's a response to SUBSCRIBE
                if (node.has("result") && node.get("result").isNull) {
                    // Success response for subscription
                }
            }
        } catch (e: Exception) {
            logger.error("Error parsing message: ${e.message}", e)
        }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        logger.info("Binance WebSocket closed: $code / $reason")
        isConnected = false
        if (!isShuttingDown) {
            scheduleReconnect()
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        logger.error("Binance WebSocket failure: ${t.message}", t)
        isConnected = false
        if (!isShuttingDown) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        logger.info("Scheduling WebSocket reconnect in 5 seconds...")
        scheduler.schedule({
            try {
                logger.info("Attempting to reconnect to Binance WebSocket...")
                connect(emptyList()) // Connect will restore subscribedSymbols and pendingSymbols
            } catch (e: Exception) {
                logger.error("Reconnect attempt failed", e)
            }
        }, 5, TimeUnit.SECONDS)
    }

    private fun handleKline(node: JsonNode) {
        val symbol = node.get("s").asText()
        val kline = node.get("k")

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
        val symbol = node.get("s").asText()
        val tickerData = MarketDataIngestionService.TickerData(
            symbol = symbol,
            lastPrice = node.get("c").asText().toBigDecimal(),
            priceChangePercent = node.get("P").asText().toBigDecimal(),
            volume24h = node.get("v").asText().toBigDecimal(),
            quoteVolume24h = node.get("q").asText().toBigDecimal()
        )
        tickerBuffer[symbol] = tickerData
    }

    @Volatile
    private var webSocket: WebSocket? = null
    private val baseUrl = "wss://fstream.binance.com/stream"
    @Volatile
    private var isConnected = false
    private var isShuttingDown = false

    @PreDestroy
    fun shutdown() {
        logger.info("Shutting down Binance WebSocket Client...")
        isShuttingDown = true
        isConnected = false
        webSocket?.close(1000, "Application shutdown")
        scheduler.shutdown()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
