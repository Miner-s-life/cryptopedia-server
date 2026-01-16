package me.hajoo.cryptopediaserver.core.domain

import org.springframework.data.jpa.repository.JpaRepository

interface SignupRequestRepository : JpaRepository<SignupRequest, Long> {
    fun findByEmail(email: String): SignupRequest?
    fun existsByEmail(email: String): Boolean
}
