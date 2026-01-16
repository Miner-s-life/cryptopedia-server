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
    val id: Long? = null,

    @Column(nullable = false, length = 20)
    val exchange: String,

    @Column(nullable = false, length = 20)
    val symbol: String,

    @Column(nullable = false, precision = 32, scale = 8)
    var rvol: BigDecimal, // Relative Volume (Current / Average)

    @Column(nullable = false, precision = 32, scale = 8)
    var priceChangePercent24h: BigDecimal,
    
    @Column(nullable = false)
    var isSurging: Boolean = false, // Simple flag for high RVOL + Price Up

    @Column(nullable = false)
    var lastUpdated: LocalDateTime = LocalDateTime.now()
)
