package me.hajoo.cryptopediaserver.batch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.ComponentScan
import jakarta.annotation.PostConstruct
import java.util.TimeZone

@SpringBootApplication
@EnableScheduling
@EnableFeignClients(basePackages = ["me.hajoo.cryptopediaserver"])
@ComponentScan(basePackages = ["me.hajoo.cryptopediaserver"])
@EntityScan(basePackages = ["me.hajoo.cryptopediaserver"])
@EnableJpaRepositories(basePackages = ["me.hajoo.cryptopediaserver"])
class CryptopediaBatchApplication {
    @PostConstruct
    fun init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }
}

fun main(args: Array<String>) {
    runApplication<CryptopediaBatchApplication>(*args)
}
