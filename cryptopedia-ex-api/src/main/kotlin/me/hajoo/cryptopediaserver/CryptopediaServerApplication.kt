package me.hajoo.cryptopediaserver

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.servers.Server
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@OpenAPIDefinition(
    info = Info(
        title = "Cryptopedia API",
        version = "v1",
        description = "Cryptopedia 서버의 회원/인증 및 기타 기능을 제공하는 REST API 문서입니다.",
    ),
    servers = [
        Server(url = "https://api.cryptopedia.com", description = "Production"),
        Server(url = "http://localhost:8080", description = "Local")
    ]
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    `in` = SecuritySchemeIn.HEADER,
)
class CryptopediaServerApplication

fun main(args: Array<String>) {
    runApplication<CryptopediaServerApplication>(*args)
}
