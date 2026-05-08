package com.cyclingcoach.config

import com.cyclingcoach.garmin.connect.GarminConfig
import com.cyclingcoach.garmin.connect.GarminConnect
import com.cyclingcoach.garmin.connect.TokenStore
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "garmin.connect")
data class GarminConnectProperties(
    val ssoBaseUrl: String = "https://sso.garmin.com",
    val ssoClientId: String = "GCM_IOS_DARK",
    val ssoServiceUrl: String = "https://mobile.integration.garmin.com/gcm/ios",
    val diAuthBaseUrl: String = "https://diauth.garmin.com",
    val apiBaseUrl: String = "https://connectapi.garmin.com",
)

@Configuration
class GarminClientConfig {
    @Bean
    fun garminConfig(props: GarminConnectProperties): GarminConfig =
        GarminConfig(
            ssoBaseUrl = props.ssoBaseUrl,
            ssoClientId = props.ssoClientId,
            ssoServiceUrl = props.ssoServiceUrl,
            diAuthBaseUrl = props.diAuthBaseUrl,
            apiBaseUrl = props.apiBaseUrl,
        )

    @Bean
    fun garminClient(
        garminConfig: GarminConfig,
        tokenStore: TokenStore,
    ): GarminConnect = GarminConnect(garminConfig, tokenStore)
}
