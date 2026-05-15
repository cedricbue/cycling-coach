package com.cyclingcoach.config

import com.cyclingcoach.garmin.GarminProperties
import com.cyclingcoach.garmin.connect.client.GarminConfig
import com.cyclingcoach.garmin.connect.client.GarminConnect
import com.cyclingcoach.garmin.connect.client.TokenStore
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GarminClientConfig {
    @Bean
    fun garminConfig(props: GarminProperties): GarminConfig =
        GarminConfig(
            ssoBaseUrl = props.connect.client.ssoBaseUrl,
            ssoClientId = props.connect.client.ssoClientId,
            ssoServiceUrl = props.connect.client.ssoServiceUrl,
            diAuthBaseUrl = props.connect.client.diAuthBaseUrl,
            apiBaseUrl = props.connect.client.apiBaseUrl,
        )

    @Bean
    fun garminClient(
        garminConfig: GarminConfig,
        tokenStore: TokenStore,
        objectMapper: ObjectMapper,
    ): GarminConnect = GarminConnect(garminConfig, tokenStore, objectMapper)
}
