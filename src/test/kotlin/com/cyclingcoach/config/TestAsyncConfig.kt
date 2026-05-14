package com.cyclingcoach.config

import com.cyclingcoach.Profiles
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.scheduling.annotation.AsyncConfigurer
import java.util.concurrent.Executor

/**
 * Replaces the virtual-thread async executor with a synchronous one during tests.
 * This prevents @Async event listeners from spilling across test boundaries and
 * causing race conditions (e.g., UNIQUE constraint violations in training_load).
 */
@Configuration
@Profile(Profiles.TEST)
class TestAsyncConfig : AsyncConfigurer {
    @Bean(VIRTUAL_THREAD_EXECUTOR)
    fun virtualThreadExecutor(): Executor = SyncTaskExecutor()

    override fun getAsyncExecutor(): Executor = SyncTaskExecutor()
}
