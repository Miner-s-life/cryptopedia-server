package me.hajoo.cryptopediaserver.core.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface Ticker24hLatestRepository : JpaRepository<Ticker24hLatest, Long> {
    fun findByExchangeAndSymbol(exchange: String, symbol: String): Ticker24hLatest?
}
