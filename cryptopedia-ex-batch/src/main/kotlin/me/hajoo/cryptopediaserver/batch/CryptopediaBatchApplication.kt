package me.hajoo.cryptopediaserver.batch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@EnableScheduling
@EnableFeignClients
@ComponentScan(basePackages = ["me.hajoo.cryptopediaserver"])
@EntityScan(basePackages = ["me.hajoo.cryptopediaserver"])
@EnableJpaRepositories(basePackages = ["me.hajoo.cryptopediaserver"])
class CryptopediaBatchApplication

fun main(args: Array<String>) {
    runApplication<CryptopediaBatchApplication>(*args)
}
