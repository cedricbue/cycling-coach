package com.cyclingcoach.garmin.connect.internal

import com.cyclingcoach.garmin.connect.GarminApiException
import com.cyclingcoach.garmin.connect.GarminAuthException
import com.cyclingcoach.garmin.connect.GarminConfig
import com.cyclingcoach.garmin.connect.GarminMfaRequiredException
import com.cyclingcoach.garmin.connect.GarminTokenException
import com.cyclingcoach.garmin.connect.GarminTokens
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.time.Instant
import java.util.*

/**
 * Handles the Garmin Connect DI-OAuth2 authentication flow:
 * 1. POST /mobile/api/login (JSON) → serviceTicketId
 * 2. POST /di-oauth2-service/oauth/token (form, Basic auth) → DI Bearer token
 *
 * Also supports token refresh via the same /di-oauth2-service/oauth/token endpoint.
 */
internal class GarminAuthService(
    private val config: GarminConfig,
    private val http: GarminHttpClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun authenticate(
        username: String,
        password: String,
    ): GarminTokens {
        log.info("Authenticating with Garmin Connect for {}", username)
        val ticket = loginAndGetTicket(username, password)
        return exchangeForDiToken(ticket)
    }

    fun refreshToken(tokens: GarminTokens): GarminTokens {
        log.info("Refreshing DI token for client {}", tokens.diClientId)
        val fields =
            mapOf(
                "grant_type" to "refresh_token",
                "client_id" to tokens.diClientId,
                "refresh_token" to tokens.refreshToken,
            )
        val headers =
            nativeHeaders() +
                mapOf(
                    "Authorization" to basicAuth(tokens.diClientId),
                    "Cache-Control" to "no-cache",
                )

        val responseBody =
            try {
                http.postForm(
                    url = "${config.diAuthBaseUrl}/di-oauth2-service/oauth/token",
                    headers = headers,
                    fields = fields,
                )
            } catch (e: GarminApiException) {
                throw GarminTokenException("Token refresh failed: ${e.message}", e)
            }

        return parseTokenResponse(
            responseBody,
            fallbackRefreshToken = tokens.refreshToken,
            fallbackClientId = tokens.diClientId,
        ).also {
            log.info(
                "DI token refreshed — expires in {}s",
                it.accessTokenExpiresAt.epochSecond - Instant.now().epochSecond,
            )
        }
    }

    // ── Step 1: POST credentials → serviceTicketId ──────────────────────────────

    private fun loginAndGetTicket(
        username: String,
        password: String,
    ): String {
        log.debug("Posting credentials to Garmin SSO mobile endpoint")
        val loginBody = """{"username":"$username","password":"$password","rememberMe":true,"captchaToken":""}"""

        val url =
            buildString {
                append(config.ssoBaseUrl)
                append("/mobile/api/login")
                append("?clientId=").append(config.ssoClientId)
                append("&locale=en-US")
                append("&service=").append(URLEncoder.encode(config.ssoServiceUrl, Charsets.UTF_8))
            }

        val headers =
            mapOf(
                "User-Agent" to IOS_LOGIN_UA,
                "Accept" to "application/json, text/plain, */*",
                "Content-Type" to "application/json",
                "Origin" to config.ssoBaseUrl,
            )

        val responseBody =
            try {
                http.postJson(url = url, headers = headers, body = loginBody)
            } catch (e: GarminApiException) {
                throw GarminAuthException("Garmin login request failed: ${e.message}")
            }

        log.debug("Login response: {}", responseBody.take(300))

        val status = extractJsonField(responseBody, "type") ?: ""
        when {
            status.contains("SUCCESSFUL") -> {}

            status.contains("MFA_REQUIRED") -> {
                throw GarminMfaRequiredException("Garmin MFA is enabled. Disable it in Garmin Connect settings to allow automated sync.")
            }

            status.contains("INVALID_USERNAME_PASSWORD") -> {
                throw GarminAuthException("Invalid Garmin username or password.")
            }

            else -> {
                throw GarminAuthException("Garmin login failed with status '$status'. Response: ${responseBody.take(300)}")
            }
        }

        return extractJsonField(responseBody, "serviceTicketId")
            ?: throw GarminAuthException("serviceTicketId missing from login response: ${responseBody.take(300)}")
    }

    // ── Step 2: serviceTicketId → DI Bearer token ───────────────────────────────

    private fun exchangeForDiToken(ticket: String): GarminTokens {
        for (clientId in DI_CLIENT_IDS) {
            try {
                log.debug("Trying DI token exchange with clientId={}", clientId)
                val fields =
                    mapOf(
                        "client_id" to clientId,
                        "service_ticket" to ticket,
                        "grant_type" to DI_GRANT_TYPE,
                        "service_url" to config.ssoServiceUrl,
                    )
                val headers =
                    nativeHeaders() +
                        mapOf(
                            "Authorization" to basicAuth(clientId),
                            "Cache-Control" to "no-cache",
                        )

                val responseBody =
                    http.postForm(
                        url = "${config.diAuthBaseUrl}/di-oauth2-service/oauth/token",
                        headers = headers,
                        fields = fields,
                    )

                val tokens = parseTokenResponse(responseBody, fallbackClientId = clientId)
                if (tokens.accessToken.isBlank()) continue

                log.info(
                    "Garmin DI token obtained — clientId={}, expires in {}s",
                    tokens.diClientId,
                    tokens.accessTokenExpiresAt.epochSecond - Instant.now().epochSecond,
                )
                return tokens
            } catch (e: GarminApiException) {
                log.debug("DI token exchange failed for clientId={}: {}", clientId, e.message)
            } catch (e: Exception) {
                log.debug("DI token exchange failed for clientId={}: {}", clientId, e.message)
            }
        }
        throw GarminTokenException("DI token exchange failed for all client IDs: ${DI_CLIENT_IDS.toList()}")
    }

    // ── JSON helpers (no Jackson dependency — simple field extraction) ───────────

    private fun parseTokenResponse(
        json: String,
        fallbackRefreshToken: String = "",
        fallbackClientId: String = "",
    ): GarminTokens {
        val accessToken = extractJsonField(json, "access_token") ?: ""
        val refreshToken =
            extractJsonField(json, "refresh_token")?.ifBlank { fallbackRefreshToken } ?: fallbackRefreshToken
        val expiresIn = extractJsonField(json, "expires_in")?.toLongOrNull() ?: 3600L
        val refreshExpiresIn = extractJsonField(json, "refresh_token_expires_in")?.toLongOrNull() ?: 7_776_000L
        val diClientId = extractClientIdFromJwt(accessToken) ?: fallbackClientId

        return GarminTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            diClientId = diClientId,
            accessTokenExpiresAt = Instant.now().plusSeconds(expiresIn),
            refreshTokenExpiresAt = Instant.now().plusSeconds(refreshExpiresIn),
        )
    }

    /**
     * Minimal JSON field extractor — avoids Jackson dependency in the garmin package.
     * Handles simple top-level and one-level-nested string/number fields.
     */
    private fun extractJsonField(
        json: String,
        field: String,
    ): String? {
        // Match "field":"value" or "field":number
        val pattern = """"$field"\s*:\s*"?([^",}\]]+)"?""".toRegex()
        return pattern
            .find(json)
            ?.groupValues
            ?.get(1)
            ?.trim()
    }

    private fun extractClientIdFromJwt(token: String): String? =
        try {
            val payload = token.split(".").getOrNull(1) ?: return null
            val padded = payload + "=".repeat(-payload.length and 3)
            val decoded = String(Base64.getUrlDecoder().decode(padded), Charsets.UTF_8)
            extractJsonField(decoded, "client_id")?.ifBlank { null }
        } catch (_: Exception) {
            null
        }

    private fun nativeHeaders(): Map<String, String> =
        mapOf(
            "User-Agent" to NATIVE_USER_AGENT,
            "X-Garmin-User-Agent" to NATIVE_X_GARMIN_USER_AGENT,
            "X-Garmin-Paired-App-Version" to "10861",
            "X-Garmin-Client-Platform" to "Android",
            "X-App-Ver" to "10861",
            "X-Lang" to "en",
            "X-GCExperience" to "GC5",
            "Accept-Language" to "en-US,en;q=0.9",
            "Accept" to "application/json,text/html;q=0.9,*/*;q=0.8",
        )

    private fun basicAuth(clientId: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$clientId:".toByteArray(Charsets.UTF_8))

    private companion object {
        val DI_CLIENT_IDS =
            arrayOf(
                "GARMIN_CONNECT_MOBILE_ANDROID_DI_2025Q2",
                "GARMIN_CONNECT_MOBILE_ANDROID_DI_2024Q4",
                "GARMIN_CONNECT_MOBILE_ANDROID_DI",
                "GARMIN_CONNECT_MOBILE_IOS_DI",
            )

        const val DI_GRANT_TYPE = "https://connectapi.garmin.com/di-oauth2-service/oauth/grant/service_ticket"

        const val IOS_LOGIN_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148"

        const val NATIVE_USER_AGENT = "GCM-Android-5.23"
        const val NATIVE_X_GARMIN_USER_AGENT =
            "com.garmin.android.apps.connectmobile/5.23; ; Google/sdk_gphone64_arm64/google; Android/33; Dalvik/2.1.0"
    }
}
