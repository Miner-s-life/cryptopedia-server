package me.hajoo.cryptopediaserver.core.domain

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
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "exchange", nullable = false, length = 20)
    val exchange: String,

    @Column(name = "symbol", nullable = false, length = 20)
    val symbol: String,

    @Column(name = "date", nullable = false)
    val date: LocalDate,

    @Column(name = "volume_sum", nullable = false, precision = 32, scale = 8)
    val volumeSum: BigDecimal,

    @Column(name = "quote_volume_sum", nullable = false, precision = 32, scale = 8)
    val quoteVolumeSum: BigDecimal,
    
    @Column(name = "quote_volume_ma_7d", nullable = true, precision = 32, scale = 8)
    val quoteVolumeMa7d: BigDecimal? = null,

    @Column(name = "quote_volume_ma_30d", nullable = true, precision = 32, scale = 8)
    val quoteVolumeMa30d: BigDecimal? = null
)
