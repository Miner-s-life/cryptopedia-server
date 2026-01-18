package me.hajoo.cryptopediaserver.core.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
interface Ticker24hLatestRepository : JpaRepository<Ticker24hLatest, Long> {
    fun findByExchangeAndSymbol(exchange: String, symbol: String): Ticker24hLatest?

    @Modifying
    @Query(value = """
        INSERT INTO ticker_24h_latest (exchange, symbol, last_price, price_change_percent, volume24h, quote_volume24h, last_updated)
        VALUES (:exchange, :symbol, :lastPrice, :priceChangePercent, :volume24h, :quoteVolume24h, :lastUpdated)
        ON DUPLICATE KEY UPDATE
            last_price = VALUES(last_price),
            price_change_percent = VALUES(price_change_percent),
            volume24h = VALUES(volume24h),
            quote_volume24h = VALUES(quote_volume24h),
            last_updated = VALUES(last_updated)
    """, nativeQuery = true)
    fun upsert(
        @Param("exchange") exchange: String,
        @Param("symbol") symbol: String,
        @Param("lastPrice") lastPrice: BigDecimal,
        @Param("priceChangePercent") priceChangePercent: BigDecimal,
        @Param("volume24h") volume24h: BigDecimal,
        @Param("quoteVolume24h") quoteVolume24h: BigDecimal,
        @Param("lastUpdated") lastUpdated: LocalDateTime
    )
}
