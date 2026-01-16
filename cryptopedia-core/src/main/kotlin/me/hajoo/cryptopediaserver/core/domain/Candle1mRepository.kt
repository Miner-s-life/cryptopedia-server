package me.hajoo.cryptopediaserver.core.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
interface Candle1mRepository : JpaRepository<Candle1m, Long> {
    fun findByExchangeAndSymbolAndOpenTime(exchange: String, symbol: String, openTime: LocalDateTime): Candle1m?

    @Query("SELECT SUM(c.volume) FROM Candle1m c WHERE c.exchange = :exchange AND c.symbol = :symbol AND c.openTime >= :start AND c.openTime < :end")
    fun getVolumeSum(exchange: String, symbol: String, start: LocalDateTime, end: LocalDateTime): BigDecimal?

    fun findTop100ByExchangeAndSymbolOrderByOpenTimeDesc(exchange: String, symbol: String): List<Candle1m>
}
