package me.hajoo.cryptopediaserver.batch.application.service

import me.hajoo.cryptopediaserver.batch.adapter.`in`.binance.BinanceWebSocketClient
import me.hajoo.cryptopediaserver.core.client.binance.BinanceFuturesMarketClient
import me.hajoo.cryptopediaserver.core.domain.Symbol
import me.hajoo.cryptopediaserver.core.domain.SymbolRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.atomic.AtomicBoolean

@Service
class SymbolSyncService(
    private val symbolRepository: SymbolRepository,
    private val binanceFuturesMarketClient: BinanceFuturesMarketClient,
    private val marketAnalysisService: MarketAnalysisService,
    private val binanceWebSocketClient: BinanceWebSocketClient,
    private val transactionTemplate: TransactionTemplate
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

            val newSymbols = mutableListOf<String>()

            topSymbols.forEach { symbolStr ->
                val existing = symbolRepository.findByExchangeAndSymbol("BINANCE", symbolStr)
                if (existing == null) {
                    transactionTemplate.execute {
                        // Double check inside transaction to reduce race window
                        if (symbolRepository.findByExchangeAndSymbol("BINANCE", symbolStr) == null) {
                            try {
                                logger.info("Found new top symbol: $symbolStr")
                                saveNewSymbol(symbolStr)
                                marketAnalysisService.backfillHistory(symbolStr)
                                newSymbols.add(symbolStr)
                            } catch (e: org.springframework.dao.DataIntegrityViolationException) {
                                logger.warn("Symbol $symbolStr was already inserted by another thread")
                            }
                        }
                    }
                } else if (existing.status != "TRADING") {
                    logger.info("Enabling existing symbol: $symbolStr")
                    transactionTemplate.execute {
                        enableSymbol(existing)
                    }
                    newSymbols.add(symbolStr)
                }
            }

            if (newSymbols.isNotEmpty()) {
                logger.info("Subscribing to ${newSymbols.size} new symbols")
                binanceWebSocketClient.subscribe(newSymbols)
            }
            logger.info("Symbol Sync completed: Top 100 processed, ${newSymbols.size} new symbols added to tracking.")
        } catch (e: Exception) {
            logger.error("Error during symbol sync", e)
        } finally {
            isSyncing.set(false)
        }
    }

    private fun saveNewSymbol(symbolStr: String) {
        symbolRepository.save(
            Symbol(
                exchange = "BINANCE",
                symbol = symbolStr,
                baseAsset = symbolStr.replace("USDT", ""),
                quoteAsset = "USDT",
                status = "TRADING"
            )
        )
    }

    private fun enableSymbol(symbol: Symbol) {
        symbol.status = "TRADING"
        symbolRepository.save(symbol)
    }
}
