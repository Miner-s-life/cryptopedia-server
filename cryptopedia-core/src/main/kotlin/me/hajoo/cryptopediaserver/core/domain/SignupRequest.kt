package me.hajoo.cryptopediaserver.core.domain

import jakarta.persistence.*

@Entity
@Table(name = "signup_requests")
class SignupRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "email", nullable = false, unique = true, length = 255)
    val email: String,

    @Column(name = "password", nullable = false, length = 255)
    val password: String,

    @Column(name = "phone_number", length = 20)
    val phoneNumber: String? = null,

    @Column(name = "comment", columnDefinition = "TEXT")
    val comment: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: SignupStatus = SignupStatus.PENDING,
) : BaseTimeEntity()

enum class SignupStatus {
    PENDING,
    APPROVED,
    REJECTED
}
