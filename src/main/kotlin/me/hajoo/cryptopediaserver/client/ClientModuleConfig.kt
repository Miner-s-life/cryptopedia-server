package me.hajoo.cryptopediaserver.client

import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Configuration

@Configuration
@EnableFeignClients(basePackages = ["me.hajoo.cryptopediaserver.client"])
class ClientModuleConfig
