package me.hajoo.cryptopediaserver.auth.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import me.hajoo.cryptopediaserver.common.domain.BaseTimeEntity
import me.hajoo.cryptopediaserver.user.domain.User

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false, unique = true, length = 512)
    val token: String,

    @Column(nullable = false)
    val expiredAt: LocalDateTime,

    @Column(nullable = false)
    var revoked: Boolean = false,
) : BaseTimeEntity()
