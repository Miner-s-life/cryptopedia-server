package me.hajoo.cryptopediaserver.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0,

    @Column(name = "email", nullable = false, unique = true, length = 255)
    val email: String,

    @Column(name = "password", nullable = false, length = 255)
    var password: String,

    @Column(name = "nickname", nullable = false, unique = true, length = 50)
    var nickname: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    val role: UserRole = UserRole.USER,

    @Column(name = "last_login_at")
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
