package me.hajoo.cryptopediaserver.markingestor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MarketIngestorApplication

fun main(args: Array<String>) {
    runApplication<MarketIngestorApplication>(*args)
}
