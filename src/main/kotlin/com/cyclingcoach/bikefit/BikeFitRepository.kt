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
    val landmarksJson: String?,
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

    fun updateDone(id: String, report: LandmarksReport, landmarksJson: String) {
        dsl.update(BIKE_FIT_ANALYSIS)
            .set(BIKE_FIT_ANALYSIS.STATUS, "DONE")
            .set(BIKE_FIT_ANALYSIS.POSE_SCHEMA, report.poseSchema)
            .set(BIKE_FIT_ANALYSIS.FPS, report.fps.toFloat())
            .set(BIKE_FIT_ANALYSIS.TOTAL_FRAMES, report.totalFrames)
            .set(BIKE_FIT_ANALYSIS.LANDMARKS_JSON, landmarksJson)
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

    fun findAll(): List<BikeFitRow> =
        dsl.select(
            BIKE_FIT_ANALYSIS.ID,
            BIKE_FIT_ANALYSIS.STATUS,
            BIKE_FIT_ANALYSIS.VIDEO_PATH,
            BIKE_FIT_ANALYSIS.ORIGINAL_FILENAME,
            BIKE_FIT_ANALYSIS.POSE_MODEL,
            BIKE_FIT_ANALYSIS.POSE_SCHEMA,
            BIKE_FIT_ANALYSIS.FPS,
            BIKE_FIT_ANALYSIS.TOTAL_FRAMES,
            BIKE_FIT_ANALYSIS.ERROR_MESSAGE,
            BIKE_FIT_ANALYSIS.CREATED_AT,
            BIKE_FIT_ANALYSIS.COMPLETED_AT,
        )
            .from(BIKE_FIT_ANALYSIS)
            .orderBy(BIKE_FIT_ANALYSIS.CREATED_AT.desc())
            .fetch()
            .map { r ->
                BikeFitRow(
                    id = r[BIKE_FIT_ANALYSIS.ID]!!,
                    status = r[BIKE_FIT_ANALYSIS.STATUS]!!,
                    videoPath = r[BIKE_FIT_ANALYSIS.VIDEO_PATH]!!,
                    originalFilename = r[BIKE_FIT_ANALYSIS.ORIGINAL_FILENAME]!!,
                    poseModel = r[BIKE_FIT_ANALYSIS.POSE_MODEL]!!,
                    poseSchema = r[BIKE_FIT_ANALYSIS.POSE_SCHEMA],
                    fps = r[BIKE_FIT_ANALYSIS.FPS]?.toDouble(),
                    totalFrames = r[BIKE_FIT_ANALYSIS.TOTAL_FRAMES],
                    landmarksJson = null,
                    errorMessage = r[BIKE_FIT_ANALYSIS.ERROR_MESSAGE],
                    createdAt = parseDateTime(r[BIKE_FIT_ANALYSIS.CREATED_AT]!!),
                    completedAt = r[BIKE_FIT_ANALYSIS.COMPLETED_AT]?.let { parseDateTime(it) },
                )
            }

    fun findById(id: String): BikeFitRow? =
        dsl.selectFrom(BIKE_FIT_ANALYSIS)
            .where(BIKE_FIT_ANALYSIS.ID.eq(id))
            .fetchOne()
            ?.let { r ->
                BikeFitRow(
                    id = r.id!!,
                    status = r.status!!,
                    videoPath = r.videoPath!!,
                    originalFilename = r.originalFilename!!,
                    poseModel = r.poseModel!!,
                    poseSchema = r.poseSchema,
                    fps = r.fps?.toDouble(),
                    totalFrames = r.totalFrames,
                    landmarksJson = r.landmarksJson,
                    errorMessage = r.errorMessage,
                    createdAt = parseDateTime(r.createdAt!!),
                    completedAt = r.completedAt?.let { parseDateTime(it) },
                )
            }

    private fun parseDateTime(s: String): OffsetDateTime =
        runCatching { OffsetDateTime.parse(s) }
            .getOrElse { OffsetDateTime.parse("${s}Z") }
}
