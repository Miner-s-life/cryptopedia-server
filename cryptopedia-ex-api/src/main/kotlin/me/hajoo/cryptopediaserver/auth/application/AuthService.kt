package me.hajoo.cryptopediaserver.auth.application

import java.time.LocalDateTime
import me.hajoo.cryptopediaserver.auth.application.AuthTokens
import me.hajoo.cryptopediaserver.core.common.exception.BusinessException
import me.hajoo.cryptopediaserver.core.common.exception.ErrorCode
import me.hajoo.cryptopediaserver.core.domain.RefreshToken
import me.hajoo.cryptopediaserver.core.domain.RefreshTokenRepository
import me.hajoo.cryptopediaserver.core.security.JwtTokenProvider
import me.hajoo.cryptopediaserver.core.domain.User
import me.hajoo.cryptopediaserver.core.domain.UserRepository
import me.hajoo.cryptopediaserver.core.domain.UserRole
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
) {

    @Transactional
    fun signup(email: String, rawPassword: String, nickname: String): Long {
        if (userRepository.existsByEmail(email)) {
            throw BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS)
        }
        if (userRepository.existsByNickname(nickname)) {
            throw BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS)
        }

        val encodedPassword = passwordEncoder.encode(rawPassword)
        val user = User(
            email = email,
            password = encodedPassword,
            nickname = nickname,
            role = UserRole.USER,
        )
        return userRepository.save(user).id
    }

    @Transactional
    fun login(email: String, rawPassword: String): AuthTokens {
        val user = userRepository.findByEmail(email)
            ?: throw BusinessException(ErrorCode.INVALID_CREDENTIALS)

        if (!passwordEncoder.matches(rawPassword, user.password)) {
            throw BusinessException(ErrorCode.INVALID_CREDENTIALS)
        }

        refreshTokenRepository.deleteByUser(user)

        val accessToken = jwtTokenProvider.generateAccessToken(user)
        val refreshTokenValue = jwtTokenProvider.generateRefreshToken(user)
        val now = LocalDateTime.now()
        val refreshToken = RefreshToken(
            user = user,
            token = refreshTokenValue,
            expiredAt = now.plusSeconds(60L * 60L * 24L * 14L),
        )
        refreshTokenRepository.save(refreshToken)

        user.updateLoginAt(now)

        return AuthTokens(accessToken = accessToken, refreshToken = refreshTokenValue)
    }

    @Transactional
    fun refresh(refreshToken: String): AuthTokens {
        val stored = refreshTokenRepository.findByToken(refreshToken)
            ?: throw BusinessException(ErrorCode.INVALID_CREDENTIALS)

        if (stored.revoked || stored.expiredAt.isBefore(LocalDateTime.now())) {
            throw BusinessException(ErrorCode.INVALID_CREDENTIALS)
        }

        val user = stored.user
        val newAccessToken = jwtTokenProvider.generateAccessToken(user)

        return AuthTokens(accessToken = newAccessToken, refreshToken = refreshToken)
    }

    @Transactional
    fun logout(userId: Long) {
        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(ErrorCode.ENTITY_NOT_FOUND) }

        refreshTokenRepository.deleteByUser(user)
    }
}

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
)
