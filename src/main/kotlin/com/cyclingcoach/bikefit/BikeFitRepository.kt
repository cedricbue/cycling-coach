package com.cyclingcoach.bikefit

import com.cyclingcoach.generated.jooq.tables.BikeFitAnalysis.Companion.BIKE_FIT_ANALYSIS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

data class BikeFitRow(
    val id: String,
    val status: String,
    val videoPath: String,
    val originalFilename: String,
    val poseModel: String,
    val poseSchema: String?,
    val fps: Double?,
    val totalFrames: Int?,
    val landmarksJson: String?,  // JSON loaded from disk on demand; null for list queries
    val errorMessage: String?,
    val createdAt: OffsetDateTime,
    val completedAt: OffsetDateTime?,
)

@Repository
class BikeFitRepository(private val dsl: DSLContext) {

    fun insert(row: BikeFitRow) {
        dsl.insertInto(BIKE_FIT_ANALYSIS)
            .set(BIKE_FIT_ANALYSIS.ID, row.id)
            .set(BIKE_FIT_ANALYSIS.STATUS, row.status)
            .set(BIKE_FIT_ANALYSIS.VIDEO_PATH, row.videoPath)
            .set(BIKE_FIT_ANALYSIS.ORIGINAL_FILENAME, row.originalFilename)
            .set(BIKE_FIT_ANALYSIS.POSE_MODEL, row.poseModel)
            .set(BIKE_FIT_ANALYSIS.POSE_SCHEMA, row.poseSchema)
            .set(BIKE_FIT_ANALYSIS.CREATED_AT, row.createdAt.toString())
            .execute()
    }

    fun updateDone(id: String, report: LandmarksReport) {
        dsl.update(BIKE_FIT_ANALYSIS)
            .set(BIKE_FIT_ANALYSIS.STATUS, "DONE")
            .set(BIKE_FIT_ANALYSIS.POSE_SCHEMA, report.poseSchema)
            .set(BIKE_FIT_ANALYSIS.FPS, report.fps.toFloat())
            .set(BIKE_FIT_ANALYSIS.TOTAL_FRAMES, report.totalFrames)
            .set(BIKE_FIT_ANALYSIS.COMPLETED_AT, OffsetDateTime.now().toString())
            .where(BIKE_FIT_ANALYSIS.ID.eq(id))
            .execute()
    }

    fun updateFailed(id: String, errorMessage: String?) {
        dsl.update(BIKE_FIT_ANALYSIS)
            .set(BIKE_FIT_ANALYSIS.STATUS, "FAILED")
            .set(BIKE_FIT_ANALYSIS.ERROR_MESSAGE, errorMessage)
            .set(BIKE_FIT_ANALYSIS.COMPLETED_AT, OffsetDateTime.now().toString())
            .where(BIKE_FIT_ANALYSIS.ID.eq(id))
            .execute()
    }

    fun resetToProcessing(id: String) {
        dsl.update(BIKE_FIT_ANALYSIS)
            .set(BIKE_FIT_ANALYSIS.STATUS, "PROCESSING")
            .setNull(BIKE_FIT_ANALYSIS.ERROR_MESSAGE)
            .setNull(BIKE_FIT_ANALYSIS.COMPLETED_AT)
            .where(BIKE_FIT_ANALYSIS.ID.eq(id))
            .execute()
    }

    fun findAll(): List<BikeFitRow> =
        dsl.selectFrom(BIKE_FIT_ANALYSIS)
            .orderBy(BIKE_FIT_ANALYSIS.CREATED_AT.desc())
            .fetch()
            .map { r -> r.toRow(landmarksJson = null) }

    fun findById(id: String): BikeFitRow? =
        dsl.selectFrom(BIKE_FIT_ANALYSIS)
            .where(BIKE_FIT_ANALYSIS.ID.eq(id))
            .fetchOne()
            ?.toRow(landmarksJson = null)

    private fun com.cyclingcoach.generated.jooq.tables.records.BikeFitAnalysisRecord.toRow(
        landmarksJson: String?,
    ) = BikeFitRow(
        id = id!!,
        status = status!!,
        videoPath = videoPath!!,
        originalFilename = originalFilename!!,
        poseModel = poseModel!!,
        poseSchema = poseSchema,
        fps = fps?.toDouble(),
        totalFrames = totalFrames,
        landmarksJson = landmarksJson,
        errorMessage = errorMessage,
        createdAt = parseDateTime(createdAt!!),
        completedAt = completedAt?.let { parseDateTime(it) },
    )

    private fun parseDateTime(s: String): OffsetDateTime =
        runCatching { OffsetDateTime.parse(s) }
            .getOrElse { OffsetDateTime.parse("${s}Z") }
}
