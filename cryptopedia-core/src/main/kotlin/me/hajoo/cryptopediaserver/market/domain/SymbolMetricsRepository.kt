package me.hajoo.cryptopediaserver.market.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SymbolMetricsRepository : JpaRepository<SymbolMetrics, Long> {
    fun findByExchangeAndSymbol(exchange: String, symbol: String): SymbolMetrics?
}
