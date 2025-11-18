package me.hajoo.cryptopediaserver.config.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.hajoo.cryptopediaserver.user.domain.UserRole
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val jwt = resolveToken(request)
        if (jwt != null && jwtTokenProvider.validateToken(jwt)) {
            val claims = jwtTokenProvider.parseClaims(jwt)
            val userId = jwtTokenProvider.getUserId(claims)
            val role = jwtTokenProvider.getUserRole(claims) ?: UserRole.USER

            if (SecurityContextHolder.getContext().authentication == null) {
                val authorities = listOf(SimpleGrantedAuthority("ROLE_${'$'}{role.name}"))
                val authentication = UsernamePasswordAuthenticationToken(userId, null, authorities)
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authentication
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
        return if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7)
        } else {
            null
        }
    }
}
