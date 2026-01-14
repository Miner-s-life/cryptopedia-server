package me.hajoo.cryptopediaserver.user.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import me.hajoo.cryptopediaserver.user.application.MeResponse
import me.hajoo.cryptopediaserver.user.application.UserService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/users")
class UserController(
    private val userService: UserService,
) {

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 기본 프로필 정보를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    fun getMe(authentication: Authentication): ResponseEntity<MeResponse> {
        val userId = authentication.principal as Long
        val response = userService.getMe(userId)
        return ResponseEntity.ok(response)
    }
}
