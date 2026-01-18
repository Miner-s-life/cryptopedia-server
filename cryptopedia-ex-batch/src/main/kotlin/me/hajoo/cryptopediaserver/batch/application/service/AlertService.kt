package me.hajoo.cryptopediaserver.batch.application.service

import me.hajoo.cryptopediaserver.client.slack.SlackService
import me.hajoo.cryptopediaserver.core.domain.SymbolMetrics
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class AlertService(
    private val slackService: SlackService,
    private val redisTemplate: RedisTemplate<String, String>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun sendSurgeAlert(metrics: SymbolMetrics) {
        val key = "alert:cooldown:${metrics.exchange}:${metrics.symbol}"
        val cooldown = Duration.ofMinutes(5)

        // Atomic check and set
        val isFirst = redisTemplate.opsForValue().setIfAbsent(key, "SENT", cooldown)

        if (isFirst == true) {
            try {
                val message = """
                    🚨 *Volume Surge Detected* 🚨
                    
                    *Exchange*: ${metrics.exchange}
                    *Symbol*: ${metrics.symbol}
                    *RVOL*: ${metrics.rvolToday} (Expected: 1.0)
                    *Price Change (24h)*: ${metrics.priceChangePercent24h}%
                    
                    🌊 _Trading volume is significantly higher than average!_
                """.trimIndent()

                slackService.sendMessage(message)
                logger.info("Sent alert for ${metrics.symbol}")
            } catch (e: Exception) {
                logger.error("Failed to process alert flow", e)
            }
        }
    }
}
