package me.hajoo.cryptopediaserver.batch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CryptopediaBatchApplication

fun main(args: Array<String>) {
    runApplication<CryptopediaBatchApplication>(*args)
}
