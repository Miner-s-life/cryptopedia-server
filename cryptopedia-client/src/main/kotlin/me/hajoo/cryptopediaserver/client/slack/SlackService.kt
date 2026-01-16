package me.hajoo.cryptopediaserver.client.slack

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI

@Service
class SlackService(
    private val slackClient: SlackClient,
    @Value("\${slack.webhook.url:}") private val webhookUrl: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun sendMessage(text: String) {
        if (webhookUrl.isBlank()) {
            logger.warn("Slack webhook URL is not configured. Skipping message.")
            return
        }

        try {
            slackClient.sendMessage(URI.create(webhookUrl), SlackMessage(text))
        } catch (e: Exception) {
            logger.error("Failed to send slack message", e)
        }
    }
}
