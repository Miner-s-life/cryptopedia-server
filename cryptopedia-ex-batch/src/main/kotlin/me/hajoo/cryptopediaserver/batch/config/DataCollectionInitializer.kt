package me.hajoo.cryptopediaserver.batch.config

import me.hajoo.cryptopediaserver.batch.adapter.`in`.binance.BinanceWebSocketClient
import me.hajoo.cryptopediaserver.core.domain.Symbol
import me.hajoo.cryptopediaserver.core.domain.SymbolRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class DataCollectionInitializer(
    private val symbolRepository: SymbolRepository,
    private val binanceWebSocketClient: BinanceWebSocketClient,
    private val symbolSyncService: me.hajoo.cryptopediaserver.batch.application.service.SymbolSyncService
) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String?) {
        logger.info("Running DataCollectionInitializer...")

        // 1. Run initial sync
        symbolSyncService.syncTopVolumeSymbols()

        // 2. Connect WebSocket for ALL active symbols (including newly synced ones)
        val allSymbolsCount = symbolRepository.count()
        val activeSymbols = symbolRepository.findAllByStatus("TRADING").map { it.symbol }
        
        logger.info("Database total symbols: $allSymbolsCount, Active symbols: ${activeSymbols.size}")

        if (activeSymbols.isNotEmpty()) {
            logger.info("Connecting WebSocket for ${activeSymbols.size} symbols")
            binanceWebSocketClient.connect(activeSymbols)
        } else {
            logger.warn("No active symbols found to collect.")
        }
    }
}
