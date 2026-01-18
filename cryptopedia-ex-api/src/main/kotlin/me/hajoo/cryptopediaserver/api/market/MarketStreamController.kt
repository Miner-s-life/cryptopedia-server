package me.hajoo.cryptopediaserver.api.market

import me.hajoo.cryptopediaserver.core.market.dto.TickerWithMetricsResponse
import me.hajoo.cryptopediaserver.core.market.event.TickerUpdateEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.CopyOnWriteArrayList

@RestController
@RequestMapping("/api/v1/market/stream")
class MarketStreamController(
    private val marketService: MarketService,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val emitters = CopyOnWriteArrayList<SseEmitter>()

    @GetMapping("/tickers", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamTickers(): SseEmitter {
        val emitter = SseEmitter(30 * 60 * 1000L)
        emitters.add(emitter)

        emitter.onCompletion { emitters.remove(emitter) }
        emitter.onTimeout { emitters.remove(emitter) }
        emitter.onError { emitters.remove(emitter) }

        try {
            val initialData = marketService.getTickersWithMetrics()
            emitter.send(SseEmitter.event().name("ticker-update").data(initialData))
        } catch (e: Exception) {
            emitter.completeWithError(e)
        }

        return emitter
    }

    @EventListener
    fun handleTickerUpdate(event: TickerUpdateEvent) {
        val deadEmitters = mutableListOf<SseEmitter>()
        emitters.forEach { emitter ->
            try {
                emitter.send(SseEmitter.event().name("ticker-update").data(event.tickers))
            } catch (e: Exception) {
                deadEmitters.add(emitter)
            }
        }
        emitters.removeAll(deadEmitters.toSet())
    }
}
