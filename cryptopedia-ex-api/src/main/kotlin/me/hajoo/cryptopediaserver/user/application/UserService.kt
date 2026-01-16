package me.hajoo.cryptopediaserver.user.application

import me.hajoo.cryptopediaserver.core.common.exception.BusinessException
import me.hajoo.cryptopediaserver.core.common.exception.ErrorCode
import me.hajoo.cryptopediaserver.core.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    fun getMe(userId: Long): MeResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { BusinessException(ErrorCode.ENTITY_NOT_FOUND) }

        return MeResponse(
            id = user.id,
            email = user.email,
            nickname = user.nickname,
            createdAt = user.createdAt,
            lastLoginAt = user.lastLoginAt,
        )
    }
}

data class MeResponse(
    val id: Long,
    val email: String,
    val nickname: String,
    val createdAt: java.time.LocalDateTime,
    val lastLoginAt: java.time.LocalDateTime?,
)
