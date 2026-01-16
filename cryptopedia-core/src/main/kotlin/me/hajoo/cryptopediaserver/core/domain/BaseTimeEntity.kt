package me.hajoo.cryptopediaserver.core.domain

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import java.time.LocalDateTime

@MappedSuperclass
abstract class BaseTimeEntity(
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    fun touch(now: LocalDateTime = LocalDateTime.now()) {
        this.updatedAt = now
    }
}
