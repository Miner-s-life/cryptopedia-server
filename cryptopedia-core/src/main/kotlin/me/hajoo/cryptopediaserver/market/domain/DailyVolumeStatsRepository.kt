package me.hajoo.cryptopediaserver.market.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface DailyVolumeStatsRepository : JpaRepository<DailyVolumeStats, Long> {
    fun findByExchangeAndSymbolAndDate(exchange: String, symbol: String, date: LocalDate): DailyVolumeStats?
    
    // For MA calculation: get last N records before specific date
    fun findTop30ByExchangeAndSymbolAndDateLessThanOrderByDateDesc(exchange: String, symbol: String, date: LocalDate): List<DailyVolumeStats>
}
