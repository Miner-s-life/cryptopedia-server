package me.hajoo.cryptopediaserver.auth.domain

import me.hajoo.cryptopediaserver.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByToken(token: String): RefreshToken?
    fun deleteByUser(user: User)
}
