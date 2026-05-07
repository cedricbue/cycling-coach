package com.cyclingcoach.sync

import com.cyclingcoach.ApplicationContextTest
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@Tag("integration")
class GarminAuthClientIntegrationTest : ApplicationContextTest() {

    @Autowired
    private lateinit var garminAuthClient: GarminAuthClient

    @Autowired
    private lateinit var garminProperties: GarminProperties

    @BeforeEach
    fun resetSession() {
        garminSessionRepository.deleteAll()
    }

    @Test
    fun `authenticate posts credentials and exchanges ticket for DI token`() {
        val session = garminAuthClient.authenticate("user@example.com", "secret")

        assertThat(session.accessToken).isEqualTo("test-access-token")
        assertThat(session.refreshToken).isEqualTo("test-refresh-token")
        assertThat(session.diClientId).isNotBlank()
        assertThat(session.isExpired()).isFalse()
        assertThat(session.isRefreshTokenExpired()).isFalse()

        // Verify the SSO login was called with the right query params
        wireMock.verify(
            postRequestedFor(urlPathEqualTo("/mobile/api/login"))
                .withQueryParam("clientId", equalTo(garminProperties.sso.clientId))
                .withQueryParam("service", equalTo(garminProperties.sso.serviceUrl)),
        )

        // Verify DI token exchange was called
        wireMock.verify(
            postRequestedFor(urlPathEqualTo("/di-oauth2-service/oauth/token"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("service_ticket=ST-TEST-TICKET"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("grant_type="))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("service_url=")),
        )
    }

    @Test
    fun `authenticate tries next DI client ID when first one fails`() {
        wireMock.stubFor(
            post(urlPathMatching("/di-oauth2-service/oauth/token"))
                .inScenario("di-fallback")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(400).withBody("invalid client"))
                .willSetStateTo("first-failed"),
        )
        wireMock.stubFor(
            post(urlPathMatching("/di-oauth2-service/oauth/token"))
                .inScenario("di-fallback")
                .whenScenarioStateIs("first-failed")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"access_token":"fallback-token","refresh_token":"fallback-refresh","expires_in":3600}"""),
                ),
        )

        val session = garminAuthClient.authenticate("user@example.com", "secret")

        assertThat(session.accessToken).isEqualTo("fallback-token")
    }

    @Test
    fun `authenticate throws on invalid credentials`() {
        wireMock.stubFor(
            post(urlPathMatching("/mobile/api/login"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"responseStatus":{"type":"INVALID_USERNAME_PASSWORD"}}"""),
                ),
        )

        assertThatThrownBy { garminAuthClient.authenticate("bad@user.com", "wrong") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid Garmin username or password")
    }

    @Test
    fun `authenticate throws descriptive error when MFA is required`() {
        wireMock.stubFor(
            post(urlPathMatching("/mobile/api/login"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"responseStatus":{"type":"MFA_REQUIRED"}}"""),
                ),
        )

        assertThatThrownBy { garminAuthClient.authenticate("mfa@user.com", "secret") }
            .isInstanceOf(UnsupportedOperationException::class.java)
            .hasMessageContaining("MFA")
    }

    @Test
    fun `refreshToken exchanges refresh token for new access token`() {
        val original = garminAuthClient.authenticate("user@example.com", "secret")

        wireMock.stubFor(
            post(urlPathMatching("/di-oauth2-service/oauth/token"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"access_token":"refreshed-token","refresh_token":"new-refresh","expires_in":3600}"""),
                ),
        )

        val refreshed = garminAuthClient.refreshToken(original)

        assertThat(refreshed.accessToken).isEqualTo("refreshed-token")
        assertThat(refreshed.refreshToken).isEqualTo("new-refresh")
        assertThat(refreshed.diClientId).isEqualTo(original.diClientId)

        wireMock.verify(
            postRequestedFor(urlPathEqualTo("/di-oauth2-service/oauth/token"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("grant_type=refresh_token"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.containing("refresh_token=test-refresh-token")),
        )
    }
}
