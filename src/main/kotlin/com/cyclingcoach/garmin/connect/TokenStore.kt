package com.cyclingcoach.garmin.connect

/**
 * Pluggable persistence for Garmin OAuth tokens.
 * Implementations live outside the garmin package (e.g. jOOQ-backed in sync/).
 */
interface TokenStore {
    fun save(tokens: GarminTokens)

    fun load(): GarminTokens?

    fun delete()
}
