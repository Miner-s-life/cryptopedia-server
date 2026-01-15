package me.hajoo.cryptopediaserver.market.domain

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
    val id: Long? = null,

    @Column(nullable = false, length = 20)
    val exchange: String,

    @Column(nullable = false, length = 20)
    val symbol: String,

    @Column(nullable = false, precision = 18, scale = 8)
    var lastPrice: BigDecimal,

    @Column(nullable = false, precision = 10, scale = 4)
    var priceChangePercent: BigDecimal,

    @Column(nullable = false, precision = 18, scale = 8)
    var volume24h: BigDecimal,

    @Column(nullable = false, precision = 18, scale = 8)
    var quoteVolume24h: BigDecimal,

    @Column(nullable = false)
    var lastUpdated: LocalDateTime = LocalDateTime.now()
)
