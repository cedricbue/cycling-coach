package com.cyclingcoach.config

import com.cyclingcoach.ai.OllamaLauncher
import jakarta.annotation.PostConstruct
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AiConfig(
    private val ollamaLauncher: OllamaLauncher,
    private val ollamaChatProperties: OllamaChatProperties,
) {
    @Bean
    fun chatClient(ollamaChatModel: OllamaChatModel): ChatClient = ChatClient.create(ollamaChatModel)

    @PostConstruct
    fun ensureOllamaReady() {
        ollamaLauncher.ensureRunningAndPull(ollamaChatProperties.model)
    }
}
