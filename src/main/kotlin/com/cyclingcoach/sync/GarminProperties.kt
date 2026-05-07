package com.cyclingcoach.sync

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "sync.garmin")
data class GarminProperties(
    @field:NotBlank(message = "GARMIN_EMAIL must be set")
    val email: String = "",
    @field:NotBlank(message = "GARMIN_PASSWORD must be set")
    val password: String = "",
    val sync: SyncProperties = SyncProperties(),
) {
    data class SyncProperties(
        val initialFetchDays: Int = 365,
    )
}
