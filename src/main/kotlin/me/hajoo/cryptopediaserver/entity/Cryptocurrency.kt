package me.hajoo.cryptopediaserver.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "cryptocurrencies")
data class Cryptocurrency(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(nullable = false, unique = true, length = 10)
    val symbol: String,
    
    @Column(nullable = false, length = 100)
    val name: String,
    
    @Column(precision = 20, scale = 8)
    val currentPrice: BigDecimal? = null,
    
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
