package me.hajoo.cryptopediaserver.client.binance
import me.hajoo.cryptopediaserver.core.client.binance.BinanceFuturesMarketClient
import me.hajoo.cryptopediaserver.core.client.binance.dto.FuturesBookTicker
import me.hajoo.cryptopediaserver.core.client.binance.dto.FuturesDepth
import me.hajoo.cryptopediaserver.core.client.binance.dto.FuturesKline
import me.hajoo.cryptopediaserver.core.client.binance.dto.FuturesRecentTrade
import me.hajoo.cryptopediaserver.core.client.binance.dto.FuturesTicker24h
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.http.HttpMessageConverters
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.cloud.openfeign.FeignAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal

@SpringBootTest
@EnableFeignClients(basePackages = ["me.hajoo.cryptopediaserver.client"])
@TestPropertySource(
    properties = [
        "binance.futures.base-url=http://localhost:8081",
    ]
)
class BinanceFuturesMarketClientTest {

    @Autowired
    private lateinit var client: BinanceFuturesMarketClient

    @Autowired
    private lateinit var server: MockWebServer

    @Test
    fun `getDepth should call depth endpoint and map response`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                {
                  "lastUpdateId": 1,
                  "bids": [["100.0", "1.0"]],
                  "asks": [["101.0", "2.0"]]
                }
                """.trimIndent()
                )
                .setHeader("Content-Type", "application/json")
        )

        val result: FuturesDepth = client.getDepth(symbol = "BTCUSDT", limit = 10)

        val recordedRequest = server.takeRequest()

        println("[BinanceFuturesMarketClientTest] depth request method=${recordedRequest.method}, path=${recordedRequest.path}")
        println("[BinanceFuturesMarketClientTest] depth response body mapped=$result")

        assertThat(result.lastUpdateId).isEqualTo(1L)
        assertThat(result.bids).hasSize(1)
        assertThat(result.asks).hasSize(1)
        assertThat(result.bids[0][0]).isEqualTo(BigDecimal("100.0"))
        assertThat(result.bids[0][1]).isEqualTo(BigDecimal("1.0"))
        assertThat(result.asks[0][0]).isEqualTo(BigDecimal("101.0"))
        assertThat(result.asks[0][1]).isEqualTo(BigDecimal("2.0"))
    }

    @Test
    fun `getRecentTrades should call trades endpoint and map response`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                [
                  {
                    "id": 28457,
                    "price": "4.00000100",
                    "qty": "12.00000000",
                    "quoteQty": "48.000012",
                    "time": 1499865549590,
                    "isBuyerMaker": true,
                    "isBestMatch": true
                  }
                ]
                """.trimIndent()
                )
                .setHeader("Content-Type", "application/json")
        )

        val result: List<FuturesRecentTrade> = client.getRecentTrades(symbol = "BTCUSDT", limit = 5)

        val recordedRequest = server.takeRequest()
        println("[BinanceFuturesMarketClientTest] trades request method=${recordedRequest.method}, path=${recordedRequest.path}")
        println("[BinanceFuturesMarketClientTest] trades response body mapped=$result")

        assertThat(result).hasSize(1)
        val trade = result[0]
        assertThat(trade.id).isEqualTo(28457L)
        assertThat(trade.price).isEqualTo(BigDecimal("4.00000100"))
        assertThat(trade.qty).isEqualTo(BigDecimal("12.00000000"))
        assertThat(trade.quoteQty).isEqualTo(BigDecimal("48.000012"))
        assertThat(trade.isBuyerMaker).isTrue()
        assertThat(trade.isBestMatch).isTrue()
    }

    @Test
    fun `getKlines should call klines endpoint and map response`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                [
                  {
                    "openTime": 1499040000000,
                    "openPrice": "0.01634790",
                    "highPrice": "0.80000000",
                    "lowPrice": "0.01575800",
                    "closePrice": "0.01577100",
                    "volume": "148976.11427815",
                    "closeTime": 1499644799999,
                    "quoteAssetVolume": "2434.19055334",
                    "numberOfTrades": 308,
                    "takerBuyBaseVolume": "1756.87402397",
                    "takerBuyQuoteVolume": "28.46694368"
                  }
                ]
                """.trimIndent()
                )
                .setHeader("Content-Type", "application/json")
        )

        val result: List<FuturesKline> = client.getKlines(symbol = "BTCUSDT", interval = "1m", limit = 1)

        val recordedRequest = server.takeRequest()
        println("[BinanceFuturesMarketClientTest] klines request method=${recordedRequest.method}, path=${recordedRequest.path}")
        println("[BinanceFuturesMarketClientTest] klines response body mapped=$result")

        assertThat(result).hasSize(1)
        val kline = result[0]
        assertThat(kline.openTime).isEqualTo(1499040000000L)
        assertThat(kline.openPrice).isEqualTo(BigDecimal("0.01634790"))
        assertThat(kline.closePrice).isEqualTo(BigDecimal("0.01577100"))
    }

    @Test
    fun `get24hTicker should call ticker 24hr endpoint and map response`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                {
                  "symbol": "BNBBTC",
                  "priceChange": "-94.99999800",
                  "priceChangePercent": "-95.960",
                  "weightedAvgPrice": "0.29628482",
                  "prevClosePrice": "0.10002000",
                  "lastPrice": "4.00000200",
                  "lastQty": "200.00000000",
                  "bidPrice": "4.00000000",
                  "bidQty": "100.00000000",
                  "askPrice": "4.00000200",
                  "askQty": "100.00000000",
                  "openPrice": "99.00000000",
                  "highPrice": "100.00000000",
                  "lowPrice": "0.10000000",
                  "volume": "8913.30000000",
                  "quoteVolume": "15.30000000",
                  "openTime": 1499783499040,
                  "closeTime": 1499869899040,
                  "firstId": 28385,
                  "lastId": 28460,
                  "count": 76
                }
                """.trimIndent()
                )
                .setHeader("Content-Type", "application/json")
        )

        val result: FuturesTicker24h = client.get24hTicker(symbol = "BNBBTC")

        val recordedRequest = server.takeRequest()
        println("[BinanceFuturesMarketClientTest] 24h ticker request method=${recordedRequest.method}, path=${recordedRequest.path}")
        println("[BinanceFuturesMarketClientTest] 24h ticker response body mapped=$result")

        assertThat(result.symbol).isEqualTo("BNBBTC")
        assertThat(result.lastPrice).isEqualTo(BigDecimal("4.00000200"))
        assertThat(result.volume).isEqualTo(BigDecimal("8913.30000000"))
    }

    @Test
    fun `getBookTicker should call bookTicker endpoint and map response`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                {
                  "symbol": "LTCBTC",
                  "bidPrice": "4.00000000",
                  "bidQty": "431.00000000",
                  "askPrice": "4.00000200",
                  "askQty": "9.00000000"
                }
                """.trimIndent()
                )
                .setHeader("Content-Type", "application/json")
        )

        val result: FuturesBookTicker = client.getBookTicker(symbol = "LTCBTC")

        val recordedRequest = server.takeRequest()
        println("[BinanceFuturesMarketClientTest] bookTicker request method=${recordedRequest.method}, path=${recordedRequest.path}")
        println("[BinanceFuturesMarketClientTest] bookTicker response body mapped=$result")

        assertThat(result.symbol).isEqualTo("LTCBTC")
        assertThat(result.bidPrice).isEqualTo(BigDecimal("4.00000000"))
        assertThat(result.askPrice).isEqualTo(BigDecimal("4.00000200"))
    }

    @Configuration
    @ImportAutoConfiguration(FeignAutoConfiguration::class)
    class TestConfig {

        @Bean
        fun httpMessageConverters(): HttpMessageConverters = HttpMessageConverters()

        @Bean
        fun mockWebServer(): MockWebServer = MockWebServer().apply { start(8081) }
    }
}
