package com.cyclingcoach.config

import com.cyclingcoach.sync.GarminProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfig(
    private val garminProperties: GarminProperties,
) {
    /**
     * Garmin Connect API calls (activity list, TCX download) against connectapi.garmin.com.
     * Authorization: Bearer token is added per-request by GarminSyncService.
     */
    @Bean
    fun garminApiRestClient(): RestClient =
        RestClient
            .builder()
            .baseUrl(garminProperties.api.baseUrl)
            .defaultHeader("User-Agent", "GCM-Android-5.23")
            .defaultHeader("X-Garmin-User-Agent", "com.garmin.android.apps.connectmobile/5.23; ; Google/sdk_gphone64_arm64/google; Android/33; Dalvik/2.1.0")
            .defaultHeader("Accept", "application/json, text/plain, */*")
            .defaultHeader("Accept-Language", "en-US,en;q=0.9")
            .build()
}
