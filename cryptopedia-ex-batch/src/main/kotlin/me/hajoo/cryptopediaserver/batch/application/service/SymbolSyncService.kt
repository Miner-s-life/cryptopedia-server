package me.hajoo.cryptopediaserver.batch.application.service

import me.hajoo.cryptopediaserver.batch.adapter.`in`.binance.BinanceWebSocketClient
import me.hajoo.cryptopediaserver.core.client.binance.BinanceFuturesMarketClient
import me.hajoo.cryptopediaserver.core.client.binance.dto.FuturesSymbolInfo
import me.hajoo.cryptopediaserver.core.domain.Symbol
import me.hajoo.cryptopediaserver.core.domain.SymbolRepository
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.math.BigDecimal
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
    
    // Cache for valid symbols from exchangeInfo to be used in ranking sync
    @Volatile
    private var validBinanceSymbols: Map<String, FuturesSymbolInfo> = emptyMap()

    @Scheduled(fixedRate = 3600000) // 1 hour - Source of Truth Sync
    fun syncExchangeInfo() {
        try {
            logger.info("Starting ExchangeInfo Sync Job...")
            val exchangeInfo = binanceFuturesMarketClient.getExchangeInfo()
            
            val currentValidSymbols = exchangeInfo.symbols
                .filter { it.status == "TRADING" && it.contractType == "PERPETUAL" && it.quoteAsset == "USDT" }
                .associateBy { it.symbol }
            
            validBinanceSymbols = currentValidSymbols

            val allExistingSymbols = symbolRepository.findAllByExchange("BINANCE")
            val symbolsToDelist = allExistingSymbols
                .filter { s -> !currentValidSymbols.containsKey(s.symbol) && s.status != "DELISTED" }

            if (symbolsToDelist.isNotEmpty()) {
                batchUpdateSymbolStatus(symbolsToDelist, "DELISTED")
                logger.info("Delisted ${symbolsToDelist.size} symbols based on exchangeInfo")
            }
            logger.info("ExchangeInfo Sync completed. Total valid symbols: ${currentValidSymbols.size}")
        } catch (e: Exception) {
            logger.error("Failed to sync exchangeInfo", e)
        }
    }

    @Scheduled(fixedRate = 300000) // 5 minutes - Ranking & Status Transition
    fun syncTopVolumeSymbols() {
        if (!isSyncing.compareAndSet(false, true)) {
            logger.info("Symbol Ranking Sync is already running, skipping.")
            return
        }

        try {
            logger.info("Starting Symbol Ranking Sync...")
            
            val validSymbols = validBinanceSymbols
            if (validSymbols.isEmpty()) {
                logger.warn("No valid symbols cached from exchangeInfo, skipping ranking sync.")
                return
            }

            // 1. Fetch Volume Ranking
            val allTickers = try {
                binanceFuturesMarketClient.getAll24hTickers()
            } catch (e: Exception) {
                logger.error("Failed to fetch tickers", e)
                return
            }

            val top100SymbolNames = allTickers
                .filter { it.symbol.endsWith("USDT") && validSymbols.containsKey(it.symbol) }
                .sortedByDescending { it.quoteVolume ?: BigDecimal.ZERO }
                .take(100)
                .map { it.symbol }
                .toSet()

            // 2. Process DB Updates
            val allExistingSymbols = symbolRepository.findAllByExchange("BINANCE")
            
            val symbolsToInsert = mutableListOf<Symbol>()
            val symbolsToEnable = mutableListOf<Symbol>() // TRADING
            val symbolsToPause = mutableListOf<Symbol>()  // BREAK

            // Check existing for status changes
            allExistingSymbols.forEach { s ->
                val isValid = validSymbols.containsKey(s.symbol)
                val isTop100 = top100SymbolNames.contains(s.symbol)

                if (isValid) {
                    if (isTop100) {
                        if (s.status != "TRADING") symbolsToEnable.add(s)
                    } else {
                        if (s.status == "TRADING") symbolsToPause.add(s)
                    }
                }
            }

            // Check for new symbols in Top 100
            val existingNames = allExistingSymbols.map { it.symbol }.toSet()
            top100SymbolNames.filter { !existingNames.contains(it) }.forEach { name ->
                val info = validSymbols[name]!!
                symbolsToInsert.add(
                    Symbol(
                        exchange = "BINANCE",
                        symbol = name,
                        baseAsset = info.baseAsset,
                        quoteAsset = info.quoteAsset,
                        status = "TRADING"
                    )
                )
            }

            // 3. Batch Execute
            if (symbolsToInsert.isNotEmpty()) {
                batchInsertSymbols(symbolsToInsert)
                logger.info("Bulk inserted ${symbolsToInsert.size} new symbols")
            }

            if (symbolsToEnable.isNotEmpty()) {
                batchUpdateSymbolStatus(symbolsToEnable, "TRADING")
                logger.info("Bulk enabled ${symbolsToEnable.size} symbols (TRADING)")
            }

            if (symbolsToPause.isNotEmpty()) {
                batchUpdateSymbolStatus(symbolsToPause, "BREAK")
                logger.info("Bulk paused ${symbolsToPause.size} symbols (BREAK)")
            }

            // 4. Post-sync Backfill & Subscription
            val newlyActive = symbolsToInsert + symbolsToEnable
            if (newlyActive.isNotEmpty()) {
                binanceWebSocketClient.subscribe(newlyActive.map { it.symbol })
                marketAnalysisService.backfillHistoryBulk(newlyActive)
                marketAnalysisService.backfillTodayCandlesBulk(newlyActive)
            }
            
            logger.info("Symbol Ranking Sync completed.")
        } catch (e: Exception) {
            logger.error("Error during ranking sync", e)
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
