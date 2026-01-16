package me.hajoo.cryptopediaserver.core.common.response

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun <T> success(data: T? = null): ApiResponse<T> {
            return ApiResponse(
                success = true,
                data = data
            )
        }

        fun error(code: String, message: String): ApiResponse<Nothing> {
            return ApiResponse(
                success = false,
                error = ApiError(code, message)
            )
        }
    }
}

data class ApiError(
    val code: String,
    val message: String
)
