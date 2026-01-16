package me.hajoo.cryptopediaserver.core.domain

import jakarta.persistence.*
import me.hajoo.cryptopediaserver.core.domain.BaseTimeEntity

@Entity
@Table(
    name = "symbols",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_exchange_symbol", columnNames = ["exchange", "symbol"])
    ]
)
class Symbol(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 20)
    val exchange: String, // BINANCE

    @Column(nullable = false, length = 20)
    val symbol: String, // BTCUSDT

    @Column(nullable = false, length = 10)
    val baseAsset: String, // BTC

    @Column(nullable = false, length = 10)
    val quoteAsset: String, // USDT

    @Column(nullable = false, length = 20)
    var status: String, // TRADING, BREAK

) : BaseTimeEntity()
