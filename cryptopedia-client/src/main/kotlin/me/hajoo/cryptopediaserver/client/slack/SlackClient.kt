package me.hajoo.cryptopediaserver.client.slack

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import java.net.URI

@FeignClient(name = "slack-client", url = "https://hooks.slack.com")
interface SlackClient {

    @PostMapping(consumes = ["application/json"])
    fun sendMessage(baseUri: URI, @RequestBody message: SlackMessage)
}

data class SlackMessage(
    val text: String
)
