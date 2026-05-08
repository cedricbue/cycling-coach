package com.cyclingcoach.garmin.connect.client

/**
 * Pluggable persistence for Garmin OAuth tokens.
 */
interface TokenStore {
    fun save(tokens: GarminTokens)

    fun load(): GarminTokens?

    fun delete()
}
