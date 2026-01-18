package me.hajoo.cryptopediaserver.core.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
interface Candle1mRepository : JpaRepository<Candle1m, Long> {
    fun findByExchangeAndSymbolAndOpenTime(exchange: String, symbol: String, openTime: LocalDateTime): Candle1m?

    @Query("SELECT SUM(c.volume) FROM Candle1m c WHERE c.exchange = :exchange AND c.symbol = :symbol AND c.openTime >= :start AND c.openTime < :end")
    fun getVolumeSum(exchange: String, symbol: String, start: LocalDateTime, end: LocalDateTime): BigDecimal?

    fun findTop100ByExchangeAndSymbolOrderByOpenTimeDesc(exchange: String, symbol: String): List<Candle1m>

    @Query("SELECT c.openTime FROM Candle1m c WHERE c.exchange = :exchange AND c.symbol = :symbol ORDER BY c.openTime DESC LIMIT 1")
    fun findLatestOpenTime(exchange: String, symbol: String): LocalDateTime?

    fun findFirstByExchangeAndSymbolAndOpenTimeGreaterThanEqualOrderByOpenTimeAsc(
        exchange: String, symbol: String, start: LocalDateTime
    ): Candle1m?

    fun existsByExchangeAndSymbolAndOpenTime(exchange: String, symbol: String, openTime: LocalDateTime): Boolean

    @Modifying
    @Query(value = """
        INSERT INTO candles_1m (exchange, symbol, open_time, open_price, high_price, low_price, close_price, volume, quote_volume, trades)
        VALUES (:exchange, :symbol, :openTime, :openPrice, :highPrice, :lowPrice, :closePrice, :volume, :quoteVolume, :trades)
        ON DUPLICATE KEY UPDATE
            open_price = VALUES(open_price),
            high_price = VALUES(high_price),
            low_price = VALUES(low_price),
            close_price = VALUES(close_price),
            volume = VALUES(volume),
            quote_volume = VALUES(quote_volume),
            trades = VALUES(trades)
    """, nativeQuery = true)
    fun upsert(
        @Param("exchange") exchange: String,
        @Param("symbol") symbol: String,
        @Param("openTime") openTime: LocalDateTime,
        @Param("openPrice") openPrice: BigDecimal,
        @Param("highPrice") highPrice: BigDecimal,
        @Param("lowPrice") lowPrice: BigDecimal,
        @Param("closePrice") closePrice: BigDecimal,
        @Param("volume") volume: BigDecimal,
        @Param("quoteVolume") quoteVolume: BigDecimal,
        @Param("trades") trades: Long
    )
}
