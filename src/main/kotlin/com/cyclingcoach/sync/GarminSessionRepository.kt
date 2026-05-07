package com.cyclingcoach.sync

import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class GarminSessionRepository(
    private val dsl: DSLContext,
) {
    // TODO replace dsl table/field creation with already generated jooq constants
    private val table = DSL.table("garmin_session")
    private val accessTokenField = DSL.field("access_token", String::class.java)
    private val refreshTokenField = DSL.field("refresh_token", String::class.java)
    private val diClientIdField = DSL.field("di_client_id", String::class.java)
    private val accessTokenExpiresAtField = DSL.field("access_token_expires_at", String::class.java)
    private val refreshTokenExpiresAtField = DSL.field("refresh_token_expires_at", String::class.java)
    private val createdAtField = DSL.field("created_at", String::class.java)

    fun save(session: GarminSession) {
        dsl.deleteFrom(table).execute()
        dsl
            .insertInto(table)
            .set(accessTokenField, session.accessToken)
            .set(refreshTokenField, session.refreshToken)
            .set(diClientIdField, session.diClientId)
            .set(accessTokenExpiresAtField, session.accessTokenExpiresAt.toString())
            .set(refreshTokenExpiresAtField, session.refreshTokenExpiresAt.toString())
            .execute()
    }

    fun loadLatest(): GarminSession? {
        val record =
            dsl
                .select(accessTokenField, refreshTokenField, diClientIdField, accessTokenExpiresAtField, refreshTokenExpiresAtField)
                .from(table)
                .orderBy(createdAtField.desc())
                .limit(1)
                .fetchOne() ?: return null

        return GarminSession(
            accessToken = record.value1() ?: return null,
            refreshToken = record.value2() ?: return null,
            diClientId = record.value3() ?: "",
            accessTokenExpiresAt = Instant.parse(record.value4() ?: return null),
            refreshTokenExpiresAt = Instant.parse(record.value5() ?: return null),
        )
    }

    fun hasValidSession(): Boolean = loadLatest()?.isExpired()?.not() ?: false

    fun deleteAll() = dsl.deleteFrom(table).execute()
}
