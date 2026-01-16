package me.hajoo.cryptopediaserver.core.common.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val code: String,
    val message: String
) {
    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "Please check your input values."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "This method is not supported."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C003", "An unexpected error occurred. Please try again later."),
    ENTITY_NOT_FOUND(HttpStatus.BAD_REQUEST, "C004", "The requested information could not be found."),

    // Auth & User
    EMAIL_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "A001", "This email is already registered."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "A002", "This nickname is already taken."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A003", "Invalid email or password."),
    SIGNUP_REQUEST_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "A004", "A signup request is already in progress for this email."),
}

open class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message
) : RuntimeException(message)
