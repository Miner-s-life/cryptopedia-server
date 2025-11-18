package me.hajoo.cryptopediaserver.auth.api

import me.hajoo.cryptopediaserver.auth.application.AuthService
import me.hajoo.cryptopediaserver.auth.application.AuthTokens
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
) {

    @PostMapping("/signup")
    fun signup(@RequestBody request: SignupRequest): ResponseEntity<SignupResponse> {
        val userId = authService.signup(
            email = request.email,
            rawPassword = request.password,
            nickname = request.nickname,
        )
        return ResponseEntity.ok(SignupResponse(userId = userId))
    }

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<AuthTokens> {
        val tokens = authService.login(
            email = request.email,
            rawPassword = request.password,
        )
        return ResponseEntity.ok(tokens)
    }

    @PostMapping("/token/refresh")
    fun refresh(@RequestBody request: TokenRefreshRequest): ResponseEntity<TokenRefreshResponse> {
        val tokens = authService.refresh(request.refreshToken)
        return ResponseEntity.ok(TokenRefreshResponse(accessToken = tokens.accessToken, refreshToken = tokens.refreshToken))
    }

    @PostMapping("/logout")
    fun logout(authentication: Authentication): ResponseEntity<Unit> {
        val userId = authentication.principal as Long
        authService.logout(userId)
        return ResponseEntity.ok().build()
    }
}

data class SignupRequest(
    val email: String,
    val password: String,
    val nickname: String,
)

data class SignupResponse(
    val userId: Long,
)

data class LoginRequest(
    val email: String,
    val password: String,
)

data class TokenRefreshRequest(
    val refreshToken: String,
)

data class TokenRefreshResponse(
    val accessToken: String,
    val refreshToken: String,
)
