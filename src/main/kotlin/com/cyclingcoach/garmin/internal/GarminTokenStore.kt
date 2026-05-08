package com.cyclingcoach.garmin.internal

import com.cyclingcoach.garmin.connect.client.GarminTokens
import com.cyclingcoach.garmin.connect.client.TokenStore
import com.cyclingcoach.generated.jooq.tables.references.GARMIN_TOKEN
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class GarminTokenStore(
    private val dsl: DSLContext,
) : TokenStore {
    override fun save(tokens: GarminTokens) {
        dsl.deleteFrom(GARMIN_TOKEN).execute()
        dsl
            .insertInto(GARMIN_TOKEN)
            .set(GARMIN_TOKEN.ACCESS_TOKEN, tokens.accessToken)
            .set(GARMIN_TOKEN.REFRESH_TOKEN, tokens.refreshToken)
            .set(GARMIN_TOKEN.DI_CLIENT_ID, tokens.diClientId)
            .set(GARMIN_TOKEN.ACCESS_TOKEN_EXPIRES_AT, tokens.accessTokenExpiresAt.toString())
            .set(GARMIN_TOKEN.REFRESH_TOKEN_EXPIRES_AT, tokens.refreshTokenExpiresAt.toString())
            .execute()
    }

    override fun load(): GarminTokens? {
        val record =
            dsl
                .select(
                    GARMIN_TOKEN.ACCESS_TOKEN,
                    GARMIN_TOKEN.REFRESH_TOKEN,
                    GARMIN_TOKEN.DI_CLIENT_ID,
                    GARMIN_TOKEN.ACCESS_TOKEN_EXPIRES_AT,
                    GARMIN_TOKEN.REFRESH_TOKEN_EXPIRES_AT,
                ).from(GARMIN_TOKEN)
                .orderBy(GARMIN_TOKEN.CREATED_AT.desc())
                .limit(1)
                .fetchOne() ?: return null

        return GarminTokens(
            accessToken = record[GARMIN_TOKEN.ACCESS_TOKEN] ?: return null,
            refreshToken = record[GARMIN_TOKEN.REFRESH_TOKEN] ?: return null,
            diClientId = record[GARMIN_TOKEN.DI_CLIENT_ID] ?: "",
            accessTokenExpiresAt = Instant.parse(record[GARMIN_TOKEN.ACCESS_TOKEN_EXPIRES_AT] ?: return null),
            refreshTokenExpiresAt = Instant.parse(record[GARMIN_TOKEN.REFRESH_TOKEN_EXPIRES_AT] ?: return null),
        )
    }

    override fun delete() {
        dsl.deleteFrom(GARMIN_TOKEN).execute()
    }

    fun deleteAll() = delete()
}
