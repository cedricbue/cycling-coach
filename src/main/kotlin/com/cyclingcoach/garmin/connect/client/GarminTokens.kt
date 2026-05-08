package com.cyclingcoach.garmin.connect.client

import java.time.Instant

data class GarminTokens(
    val accessToken: String,
    val refreshToken: String,
    val diClientId: String,
    val accessTokenExpiresAt: Instant,
    val refreshTokenExpiresAt: Instant,
) {
    fun isExpired(): Boolean = Instant.now().isAfter(accessTokenExpiresAt.minusSeconds(1800))

    fun isRefreshTokenExpired(): Boolean = Instant.now().isAfter(refreshTokenExpiresAt.minusSeconds(300))
}
