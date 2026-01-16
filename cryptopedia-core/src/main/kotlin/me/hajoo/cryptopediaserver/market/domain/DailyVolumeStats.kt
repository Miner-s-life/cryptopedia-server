package me.hajoo.cryptopediaserver.market.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(
    name = "daily_volume_stats",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_stats_ex_sy_date", columnNames = ["exchange", "symbol", "date"])
    ]
)
class DailyVolumeStats(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 20)
    val exchange: String,

    @Column(nullable = false, length = 20)
    val symbol: String,

    @Column(nullable = false)
    val date: LocalDate,

    @Column(nullable = false, precision = 18, scale = 8)
    val volumeSum: BigDecimal,

    @Column(nullable = false, precision = 18, scale = 8)
    val quoteVolumeSum: BigDecimal,
    
    @Column(nullable = true, precision = 18, scale = 8)
    val volumeMa7d: BigDecimal? = null, // Moving Average 7 Days

    @Column(nullable = true, precision = 18, scale = 8)
    val volumeMa30d: BigDecimal? = null
)
