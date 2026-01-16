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

    @Column(name = "rvol", nullable = false, precision = 32, scale = 8)
    var rvol: BigDecimal, // Relative Volume (Current / Average)

    @Column(name = "price_change_percent24h", nullable = false, precision = 32, scale = 8)
    var priceChangePercent24h: BigDecimal,
    
    @Column(name = "is_surging", nullable = false)
    var isSurging: Boolean = false, // Simple flag for high RVOL + Price Up

    @Column(name = "last_updated", nullable = false)
    var lastUpdated: LocalDateTime = LocalDateTime.now()
)
