package com.cyclingcoach.sync

import tools.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.time.Instant
import java.util.Base64

/**
 * Authenticates with Garmin Connect using the same flow as python-garminconnect:
 * 1. POST /mobile/api/login (JSON) → serviceTicketId
 * 2. POST diauth.garmin.com/di-oauth2-service/oauth/token (form, Basic auth) → DI Bearer token
 *
 * No OAuth1 or HTML scraping. The DI token is used as a Bearer token for all API calls.
 */
@Component
class GarminAuthClient(
    private val garminProperties: GarminProperties,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun authenticate(
        username: String,
        password: String,
    ): GarminSession {
        log.info("Authenticating with Garmin Connect for {}", username)
        val ticket = loginAndGetTicket(username, password)
        return exchangeForDiToken(ticket)
    }

    fun refreshToken(session: GarminSession): GarminSession {
        log.info("Refreshing DI token for client {}", session.diClientId)
        val form =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "refresh_token")
                add("client_id", session.diClientId)
                add("refresh_token", session.refreshToken)
            }

        val responseBody =
            RestClient.create()
                .post()
                .uri("${garminProperties.diAuth.baseUrl}/di-oauth2-service/oauth/token")
                .headers { applyNativeHeaders(it) }
                .header(HttpHeaders.AUTHORIZATION, basicAuth(session.diClientId))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String::class.java)
                ?: throw IllegalStateException("Empty token refresh response")

        val json = objectMapper.readTree(responseBody)
        val newAccessToken =
            json.path("access_token").asText()
                .ifBlank { throw IllegalStateException("access_token missing from token refresh response") }
        val newRefreshToken = json.path("refresh_token").asText().ifBlank { session.refreshToken }
        val expiresIn = json.path("expires_in").asLong(3600)
        val diClientId = extractClientIdFromJwt(newAccessToken) ?: session.diClientId

        log.info("DI token refreshed — expires in {}s", expiresIn)
        return session.copy(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken,
            diClientId = diClientId,
            accessTokenExpiresAt = Instant.now().plusSeconds(expiresIn),
        )
    }

    // ── Step 1: POST credentials → serviceTicketId ──────────────────────────────

    private fun loginAndGetTicket(
        username: String,
        password: String,
    ): String {
        log.debug("Posting credentials to Garmin SSO mobile endpoint")
        val loginBody =
            objectMapper.writeValueAsString(
                mapOf("username" to username, "password" to password, "rememberMe" to true, "captchaToken" to ""),
            )

        val loginUri =
            UriComponentsBuilder.fromUriString("${garminProperties.sso.baseUrl}/mobile/api/login")
                .queryParam("clientId", garminProperties.sso.clientId)
                .queryParam("locale", "en-US")
                .queryParam("service", garminProperties.sso.serviceUrl)
                .build()
                .toUri()

        val responseBody =
            RestClient.create()
                .post()
                .uri(loginUri)
                .header(HttpHeaders.USER_AGENT, IOS_LOGIN_UA)
                .header(HttpHeaders.ACCEPT, "application/json, text/plain, */*")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.ORIGIN, garminProperties.sso.baseUrl)
                .body(loginBody)
                .retrieve()
                .body(String::class.java)
                ?: throw IllegalStateException("Empty Garmin login response")

        log.debug("Login response: {}", responseBody.take(300))

        val json = objectMapper.readTree(responseBody)
        val status = json.path("responseStatus").path("type").asText()

        when (status) {
            "SUCCESSFUL" -> {}
            "MFA_REQUIRED" ->
                throw UnsupportedOperationException(
                    "Garmin MFA is enabled. Disable it in Garmin Connect settings to allow automated sync.",
                )
            "INVALID_USERNAME_PASSWORD" ->
                throw IllegalArgumentException("Invalid Garmin username or password.")
            else ->
                throw IllegalStateException(
                    "Garmin login failed with status '$status'. Response: ${responseBody.take(300)}",
                )
        }

        return json.path("serviceTicketId").asText()
            .ifBlank { throw IllegalStateException("serviceTicketId missing from login response: ${responseBody.take(300)}") }
    }

    // ── Step 2: serviceTicketId → DI Bearer token ───────────────────────────────

    private fun exchangeForDiToken(ticket: String): GarminSession {
        for (clientId in DI_CLIENT_IDS) {
            try {
                log.debug("Trying DI token exchange with clientId={}", clientId)
                val form =
                    LinkedMultiValueMap<String, String>().apply {
                        add("client_id", clientId)
                        add("service_ticket", ticket)
                        add("grant_type", DI_GRANT_TYPE)
                        add("service_url", garminProperties.sso.serviceUrl)
                    }

                val responseBody =
                    RestClient.create()
                        .post()
                        .uri("${garminProperties.diAuth.baseUrl}/di-oauth2-service/oauth/token")
                        .headers { applyNativeHeaders(it) }
                        .header(HttpHeaders.AUTHORIZATION, basicAuth(clientId))
                        .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(String::class.java)
                        ?: continue

                val json = objectMapper.readTree(responseBody)
                val accessToken = json.path("access_token").asText()
                if (accessToken.isBlank()) continue

                val refreshToken = json.path("refresh_token").asText()
                val expiresIn = json.path("expires_in").asLong(3600)
                val refreshExpiresIn = json.path("refresh_token_expires_in").asLong(7_776_000) // 90 days
                val diClientId = extractClientIdFromJwt(accessToken) ?: clientId

                log.info("Garmin DI token obtained — clientId={}, expires in {}s", diClientId, expiresIn)
                return GarminSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    diClientId = diClientId,
                    accessTokenExpiresAt = Instant.now().plusSeconds(expiresIn),
                    refreshTokenExpiresAt = Instant.now().plusSeconds(refreshExpiresIn),
                )
            } catch (e: Exception) {
                log.debug("DI token exchange failed for clientId={}: {}", clientId, e.message)
            }
        }
        throw IllegalStateException("DI token exchange failed for all client IDs: ${DI_CLIENT_IDS.toList()}")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private fun applyNativeHeaders(headers: HttpHeaders) {
        headers[HttpHeaders.USER_AGENT] = NATIVE_USER_AGENT
        headers["X-Garmin-User-Agent"] = NATIVE_X_GARMIN_USER_AGENT
        headers["X-Garmin-Paired-App-Version"] = "10861"
        headers["X-Garmin-Client-Platform"] = "Android"
        headers["X-App-Ver"] = "10861"
        headers["X-Lang"] = "en"
        headers["X-GCExperience"] = "GC5"
        headers[HttpHeaders.ACCEPT_LANGUAGE] = "en-US,en;q=0.9"
        headers[HttpHeaders.ACCEPT] = "application/json,text/html;q=0.9,*/*;q=0.8"
    }

    private fun basicAuth(clientId: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$clientId:".toByteArray(Charsets.UTF_8))

    private fun extractClientIdFromJwt(token: String): String? =
        try {
            val payload = token.split(".").getOrNull(1) ?: return null
            val padded = payload + "=".repeat(-payload.length and 3)
            val decoded = String(Base64.getUrlDecoder().decode(padded), Charsets.UTF_8)
            objectMapper.readTree(decoded).path("client_id").asText().ifBlank { null }
        } catch (_: Exception) {
            null
        }

    private companion object {
        val DI_CLIENT_IDS =
            arrayOf(
                "GARMIN_CONNECT_MOBILE_ANDROID_DI_2025Q2",
                "GARMIN_CONNECT_MOBILE_ANDROID_DI_2024Q4",
                "GARMIN_CONNECT_MOBILE_ANDROID_DI",
                "GARMIN_CONNECT_MOBILE_IOS_DI",
            )

        const val DI_GRANT_TYPE =
            "https://connectapi.garmin.com/di-oauth2-service/oauth/grant/service_ticket"

        const val IOS_LOGIN_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148"

        const val NATIVE_USER_AGENT = "GCM-Android-5.23"
        const val NATIVE_X_GARMIN_USER_AGENT =
            "com.garmin.android.apps.connectmobile/5.23; ; Google/sdk_gphone64_arm64/google; Android/33; Dalvik/2.1.0"
    }
}
