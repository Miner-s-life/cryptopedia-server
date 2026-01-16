package me.hajoo.cryptopediaserver.core.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "ticker_24h_latest",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_ticker_ex_symbol", columnNames = ["exchange", "symbol"])
    ]
)
class Ticker24hLatest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "exchange", nullable = false, length = 20)
    val exchange: String,

    @Column(name = "symbol", nullable = false, length = 20)
    val symbol: String,

    @Column(name = "last_price", nullable = false, precision = 32, scale = 8)
    var lastPrice: BigDecimal,

    @Column(name = "price_change_percent", nullable = false, precision = 32, scale = 8)
    var priceChangePercent: BigDecimal,

    @Column(name = "volume24h", nullable = false, precision = 32, scale = 8)
    var volume24h: BigDecimal,

    @Column(name = "quote_volume24h", nullable = false, precision = 32, scale = 8)
    var quoteVolume24h: BigDecimal,

    @Column(name = "last_updated", nullable = false)
    var lastUpdated: LocalDateTime = LocalDateTime.now()
)
