package me.hajoo.cryptopediaserver.api.config.redis

import me.hajoo.cryptopediaserver.api.market.subscriber.TickerUpdateSubscriber
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter

@Configuration
class RedisSubscriberConfig {

    @Bean
    fun redisMessageListenerContainer(
        connectionFactory: RedisConnectionFactory,
        tickerUpdateSubscriber: TickerUpdateSubscriber
    ): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(connectionFactory)
        container.addMessageListener(MessageListenerAdapter(tickerUpdateSubscriber), ChannelTopic("ticker-updates"))
        return container
    }
}
