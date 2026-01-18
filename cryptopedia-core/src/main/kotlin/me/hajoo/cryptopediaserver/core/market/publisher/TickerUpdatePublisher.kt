package me.hajoo.cryptopediaserver.core.market.publisher

import com.fasterxml.jackson.databind.ObjectMapper
import me.hajoo.cryptopediaserver.core.market.event.TickerUpdateEvent
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class TickerUpdatePublisher(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    fun publish(event: TickerUpdateEvent) {
        val message = objectMapper.writeValueAsString(event)
        redisTemplate.convertAndSend("ticker-updates", message)
    }
}
