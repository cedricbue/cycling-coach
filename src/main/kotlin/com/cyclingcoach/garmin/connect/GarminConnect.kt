package com.cyclingcoach.garmin.connect

import com.cyclingcoach.garmin.connect.internal.GarminAuthService
import com.cyclingcoach.garmin.connect.internal.GarminHttpClient
import org.slf4j.LoggerFactory
import java.time.LocalDate

/**
 * Reusable, framework-agnostic Garmin Connect client.
 *
 * Handles authentication (SSO + DI token exchange), automatic token refresh,
 * and provides an extensible API surface for fetching Garmin resources.
 */
class GarminConnect(
    private val config: GarminConfig,
    private val tokenStore: TokenStore,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http = GarminHttpClient()
    private val auth = GarminAuthService(config, http)

    @Volatile
    private var credentials: Pair<String, String>? = null

    // ── Authentication ──────────────────────────────────────────────────────────

    fun login(
        username: String,
        password: String,
    ): GarminTokens {
        credentials = username to password
        val tokens = auth.authenticate(username, password)
        tokenStore.save(tokens)
        log.info("Garmin authentication successful — DI tokens saved")
        return tokens
    }

    fun refreshToken(tokens: GarminTokens): GarminTokens {
        val refreshed = auth.refreshToken(tokens)
        tokenStore.save(refreshed)
        log.info("DI token refreshed successfully")
        return refreshed
    }

    fun hasValidSession(): Boolean {
        val tokens = tokenStore.load() ?: return false
        return !tokens.isExpired()
    }

    // ── API: Activities ─────────────────────────────────────────────────────────

    fun getActivities(
        since: LocalDate? = null,
        start: Int = 0,
        limit: Int = 100,
    ): List<GarminActivity> {
        val sinceDate = since?.toString() ?: LocalDate.now().minusDays(365).toString()
        val url =
            "${config.apiBaseUrl}/activitylist-service/activities/search/activities" +
                "?start=$start&limit=$limit&startDate=$sinceDate"

        val body = authenticatedGet(url)
        if (body.isBlank()) return emptyList()

        return parseActivityList(body)
    }

    fun downloadTcx(activityId: Long): String {
        val url = "${config.apiBaseUrl}/download-service/export/tcx/activity/$activityId"
        return authenticatedGet(url)
    }

    // ── Internal: authenticated requests with auto-refresh/reauth ───────────────

    private fun authenticatedGet(url: String): String {
        val tokens =
            resolveTokens()
                ?: throw GarminAuthException("No valid Garmin session available")

        return try {
            http.get(url, authHeaders(tokens))
        } catch (e: GarminApiException) {
            if (e.statusCode !in REAUTH_STATUS_CODES) throw e

            log.warn("Garmin returned {} — re-authenticating and retrying", e.statusCode)
            val refreshed =
                resolveTokens(forceRefresh = true)
                    ?: throw GarminAuthException("Re-authentication failed")

            try {
                http.get(url, authHeaders(refreshed))
            } catch (retryEx: GarminApiException) {
                log.error("Garmin still returned {} after re-authentication", retryEx.statusCode)
                throw retryEx
            }
        }
    }

    private fun resolveTokens(forceRefresh: Boolean = false): GarminTokens? {
        val tokens = tokenStore.load() ?: return tryReauthenticate()
        if (!forceRefresh && !tokens.isExpired()) return tokens

        log.info("Garmin access token expired — attempting token refresh")
        if (!tokens.isRefreshTokenExpired()) {
            try {
                return refreshToken(tokens)
            } catch (e: Exception) {
                log.warn("Token refresh failed ({}), falling back to full re-auth", e.message)
            }
        } else {
            log.info("Refresh token also expired — doing full re-auth")
        }
        return tryReauthenticate()
    }

    private fun tryReauthenticate(): GarminTokens? {
        val (username, password) =
            credentials ?: run {
                log.error("No stored credentials — cannot re-authenticate")
                return null
            }
        return try {
            login(username, password)
        } catch (e: Exception) {
            log.error("Re-authentication failed: {}", e.message)
            null
        }
    }

    private fun authHeaders(tokens: GarminTokens): Map<String, String> =
        mapOf(
            "Authorization" to "Bearer ${tokens.accessToken}",
            "User-Agent" to "GCM-Android-5.23",
            "X-Garmin-User-Agent" to
                "com.garmin.android.apps.connectmobile/5.23; ; Google/sdk_gphone64_arm64/google; Android/33; Dalvik/2.1.0",
            "Accept" to "application/json, text/plain, */*",
            "Accept-Language" to "en-US,en;q=0.9",
        )

    // ── JSON parsing (no Jackson dependency) ────────────────────────────────────

    private fun parseActivityList(json: String): List<GarminActivity> {
        val trimmed = json.trim()

        // Determine if we have an array or an object with "activityList" key
        val arrayJson =
            when {
                trimmed.startsWith("[") -> {
                    trimmed
                }

                trimmed.contains("\"activityList\"") -> {
                    val start = trimmed.indexOf('[')
                    val end = trimmed.lastIndexOf(']')
                    if (start >= 0 && end > start) {
                        trimmed.substring(start, end + 1)
                    } else {
                        log.warn("Unexpected activity list response shape (first 200): {}", trimmed.take(200))
                        return emptyList()
                    }
                }

                else -> {
                    log.warn("Unexpected activity list response shape (first 200): {}", trimmed.take(200))
                    return emptyList()
                }
            }

        return parseActivitiesFromArray(arrayJson)
    }

    /**
     * Parses a JSON array of activity objects.
     * Simple regex-based parser to avoid Jackson dependency in this package.
     */
    private fun parseActivitiesFromArray(arrayJson: String): List<GarminActivity> {
        val activities = mutableListOf<GarminActivity>()
        // Split on object boundaries — each activity is a top-level object in the array
        val objectPattern = """\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}""".toRegex()
        for (match in objectPattern.findAll(arrayJson)) {
            val obj = match.value
            val activityId = extractJsonLong(obj, "activityId") ?: continue
            val activityName = extractJsonString(obj, "activityName")
            val startTimeGmt = extractJsonString(obj, "startTimeGMT")
            val typeKey = extractJsonString(obj, "typeKey")
            activities.add(
                GarminActivity(
                    activityId = activityId,
                    activityName = activityName,
                    startTimeGmt = startTimeGmt,
                    activityType = typeKey?.let { GarminActivityType(it) },
                ),
            )
        }
        return activities
    }

    private fun extractJsonString(
        json: String,
        field: String,
    ): String? {
        val pattern = """"$field"\s*:\s*"([^"]*?)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun extractJsonLong(
        json: String,
        field: String,
    ): Long? {
        val pattern = """"$field"\s*:\s*(\d+)""".toRegex()
        return pattern
            .find(json)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
    }

    private companion object {
        val REAUTH_STATUS_CODES = setOf(401, 403)
    }
}
