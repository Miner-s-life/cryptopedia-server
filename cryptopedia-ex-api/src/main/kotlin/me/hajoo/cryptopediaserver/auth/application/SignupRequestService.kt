package me.hajoo.cryptopediaserver.auth.application

import me.hajoo.cryptopediaserver.core.common.exception.BusinessException
import me.hajoo.cryptopediaserver.core.common.exception.ErrorCode
import me.hajoo.cryptopediaserver.core.domain.SignupRequest
import me.hajoo.cryptopediaserver.core.domain.SignupRequestRepository
import me.hajoo.cryptopediaserver.core.domain.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SignupRequestService(
    private val signupRequestRepository: SignupRequestRepository,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    @Transactional
    fun createSignupRequest(
        email: String,
        rawPassword: String,
        phoneNumber: String?,
        comment: String?,
    ): Long {
        if (userRepository.existsByEmail(email)) {
            throw BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS)
        }

        if (signupRequestRepository.existsByEmail(email)) {
            throw BusinessException(ErrorCode.SIGNUP_REQUEST_ALREADY_EXISTS)
        }

        val signupRequest = SignupRequest(
            email = email,
            password = passwordEncoder.encode(rawPassword),
            phoneNumber = phoneNumber,
            comment = comment,
        )

        return signupRequestRepository.save(signupRequest).id
    }
}
