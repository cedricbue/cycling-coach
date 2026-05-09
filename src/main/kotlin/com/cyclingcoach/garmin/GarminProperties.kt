package com.cyclingcoach.garmin

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "garmin")
data class GarminProperties(
    @field:NotBlank(message = "GARMIN_EMAIL must be set")
    val email: String = "",
    @field:NotBlank(message = "GARMIN_PASSWORD must be set")
    val password: String = "",
    val intervalMs: Long = 21_600_000,
    val connect: ConnectProperties = ConnectProperties(),
) {
    data class ConnectProperties(
        val client: ClientProperties = ClientProperties(),
        val activity: ActivityProperties = ActivityProperties(),
        val weight: WeightProperties = WeightProperties(),
    ) {
        data class ClientProperties(
            val ssoBaseUrl: String = "https://sso.garmin.com",
            val ssoClientId: String = "GCM_IOS_DARK",
            val ssoServiceUrl: String = "https://mobile.integration.garmin.com/gcm/ios",
            val diAuthBaseUrl: String = "https://diauth.garmin.com",
            val apiBaseUrl: String = "https://connectapi.garmin.com",
        )

        data class ActivityProperties(
            val initialFetchDays: Int = 365,
            val maxConcurrentDownloads: Int = 2,
            val pageSize: Int = 10,
        )

        data class WeightProperties(
            val initialFetchDays: Int = 365,
        )
    }
}
