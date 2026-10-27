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
    /** Sorani translation (v4): the question, the options as a JSON list (null entries allowed), the passage. */
    @ColumnInfo(name = "prompt_ckb") val promptCkb: String? = null,
    @ColumnInfo(name = "options_ckb") val optionsCkbJson: String? = null,
    @ColumnInfo(name = "passage_ckb") val passageCkb: String? = null,
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
    @ColumnInfo(name = "explanation_ckb") val explanationCkb: String? = null,
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

/**
 * A chapter of the driving theory test, as last fetched from the server. [serverAnswered] and
 * [serverCorrect] are that fetch's standing; what the learner has answered since is in
 * [TheoryResponseEntity], so progress still moves while offline.
 */
@Entity(tableName = "theory_sections")
data class TheorySectionEntity(
    @PrimaryKey val slug: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "name_ckb") val nameCkb: String? = null,
    val published: Int,
    @ColumnInfo(name = "server_answered") val serverAnswered: Int,
    @ColumnInfo(name = "server_correct") val serverCorrect: Int,
    @ColumnInfo(name = "fetched_at") val fetchedAtMillis: Long,
)

/** A downloaded theory question. Unlike practice questions these carry their answer. */
@Entity(tableName = "theory_questions", indices = [Index("section_slug")])
data class TheoryQuestionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "section_slug") val sectionSlug: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val prompt: String,
    @ColumnInfo(name = "prompt_ckb") val promptCkb: String? = null,
    val answer: String,
    @ColumnInfo(name = "answer_ckb") val answerCkb: String? = null,
    val explanation: String? = null,
    @ColumnInfo(name = "explanation_ckb") val explanationCkb: String? = null,
    @ColumnInfo(name = "media_path") val mediaPath: String? = null,
    /** Absolute path of the copy saved with the chapter, for offline use. */
    @ColumnInfo(name = "media_local") val mediaLocal: String? = null,
    val version: Int,
    @ColumnInfo(name = "downloaded_at") val downloadedAtMillis: Long,
)

/**
 * A learner's answer to a theory question. Kept on the device so a chapter's progress survives
 * being offline; [synced] says whether the server has it too.
 */
@Entity(tableName = "theory_responses", indices = [Index("question_id"), Index("synced")])
data class TheoryResponseEntity(
    @PrimaryKey @ColumnInfo(name = "response_id") val responseId: String,
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "section_slug") val sectionSlug: String,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean,
    val mode: String,
    @ColumnInfo(name = "answered_at") val answeredAtMillis: Long,
    val synced: Boolean = false,
)

/** A chapter's standing worked out from the answers stored on the device. */
data class TheoryStanding(
    @ColumnInfo(name = "section_slug") val sectionSlug: String,
    val answered: Int,
    val correct: Int,
)

/** The latest verdict on one theory question. */
data class TheoryVerdict(
    @ColumnInfo(name = "question_id") val questionId: String,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean,
)

/** How many of a chapter's questions are saved on the device. */
data class TheorySectionCount(
    @ColumnInfo(name = "section_slug") val sectionSlug: String,
    val count: Int,
)
