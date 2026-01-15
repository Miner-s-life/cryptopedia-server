package me.hajoo.cryptopediaserver.market.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface Candle1mRepository : JpaRepository<Candle1m, Long> {
    fun findByExchangeAndSymbolAndOpenTime(exchange: String, symbol: String, openTime: java.time.LocalDateTime): Candle1m?
}
