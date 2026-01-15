package me.hajoo.cryptopediaserver.market.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "candles_1m",
    indexes = [
        Index(name = "idx_ex_sy_ot", columnList = "exchange, symbol, openTime", unique = true)
    ]
)
class Candle1m(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 20)
    val exchange: String,

    @Column(nullable = false, length = 20)
    val symbol: String,

    @Column(nullable = false)
    val openTime: LocalDateTime,

    @Column(nullable = false, precision = 18, scale = 8)
    val openPrice: BigDecimal,

    @Column(nullable = false, precision = 18, scale = 8)
    val highPrice: BigDecimal,

    @Column(nullable = false, precision = 18, scale = 8)
    val lowPrice: BigDecimal,

    @Column(nullable = false, precision = 18, scale = 8)
    val closePrice: BigDecimal,

    @Column(nullable = false, precision = 18, scale = 8)
    val volume: BigDecimal, // Base Asset Volume

    @Column(nullable = false, precision = 18, scale = 8)
    val quoteVolume: BigDecimal, // Quote Asset Volume

    @Column(nullable = false)
    val trades: Long
)
