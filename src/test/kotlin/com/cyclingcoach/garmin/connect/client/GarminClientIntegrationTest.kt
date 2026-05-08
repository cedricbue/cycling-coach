package com.cyclingcoach.garmin.connect.client

import com.cyclingcoach.AbstractApplicationIntegrationTest
import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@Tag("integration")
class GarminClientIntegrationTest : AbstractApplicationIntegrationTest() {
    @Autowired
    private lateinit var garminClient: GarminConnect

    @Autowired
    private lateinit var garminConfig: GarminConfig

    @BeforeEach
    fun resetSession() {
        garminTokenStore.deleteAll()
    }

    @Test
    fun `login posts credentials and exchanges ticket for DI token`() {
        val tokens = garminClient.login("user@example.com", "secret")

        Assertions.assertThat(tokens.accessToken).isEqualTo("test-access-token")
        Assertions.assertThat(tokens.refreshToken).isEqualTo("test-refresh-token")
        Assertions.assertThat(tokens.diClientId).isNotBlank()
        Assertions.assertThat(tokens.isExpired()).isFalse()
        Assertions.assertThat(tokens.isRefreshTokenExpired()).isFalse()

        // Verify the SSO login was called with the right query params
        wireMock.verify(
            WireMock
                .postRequestedFor(WireMock.urlPathEqualTo("/mobile/api/login"))
                .withQueryParam("clientId", WireMock.equalTo(garminConfig.ssoClientId))
                .withQueryParam("service", WireMock.equalTo(garminConfig.ssoServiceUrl)),
        )

        // Verify DI token exchange was called
        wireMock.verify(
            WireMock
                .postRequestedFor(WireMock.urlPathEqualTo("/di-oauth2-service/oauth/token"))
                .withRequestBody(
                    WireMock
                        .containing("service_ticket=ST-TEST-TICKET"),
                ).withRequestBody(
                    WireMock
                        .containing("grant_type="),
                ).withRequestBody(
                    WireMock
                        .containing("service_url="),
                ),
        )
    }

    @Test
    fun `login tries next DI client ID when first one fails`() {
        wireMock.stubFor(
            WireMock
                .post(WireMock.urlPathMatching("/di-oauth2-service/oauth/token"))
                .inScenario("di-fallback")
                .whenScenarioStateIs("Started")
                .willReturn(WireMock.aResponse().withStatus(400).withBody("invalid client"))
                .willSetStateTo("first-failed"),
        )
        wireMock.stubFor(
            WireMock
                .post(WireMock.urlPathMatching("/di-oauth2-service/oauth/token"))
                .inScenario("di-fallback")
                .whenScenarioStateIs("first-failed")
                .willReturn(
                    WireMock
                        .aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"access_token":"fallback-token","refresh_token":"fallback-refresh","expires_in":3600}"""),
                ),
        )

        val tokens = garminClient.login("user@example.com", "secret")

        Assertions.assertThat(tokens.accessToken).isEqualTo("fallback-token")
    }

    @Test
    fun `login throws on invalid credentials`() {
        wireMock.stubFor(
            WireMock
                .post(WireMock.urlPathMatching("/mobile/api/login"))
                .willReturn(
                    WireMock
                        .aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"responseStatus":{"type":"INVALID_USERNAME_PASSWORD"}}"""),
                ),
        )

        Assertions
            .assertThatThrownBy { garminClient.login("bad@user.com", "wrong") }
            .isInstanceOf(GarminAuthException::class.java)
            .hasMessageContaining("Invalid Garmin username or password")
    }

    @Test
    fun `login throws descriptive error when MFA is required`() {
        wireMock.stubFor(
            WireMock
                .post(WireMock.urlPathMatching("/mobile/api/login"))
                .willReturn(
                    WireMock
                        .aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"responseStatus":{"type":"MFA_REQUIRED"}}"""),
                ),
        )

        Assertions
            .assertThatThrownBy { garminClient.login("mfa@user.com", "secret") }
            .isInstanceOf(GarminMfaRequiredException::class.java)
            .hasMessageContaining("MFA")
    }

    @Test
    fun `getActivities forwards start offset in request`() {
        garminClient.login("user@example.com", "secret")
        wireMock.stubFor(
            WireMock
                .get(WireMock.urlPathMatching("/activitylist-service/activities/search/activities"))
                .withQueryParam("start", WireMock.equalTo("50"))
                .willReturn(
                    WireMock
                        .aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]"),
                ),
        )

        val result = garminClient.getActivities(start = 50)

        Assertions.assertThat(result).isEmpty()
        wireMock.verify(
            WireMock
                .getRequestedFor(WireMock.urlPathMatching("/activitylist-service/activities/search/activities"))
                .withQueryParam("start", WireMock.equalTo("50")),
        )
    }

    @Test
    fun `refreshToken exchanges refresh token for new access token`() {
        val original = garminClient.login("user@example.com", "secret")

        wireMock.stubFor(
            WireMock
                .post(WireMock.urlPathMatching("/di-oauth2-service/oauth/token"))
                .willReturn(
                    WireMock
                        .aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"access_token":"refreshed-token","refresh_token":"new-refresh","expires_in":3600}"""),
                ),
        )

        val refreshed = garminClient.refreshToken(original)

        Assertions.assertThat(refreshed.accessToken).isEqualTo("refreshed-token")
        Assertions.assertThat(refreshed.refreshToken).isEqualTo("new-refresh")
        Assertions.assertThat(refreshed.diClientId).isEqualTo(original.diClientId)

        wireMock.verify(
            WireMock
                .postRequestedFor(WireMock.urlPathEqualTo("/di-oauth2-service/oauth/token"))
                .withRequestBody(
                    WireMock
                        .containing("grant_type=refresh_token"),
                ).withRequestBody(
                    WireMock
                        .containing("refresh_token=test-refresh-token"),
                ),
        )
    }
}
