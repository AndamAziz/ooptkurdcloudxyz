package xyz.kurdcloud.englishmaster.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Downloaded question content. Answer keys are never stored on the device. */
@Entity(tableName = "questions", indices = [Index("cefr_level")])
data class QuestionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "cefr_level") val cefrLevel: String,
    val skill: String,
    val prompt: String,
    val options: List<String>,
    val version: Int,
    @ColumnInfo(name = "review_status") val reviewStatus: String,
    @ColumnInfo(name = "server_updated_at") val serverUpdatedAt: String,
    @ColumnInfo(name = "downloaded_at") val downloadedAtMillis: Long,
    val passage: String? = null,
    @ColumnInfo(name = "media_type") val mediaType: String? = null,
    /** Object path in the question-media bucket. */
    @ColumnInfo(name = "media_path") val mediaPath: String? = null,
    @ColumnInfo(name = "media_caption") val mediaCaption: String? = null,
    /** Absolute path of the copy saved with the pack (pictures), for offline use. */
    @ColumnInfo(name = "media_local") val mediaLocal: String? = null,
    /** Content tags (JSON list) used for topic practice, e.g. ["life_in_uk","history"]. */
    @ColumnInfo(defaultValue = "[]") val tags: List<String> = emptyList(),
)

/** A completed download of one level's question pack. */
@Entity(tableName = "level_packs")
data class LevelPackEntity(
    @PrimaryKey @ColumnInfo(name = "cefr_level") val cefrLevel: String,
    @ColumnInfo(name = "question_count") val questionCount: Int,
    @ColumnInfo(name = "downloaded_at") val downloadedAtMillis: Long,
)

/**
 * A learner's answer. Written locally first (PENDING) and updated with the server's verdict
 * once synchronised. [responseId] is generated on the device and doubles as the server-side
 * idempotency key, so retries can never create duplicates.
 */
@Entity(
    tableName = "responses",
    indices = [Index("user_id", "sync_state"), Index("attempt_id")],
)
data class ResponseEntity(
    @PrimaryKey @ColumnInfo(name = "response_id") val responseId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "attempt_id") val attemptId: String,
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "cefr_level") val cefrLevel: String,
    @ColumnInfo(name = "chosen_option") val chosenOption: String,
    @ColumnInfo(name = "answered_at") val answeredAtMillis: Long,
    @ColumnInfo(name = "sync_state") val syncState: String,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean? = null,
    @ColumnInfo(name = "correct_option") val correctOption: String? = null,
    val explanation: String? = null,
    @ColumnInfo(name = "failure_reason") val failureReason: String? = null,
    @ColumnInfo(name = "sync_attempts") val syncAttempts: Int = 0,
    @ColumnInfo(name = "synced_at") val syncedAtMillis: Long? = null,
)

data class LevelProgressRow(
    @ColumnInfo(name = "cefr_level") val cefrLevel: String,
    val answered: Int,
    val correct: Int,
)

/** Questions saved for a level and the newest server change among them. */
data class LevelVersionRow(
    @ColumnInfo(name = "cefr_level") val cefrLevel: String,
    val count: Int,
    @ColumnInfo(name = "last_changed") val lastChanged: String?,
)

/** One marked practice answer, with the level of its (downloaded) question. */
data class AnswerRow(
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "cefr_level") val cefrLevel: String,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean,
    @ColumnInfo(name = "answered_at") val answeredAtMillis: Long,
)

data class LevelCountRow(
    @ColumnInfo(name = "cefr_level") val cefrLevel: String,
    val count: Int,
)
