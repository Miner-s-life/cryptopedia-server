package me.hajoo.cryptopediaserver.core.domain

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
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "exchange", nullable = false, length = 20)
    val exchange: String,

    @Column(name = "symbol", nullable = false, length = 20)
    val symbol: String,

    @Column(name = "open_time", nullable = false)
    val openTime: LocalDateTime,

    @Column(name = "open_price", nullable = false, precision = 32, scale = 8)
    val openPrice: BigDecimal,

    @Column(name = "high_price", nullable = false, precision = 32, scale = 8)
    val highPrice: BigDecimal,

    @Column(name = "low_price", nullable = false, precision = 32, scale = 8)
    val lowPrice: BigDecimal,

    @Column(name = "close_price", nullable = false, precision = 32, scale = 8)
    val closePrice: BigDecimal,

    @Column(name = "volume", nullable = false, precision = 32, scale = 8)
    val volume: BigDecimal, // Base Asset Volume

    @Column(name = "quote_volume", nullable = false, precision = 32, scale = 8)
    val quoteVolume: BigDecimal, // Quote Asset Volume

    @Column(name = "trades", nullable = false)
    val trades: Long
)
