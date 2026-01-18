package me.hajoo.cryptopediaserver.api.market

import me.hajoo.cryptopediaserver.api.market.dto.CandleResponse
import me.hajoo.cryptopediaserver.api.market.dto.SymbolResponse
import me.hajoo.cryptopediaserver.core.market.dto.TickerWithMetricsResponse
import me.hajoo.cryptopediaserver.core.domain.Candle1mRepository
import me.hajoo.cryptopediaserver.core.domain.SymbolMetricsRepository
import me.hajoo.cryptopediaserver.core.domain.SymbolRepository
import me.hajoo.cryptopediaserver.core.domain.Ticker24hLatestRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MarketService(
    private val symbolRepository: SymbolRepository,
    private val ticker24hLatestRepository: Ticker24hLatestRepository,
    private val symbolMetricsRepository: SymbolMetricsRepository,
    private val candle1mRepository: Candle1mRepository
) {

    fun getActiveSymbols(): List<SymbolResponse> {
        return symbolRepository.findAllByStatus("TRADING").map {
            SymbolResponse(
                exchange = it.exchange,
                symbol = it.symbol,
                baseAsset = it.baseAsset,
                quoteAsset = it.quoteAsset,
                status = it.status
            )
        }
    }

    fun getTickersWithMetrics(): List<TickerWithMetricsResponse> {
        val tickers = ticker24hLatestRepository.findAll()
        val metricsMap = symbolMetricsRepository.findAll().associateBy { "${it.exchange}:${it.symbol}" }

        val currentTickers = tickers.map { ticker ->
            val metrics = metricsMap["${ticker.exchange}:${ticker.symbol}"]
            TickerWithMetricsResponse(
                exchange = ticker.exchange,
                symbol = ticker.symbol,
                lastPrice = ticker.lastPrice,
                priceChangePercent = ticker.priceChangePercent,
                volume24h = ticker.volume24h,
                quoteVolume24h = ticker.quoteVolume24h,
                rvol1m = metrics?.rvol1m ?: java.math.BigDecimal.ZERO,
                rvol5m = metrics?.rvol5m ?: java.math.BigDecimal.ZERO,
                rvol15m = metrics?.rvol15m ?: java.math.BigDecimal.ZERO,
                rvol30m = metrics?.rvol30m ?: java.math.BigDecimal.ZERO,
                rvol1h = metrics?.rvol1h ?: java.math.BigDecimal.ZERO,
                rvol4h = metrics?.rvol4h ?: java.math.BigDecimal.ZERO,
                rvolToday = metrics?.rvolToday ?: java.math.BigDecimal.ZERO,
                priceChangePercentToday = metrics?.priceChangePercentToday ?: java.math.BigDecimal.ZERO,
                isSurging = metrics?.isSurging ?: false,
                lastUpdated = ticker.lastUpdated
            )
        }
        return currentTickers
    }

    fun getCandles(exchange: String, symbol: String, limit: Int = 100): List<CandleResponse> {
        // Assuming findTopNByExchangeAndSymbolOrderByOpenTimeDesc exists or similar
        // For simplicity, let's assume we fetch all and take last or use a proper repo method
        // I'll check Candle1mRepository.kt again for query methods
        return candle1mRepository.findTop100ByExchangeAndSymbolOrderByOpenTimeDesc(exchange, symbol).map {
            CandleResponse(
                openTime = it.openTime,
                open = it.openPrice,
                high = it.highPrice,
                low = it.lowPrice,
                close = it.closePrice,
                volume = it.volume,
                quoteVolume = it.quoteVolume,
                takerBuyQuoteVolume = it.takerBuyQuoteVolume,
                trades = it.trades
            )
        }.reversed()
    }
}
