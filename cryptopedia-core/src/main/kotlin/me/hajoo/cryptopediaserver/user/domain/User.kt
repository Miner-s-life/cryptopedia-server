package me.hajoo.cryptopediaserver.user.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import me.hajoo.cryptopediaserver.common.domain.BaseTimeEntity

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 255)
    val email: String,

    @Column(nullable = false, length = 255)
    var password: String,

    @Column(nullable = false, unique = true, length = 50)
    var nickname: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val role: UserRole = UserRole.USER,

    @Column
    var lastLoginAt: LocalDateTime? = null,
) : BaseTimeEntity() {
    fun updateLoginAt(now: LocalDateTime = LocalDateTime.now()) {
        this.lastLoginAt = now
        this.touch(now)
    }
}

enum class UserRole {
    USER,
    ADMIN,
}
