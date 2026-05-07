package com.cyclingcoach.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import java.util.concurrent.Executor
import java.util.concurrent.Executors

const val VIRTUAL_THREAD_EXECUTOR = "virtualThreadExecutor"

@Configuration
class AsyncConfig : AsyncConfigurer {
    @Bean(VIRTUAL_THREAD_EXECUTOR)
    fun virtualThreadExecutor(): Executor =
        Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("async-", 0).factory(),
        )

    override fun getAsyncExecutor(): Executor = virtualThreadExecutor()
}
