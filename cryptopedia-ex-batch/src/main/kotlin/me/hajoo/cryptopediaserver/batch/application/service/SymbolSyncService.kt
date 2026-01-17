package me.hajoo.cryptopediaserver.batch.application.service

import me.hajoo.cryptopediaserver.batch.adapter.`in`.binance.BinanceWebSocketClient
import me.hajoo.cryptopediaserver.core.client.binance.BinanceFuturesMarketClient
import me.hajoo.cryptopediaserver.core.domain.Symbol
import me.hajoo.cryptopediaserver.core.domain.SymbolRepository
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
class SymbolSyncService(
    private val symbolRepository: SymbolRepository,
    private val binanceFuturesMarketClient: BinanceFuturesMarketClient,
    private val marketAnalysisService: MarketAnalysisService,
    private val binanceWebSocketClient: BinanceWebSocketClient,
    private val jdbcTemplate: JdbcTemplate
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val isSyncing = AtomicBoolean(false)

    @Scheduled(fixedRate = 300000) // 5 minutes
    fun syncTopVolumeSymbols() {
        if (!isSyncing.compareAndSet(false, true)) {
            logger.info("Symbol Sync Job is already running, skipping this execution.")
            return
        }

        try {
            logger.info("Starting Symbol Sync Job...")
            val allTickers = try {
                val tickers = binanceFuturesMarketClient.getAll24hTickers()
                logger.info("Fetched ${tickers.size} tickers from Binance")
                tickers
            } catch (e: Exception) {
                logger.error("Failed to fetch tickers", e)
                return
            }

            val topSymbols = allTickers
                .filter { it.symbol.endsWith("USDT") }
                .sortedByDescending { it.quoteVolume ?: java.math.BigDecimal.ZERO }
                .take(100)
                .map { it.symbol }

            val symbolsToInsert = mutableListOf<Symbol>()
            val symbolsToEnable = mutableListOf<Symbol>()

            topSymbols.forEach { symbolStr ->
                val existing = symbolRepository.findByExchangeAndSymbol("BINANCE", symbolStr)
                if (existing == null) {
                    symbolsToInsert.add(
                        Symbol(
                            exchange = "BINANCE",
                            symbol = symbolStr,
                            baseAsset = symbolStr.replace("USDT", ""),
                            quoteAsset = "USDT",
                            status = "TRADING"
                        )
                    )
                } else if (existing.status != "TRADING") {
                    symbolsToEnable.add(existing)
                }
            }

            if (symbolsToInsert.isNotEmpty()) {
                batchInsertSymbols(symbolsToInsert)
                logger.info("Bulk inserted ${symbolsToInsert.size} new symbols")
            }

            if (symbolsToEnable.isNotEmpty()) {
                batchUpdateSymbolStatus(symbolsToEnable, "TRADING")
                logger.info("Bulk enabled ${symbolsToEnable.size} existing symbols")
            }

            val affectedSymbols = (symbolsToInsert + symbolsToEnable)
            if (affectedSymbols.isNotEmpty()) {
                // 1. WebSocket Subscription
                binanceWebSocketClient.subscribe(affectedSymbols.map { it.symbol })
                
                // 2. Bulk Backfill
                marketAnalysisService.backfillTodayCandlesBulk(affectedSymbols)
            }
            
            logger.info("Symbol Sync completed: Top 100 processed, ${affectedSymbols.size} symbols updated/added.")
        } catch (e: Exception) {
            logger.error("Error during symbol sync", e)
        } finally {
            isSyncing.set(false)
        }
    }

    private fun batchInsertSymbols(symbols: List<Symbol>) {
        val sql = """
            INSERT IGNORE INTO symbols (exchange, symbol, base_asset, quote_asset, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, NOW(), NOW())
        """.trimIndent()

        jdbcTemplate.batchUpdate(sql, symbols, 100) { ps, s ->
            ps.setString(1, s.exchange)
            ps.setString(2, s.symbol)
            ps.setString(3, s.baseAsset)
            ps.setString(4, s.quoteAsset)
            ps.setString(5, s.status)
        }
    }

    private fun batchUpdateSymbolStatus(symbols: List<Symbol>, status: String) {
        val sql = "UPDATE symbols SET status = ?, updated_at = NOW() WHERE id = ?"
        jdbcTemplate.batchUpdate(sql, symbols, 100) { ps, s ->
            ps.setString(1, status)
            ps.setLong(2, s.id)
        }
    }
}
