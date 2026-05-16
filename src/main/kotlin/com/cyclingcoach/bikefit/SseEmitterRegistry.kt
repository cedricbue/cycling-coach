package com.cyclingcoach.bikefit

import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap

@Component
class SseEmitterRegistry {

    private val emitters = ConcurrentHashMap<String, SseEmitter>()

    fun register(id: String): SseEmitter {
        val emitter = SseEmitter(Long.MAX_VALUE)
        emitters[id] = emitter
        emitter.onCompletion { emitters.remove(id) }
        emitter.onTimeout { emitters.remove(id) }
        emitter.onError { emitters.remove(id) }
        return emitter
    }

    fun completeOk(id: String, json: String) = complete(id, json)

    fun completeError(id: String, json: String) = complete(id, json)

    private fun complete(id: String, json: String) {
        val emitter = emitters.remove(id) ?: return
        try {
            emitter.send(json)
            emitter.complete()
        } catch (_: Exception) {
            emitter.completeWithError(IllegalStateException("SSE send failed"))
        }
    }
}
