package me.hajoo.cryptopediaserver.batch.application.service

import me.hajoo.cryptopediaserver.batch.adapter.`in`.binance.BinanceWebSocketClient
import me.hajoo.cryptopediaserver.core.client.binance.BinanceFuturesMarketClient
import me.hajoo.cryptopediaserver.core.domain.Symbol
import me.hajoo.cryptopediaserver.core.domain.SymbolRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SymbolSyncService(
    private val symbolRepository: SymbolRepository,
    private val binanceFuturesMarketClient: BinanceFuturesMarketClient,
    private val marketAnalysisService: MarketAnalysisService,
    private val binanceWebSocketClient: BinanceWebSocketClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 300000) // 5 minutes
    @Transactional
    fun syncTopVolumeSymbols() {
        logger.info("Starting Symbol Sync Job...")
        try {
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
                    logger.info("Found new top symbol: $symbolStr")
                    symbolRepository.save(
                        Symbol(
                            exchange = "BINANCE",
                            symbol = symbolStr,
                            baseAsset = symbolStr.replace("USDT", ""),
                            quoteAsset = "USDT",
                            status = "TRADING"
                        )
                    )
                    marketAnalysisService.backfillHistory(symbolStr)
                    newSymbols.add(symbolStr)
                } else if (existing.status != "TRADING") {
                    logger.info("Enabling existing symbol: $symbolStr")
                    existing.status = "TRADING"
                    symbolRepository.save(existing)
                    newSymbols.add(symbolStr)
                }
            }

            logger.info("Sync completed. Top 100 processed.")

            if (newSymbols.isNotEmpty()) {
                logger.info("Subscribing to ${newSymbols.size} new symbols")
                binanceWebSocketClient.subscribe(newSymbols)
            }
            logger.info("Symbol Sync completed: Top 100 processed, ${newSymbols.size} new symbols added to tracking.")
        } catch (e: Exception) {
            logger.error("Error during symbol sync", e)
        }
    }
}
