package com.cyclingcoach.ai

import com.cyclingcoach.config.VIRTUAL_THREAD_EXECUTOR
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.io.IOException

@Component
class OllamaLauncher(
    private val ollamaRestClient: RestClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async(VIRTUAL_THREAD_EXECUTOR)
    fun ensureRunningAndPull(model: String) {
        if (!isReachable()) {
            if (!launch()) return
        }
        pullModel(model)
    }

    private fun launch(): Boolean {
        log.info("Ollama not reachable — attempting to start...")
        return try {
            ProcessBuilder("ollama", "serve")
                .redirectErrorStream(true)
                .start() // fire-and-forget; process runs in background

            // Poll up to 15s for Ollama to become ready
            repeat(15) {
                Thread.sleep(1000)
                if (isReachable()) {
                    log.info("Ollama started successfully")
                    return true
                }
            }
            log.warn("Ollama process started but not reachable after 15s — model pull skipped")
            false
        } catch (e: IOException) {
            log.warn(
                "Could not start Ollama — is it installed and on PATH? " +
                    "(ollama serve failed: ${e.message})",
            )
            false
        }
    }

    private fun pullModel(model: String) {
        log.info("Checking/pulling Ollama model '$model' (may take a few minutes on first run)...")
        try {
            val process =
                ProcessBuilder("ollama", "pull", model)
                    .redirectErrorStream(true)
                    .start()

            process.inputStream.bufferedReader().forEachLine { line ->
                if (line.isNotBlank()) log.info("[ollama] $line")
            }

            val exit = process.waitFor()
            if (exit == 0) {
                log.info("Ollama model '$model' is ready")
            } else {
                log.warn("ollama pull '$model' exited with code $exit")
            }
        } catch (e: IOException) {
            log.warn("Could not pull Ollama model '$model': ${e.message}")
        }
    }

    private fun isReachable(): Boolean =
        try {
            ollamaRestClient.get().uri("/api/tags").retrieve().toBodilessEntity()
            true
        } catch (_: RestClientException) {
            false
        }
}
