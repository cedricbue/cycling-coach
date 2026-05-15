package com.cyclingcoach.config

import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class OllamaConfig(
    private val ollamaConnectionProperties: OllamaConnectionProperties,
) {
    @Bean
    fun ollamaRestClient(): RestClient = RestClient.builder()
        .baseUrl(ollamaConnectionProperties.baseUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(2))
            setReadTimeout(Duration.ofSeconds(5))
        })
        .build()
}
