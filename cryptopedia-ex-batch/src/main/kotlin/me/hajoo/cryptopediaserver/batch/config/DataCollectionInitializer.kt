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
    private val marketAnalysisService: me.hajoo.cryptopediaserver.batch.application.service.MarketAnalysisService
) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String?) {
        logger.info("Starting Data Collection Initializer...")

        val targetSymbols = listOf("BTCUSDT", "ETHUSDT", "XRPUSDT", "SOLUSDT", "BNBUSDT", "DOGEUSDT")
        
        targetSymbols.forEach { symbolStr ->
            if (symbolRepository.findByExchangeAndSymbol("BINANCE", symbolStr) == null) {
                logger.info("Seeding symbol: $symbolStr")
                symbolRepository.save(
                    Symbol(
                        exchange = "BINANCE",
                        symbol = symbolStr,
                        baseAsset = symbolStr.replace("USDT", ""),
                        quoteAsset = "USDT",
                        status = "TRADING"
                    )
                )
                // Trigger Backfill for new symbol
                marketAnalysisService.backfillHistory(symbolStr)
            } else {
                 // Even if exists, check if backfill needed? 
                 // The service checks inside backfillHistory if data exists.
                 marketAnalysisService.backfillHistory(symbolStr)
            }
        }

        val activeSymbols = symbolRepository.findAllByStatus("TRADING").map { it.symbol }
        if (activeSymbols.isNotEmpty()) {
            logger.info("Connecting WebSocket for ${activeSymbols.size} symbols: $activeSymbols")
            binanceWebSocketClient.connect(activeSymbols)
        } else {
            logger.warn("No active symbols found to collect.")
        }
    }
}
