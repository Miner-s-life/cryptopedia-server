package me.hajoo.cryptopediaserver.core.common.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val code: String,
    val message: String
) {
    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "허용되지 않은 메소드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C003", "서버 내부 오류가 발생했습니다."),
    ENTITY_NOT_FOUND(HttpStatus.BAD_REQUEST, "C004", "엔티티를 찾을 수 없습니다."),

    // Auth & User
    EMAIL_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "A001", "이미 존재하는 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "A002", "이미 존재하는 닉네임입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A003", "자격 증명이 유효하지 않습니다."),
    SIGNUP_REQUEST_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "A004", "이미 진행 중인 가입 신청이 있습니다."),
}

open class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message
) : RuntimeException(message)
