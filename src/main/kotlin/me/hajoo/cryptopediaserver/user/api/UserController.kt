package me.hajoo.cryptopediaserver.user.api

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
    fun getMe(authentication: Authentication): ResponseEntity<MeResponse> {
        val userId = authentication.principal as Long
        val response = userService.getMe(userId)
        return ResponseEntity.ok(response)
    }
}
