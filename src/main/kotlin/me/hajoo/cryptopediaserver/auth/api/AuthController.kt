package me.hajoo.cryptopediaserver.auth.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
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
    @Operation(summary = "회원 가입", description = "이메일, 비밀번호, 닉네임으로 신규 회원을 생성합니다.")
    fun signup(@RequestBody request: SignupRequest): ResponseEntity<SignupResponse> {
        val userId = authService.signup(
            email = request.email,
            rawPassword = request.password,
            nickname = request.nickname,
        )
        return ResponseEntity.ok(SignupResponse(userId = userId))
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인하고 access/refresh 토큰을 발급받습니다.")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<AuthTokens> {
        val tokens = authService.login(
            email = request.email,
            rawPassword = request.password,
        )
        return ResponseEntity.ok(tokens)
    }

    @PostMapping("/token/refresh")
    @Operation(summary = "액세스 토큰 재발급", description = "유효한 refresh 토큰으로 새로운 access 토큰을 발급받습니다.")
    fun refresh(@RequestBody request: TokenRefreshRequest): ResponseEntity<TokenRefreshResponse> {
        val tokens = authService.refresh(request.refreshToken)
        return ResponseEntity.ok(TokenRefreshResponse(accessToken = tokens.accessToken, refreshToken = tokens.refreshToken))
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "현재 인증된 사용자의 모든 refresh 토큰을 제거하여 로그아웃 처리합니다.")
    @SecurityRequirement(name = "bearerAuth")
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
