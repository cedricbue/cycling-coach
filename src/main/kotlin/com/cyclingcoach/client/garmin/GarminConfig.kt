package com.cyclingcoach.client.garmin

data class GarminConfig(
    val ssoBaseUrl: String = "https://sso.garmin.com",
    val ssoClientId: String = "GCM_IOS_DARK",
    val ssoServiceUrl: String = "https://mobile.integration.garmin.com/gcm/ios",
    val diAuthBaseUrl: String = "https://diauth.garmin.com",
    val apiBaseUrl: String = "https://connectapi.garmin.com",
)
