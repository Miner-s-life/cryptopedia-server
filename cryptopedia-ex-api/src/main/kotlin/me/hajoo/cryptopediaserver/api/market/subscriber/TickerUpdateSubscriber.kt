package me.hajoo.cryptopediaserver.api.market.subscriber

import com.fasterxml.jackson.databind.ObjectMapper
import me.hajoo.cryptopediaserver.core.market.event.TickerUpdateEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.stereotype.Component

@Component
class TickerUpdateSubscriber(
    private val objectMapper: ObjectMapper,
    private val eventPublisher: ApplicationEventPublisher
) : MessageListener {

    override fun onMessage(message: Message, pattern: ByteArray?) {
        try {
            val event = objectMapper.readValue(message.body, TickerUpdateEvent::class.java)
            eventPublisher.publishEvent(event)
        } catch (e: Exception) {
            // Log error
        }
    }
}
