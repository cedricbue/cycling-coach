package com.cyclingcoach.garmin.connect.weight

import com.cyclingcoach.generated.jooq.tables.references.GARMIN_WEIGHT
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class GarminWeightRepository(
    private val dsl: DSLContext,
) {
    fun batchUpsert(inputs: List<Pair<String, String>>) {
        if (inputs.isEmpty()) return
        dsl
            .batch(
                inputs.map { (externalId, rawJson) ->
                    dsl
                        .insertInto(GARMIN_WEIGHT)
                        .set(GARMIN_WEIGHT.EXTERNAL_ID, externalId)
                        .set(GARMIN_WEIGHT.RAW_JSON, rawJson)
                        .onConflict(GARMIN_WEIGHT.EXTERNAL_ID)
                        .doUpdate()
                        .set(GARMIN_WEIGHT.RAW_JSON, rawJson)
                },
            ).execute()
    }
}
