package me.hajoo.cryptopediaserver.core.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SymbolRepository : JpaRepository<Symbol, Long> {
    fun findByExchangeAndSymbol(exchange: String, symbol: String): Symbol?
    fun findAllByStatus(status: String): List<Symbol>
    fun findAllByExchange(exchange: String): List<Symbol>
}
