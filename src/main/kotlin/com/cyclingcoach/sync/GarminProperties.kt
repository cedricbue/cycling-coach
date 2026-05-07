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
    val sso: SsoProperties = SsoProperties(),
    val diAuth: DiAuthProperties = DiAuthProperties(),
    val api: ApiProperties = ApiProperties(),
    val sync: SyncProperties = SyncProperties(),
) {
    data class SsoProperties(
        val baseUrl: String = "https://sso.garmin.com",
        val clientId: String = "GCM_IOS_DARK",
        val serviceUrl: String = "https://mobile.integration.garmin.com/gcm/ios",
    )

    /** DI OAuth2 token endpoint (diauth.garmin.com). */
    data class DiAuthProperties(
        val baseUrl: String = "https://diauth.garmin.com",
    )

    /** Garmin Connect API — all data endpoints live on connectapi.garmin.com. */
    data class ApiProperties(
        val baseUrl: String = "https://connectapi.garmin.com",
    )

    data class SyncProperties(
        val initialFetchDays: Int = 365,
    )
}
