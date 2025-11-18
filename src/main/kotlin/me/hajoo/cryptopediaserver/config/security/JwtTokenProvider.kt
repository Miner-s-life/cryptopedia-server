package me.hajoo.cryptopediaserver.config.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import java.security.Key
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import me.hajoo.cryptopediaserver.user.domain.User
import me.hajoo.cryptopediaserver.user.domain.UserRole
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class JwtTokenProvider(
    @Value("\${jwt.secret}") secret: String,
    @Value("\${jwt.access-token-validity-in-seconds}")
    private val accessTokenValidityInSeconds: Long,
    @Value("\${jwt.refresh-token-validity-in-seconds}")
    private val refreshTokenValidityInSeconds: Long,
) {
    private val key: Key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))

    fun generateAccessToken(user: User): String {
        val now = Instant.now()
        val expiry = now.plus(accessTokenValidityInSeconds, ChronoUnit.SECONDS)

        return Jwts.builder()
            .setSubject(user.id.toString())
            .claim("email", user.email)
            .claim("role", user.role.name)
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(expiry))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    fun generateRefreshToken(user: User): String {
        val now = Instant.now()
        val expiry = now.plus(refreshTokenValidityInSeconds, ChronoUnit.SECONDS)

        return Jwts.builder()
            .setSubject(user.id.toString())
            .claim("type", "refresh")
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(expiry))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    fun getUserId(claims: Claims): Long = claims.subject.toLong()

    fun getUserRole(claims: Claims): UserRole? =
        (claims["role"] as? String)?.let { UserRole.valueOf(it) }

    fun parseClaims(token: String): Claims {
        return Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .body
    }

    fun validateToken(token: String): Boolean =
        try {
            val claims = parseClaims(token)
            claims.expiration.after(Date())
        } catch (ex: Exception) {
            false
        }
}
