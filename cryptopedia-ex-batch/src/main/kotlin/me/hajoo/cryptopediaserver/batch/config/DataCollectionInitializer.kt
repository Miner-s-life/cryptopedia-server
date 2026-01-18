package me.hajoo.cryptopediaserver.batch.config

import me.hajoo.cryptopediaserver.batch.adapter.`in`.binance.BinanceWebSocketClient
import me.hajoo.cryptopediaserver.batch.application.service.MarketAnalysisService
import me.hajoo.cryptopediaserver.batch.application.service.SymbolSyncService
import me.hajoo.cryptopediaserver.core.domain.Symbol
import me.hajoo.cryptopediaserver.core.domain.SymbolRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class DataCollectionInitializer(
    private val symbolRepository: SymbolRepository,
    private val binanceWebSocketClient: BinanceWebSocketClient,
    private val symbolSyncService: SymbolSyncService,
    private val marketAnalysisService: MarketAnalysisService
) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String?) {
        logger.info("Running DataCollectionInitializer...")

        // 1. Run initial sync
        symbolSyncService.syncTopVolumeSymbols()

        // 2. Backfill missing candles before starting WebSocket
        val tradingSymbols = symbolRepository.findAllByStatus("TRADING")
        marketAnalysisService.backfillMissingCandles(tradingSymbols)

        // 3. Connect WebSocket for ALL active symbols
        val activeSymbols = tradingSymbols.map { it.symbol }
        
        logger.info("Active symbols to subscribe: ${activeSymbols.size}")
        binanceWebSocketClient.connect(activeSymbols)
    }
}
