package me.hajoo.cryptopediaserver.core.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "symbol_metrics",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_metrics_ex_sy", columnNames = ["exchange", "symbol"])
    ]
)
class SymbolMetrics(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "exchange", nullable = false, length = 20)
    val exchange: String,

    @Column(name = "symbol", nullable = false, length = 20)
    val symbol: String,

    // Multi-Timeframe RVOL
    @Column(name = "rvol_1m", nullable = false, precision = 32, scale = 8)
    var rvol1m: BigDecimal = BigDecimal.ZERO,

    @Column(name = "rvol_5m", nullable = false, precision = 32, scale = 8)
    var rvol5m: BigDecimal = BigDecimal.ZERO,

    @Column(name = "rvol_15m", nullable = false, precision = 32, scale = 8)
    var rvol15m: BigDecimal = BigDecimal.ZERO,

    @Column(name = "rvol_30m", nullable = false, precision = 32, scale = 8)
    var rvol30m: BigDecimal = BigDecimal.ZERO,

    @Column(name = "rvol_1h", nullable = false, precision = 32, scale = 8)
    var rvol1h: BigDecimal = BigDecimal.ZERO,

    @Column(name = "rvol_4h", nullable = false, precision = 32, scale = 8)
    var rvol4h: BigDecimal = BigDecimal.ZERO,

    @Column(name = "rvol_today", nullable = false, precision = 32, scale = 8)
    var rvolToday: BigDecimal = BigDecimal.ZERO,

    @Column(name = "price_change_percent24h", nullable = false, precision = 32, scale = 8)
    var priceChangePercent24h: BigDecimal = BigDecimal.ZERO,

    @Column(name = "price_change_percent_today", nullable = false, precision = 32, scale = 8)
    var priceChangePercentToday: BigDecimal = BigDecimal.ZERO,
    
    @Column(name = "is_surging", nullable = false)
    var isSurging: Boolean = false, // Simple flag for high RVOL + Price Up

    @Column(name = "last_updated", nullable = false)
    var lastUpdated: LocalDateTime = LocalDateTime.now()
)
