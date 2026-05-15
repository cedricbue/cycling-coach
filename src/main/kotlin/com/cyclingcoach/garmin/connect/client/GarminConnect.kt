package com.cyclingcoach.garmin.connect.client

import com.cyclingcoach.garmin.connect.client.internal.GarminAuthService
import com.cyclingcoach.garmin.connect.client.internal.GarminHttpClient
import com.fasterxml.jackson.databind.ObjectMapper
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
    private val mapper: ObjectMapper,
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

    // ── API: Weight ───────────────────────────────────────────────────────────────

    fun getWeights(
        startDate: LocalDate,
        endDate: LocalDate = LocalDate.now(),
    ): List<GarminWeightEntry> {
        val url =
            "${config.apiBaseUrl}/weight-service/weight/dateRange" +
                "?startDate=$startDate&endDate=$endDate"

        val body = authenticatedGet(url)
        if (body.isBlank()) return emptyList()

        return parseWeightResponse(body)
    }

    // ── API: Activities ─────────────────────────────────────────────────────────

    fun getActivities(
        since: LocalDate? = null,
        start: Int = 0,
        limit: Int = 100,
    ): List<GarminActivityWithRaw> {
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

    // ── JSON parsing ─────────────────────────────────────────────────────────────

    private fun parseActivityList(json: String): List<GarminActivityWithRaw> {
        val trimmed = json.trim()

        val arrayNode =
            when {
                trimmed.startsWith("[") -> {
                    mapper.readTree(trimmed)
                }

                trimmed.contains("\"activityList\"") -> {
                    mapper.readTree(trimmed).get("activityList")
                }

                else -> {
                    log.warn("Unexpected activity list response shape (first 200): {}", trimmed.take(200))
                    return emptyList()
                }
            }

        if (arrayNode == null || !arrayNode.isArray) {
            log.warn("Could not find activity array in response")
            return emptyList()
        }

        return arrayNode.mapNotNull { node ->
            if (!node.has("activityId")) return@mapNotNull null
            val activity = mapper.treeToValue(node, GarminActivity::class.java)
            GarminActivityWithRaw(activity = activity, rawJson = node.toString())
        }
    }

    private fun parseWeightResponse(json: String): List<GarminWeightEntry> {
        val trimmed = json.trim()
        return try {
            val response = mapper.readValue(trimmed, GarminWeightResponse::class.java)
            log.debug("Parsed weight response: {} entries", response.dateWeightList?.size ?: 0)
            response.dateWeightList ?: emptyList()
        } catch (e: Exception) {
            log.warn("Failed to parse weight response: {} — raw (first 500): {}", e.message, trimmed.take(500))
            emptyList()
        }
    }

    private companion object {
        val REAUTH_STATUS_CODES = setOf(401, 403)
    }
}
