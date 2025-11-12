package me.hajoo.cryptopediaserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CryptopediaServerApplication

fun main(args: Array<String>) {
    runApplication<CryptopediaServerApplication>(*args)
}
