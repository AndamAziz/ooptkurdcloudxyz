package xyz.kurdcloud.englishmaster.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {
    @Upsert
    suspend fun upsertAll(questions: List<QuestionEntity>)

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun byId(id: String): QuestionEntity?

    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<QuestionEntity>

    /** Random questions for a level, preferring ones this user has not answered yet. */
    @Query(
        """SELECT q.* FROM questions q
           WHERE q.cefr_level = :level
           ORDER BY EXISTS (SELECT 1 FROM responses r WHERE r.question_id = q.id AND r.user_id = :userId), RANDOM()
           LIMIT :limit"""
    )
    suspend fun pickForSession(level: String, userId: String, limit: Int): List<QuestionEntity>

    @Query("DELETE FROM questions WHERE cefr_level = :level")
    suspend fun deleteLevel(level: String)

    /** This user's marked practice answers on downloaded questions (the input of the review schedule). */
    @Query(ANSWER_HISTORY)
    suspend fun answerHistory(userId: String): List<AnswerRow>

    @Query(ANSWER_HISTORY)
    fun observeAnswerHistory(userId: String): Flow<List<AnswerRow>>

    /**
     * Random questions from every downloaded level that match a topic, preferring unanswered ones.
     * [t1]..[t4] are LIKE patterns on the JSON tags column (unused ones are passed as "").
     */
    @Query(
        """SELECT q.* FROM questions q WHERE $TOPIC_MATCH
           ORDER BY EXISTS (SELECT 1 FROM responses r WHERE r.question_id = q.id AND r.user_id = :userId), RANDOM()
           LIMIT :limit"""
    )
    suspend fun pickForTopic(
        t1: String, t2: String, t3: String, t4: String,
        withPassage: Boolean, withMedia: Boolean, userId: String, limit: Int,
    ): List<QuestionEntity>

    @Query("SELECT cefr_level, COUNT(*) AS count, MAX(server_updated_at) AS last_changed FROM questions GROUP BY cefr_level")
    suspend fun levelVersions(): List<LevelVersionRow>

    @Query("SELECT COUNT(*) FROM questions q WHERE $TOPIC_MATCH")
    fun observeTopicCount(t1: String, t2: String, t3: String, t4: String, withPassage: Boolean, withMedia: Boolean): Flow<Int>

    private companion object {
        const val TOPIC_MATCH = """(q.tags LIKE :t1 OR q.tags LIKE :t2 OR q.tags LIKE :t3 OR q.tags LIKE :t4
            OR (:withPassage AND q.passage IS NOT NULL) OR (:withMedia AND q.media_path IS NOT NULL))"""
        const val ANSWER_HISTORY = """SELECT r.question_id AS question_id, q.cefr_level AS cefr_level,
            r.is_correct AS is_correct, r.answered_at AS answered_at
            FROM responses r JOIN questions q ON q.id = r.question_id
            WHERE r.user_id = :userId AND r.sync_state = 'SYNCED' AND r.is_correct IS NOT NULL"""
    }
}

@Dao
interface LevelPackDao {
    @Upsert
    suspend fun upsert(pack: LevelPackEntity)

    @Query("SELECT * FROM level_packs ORDER BY cefr_level")
    fun observeAll(): Flow<List<LevelPackEntity>>

    @Query("SELECT * FROM level_packs")
    suspend fun all(): List<LevelPackEntity>

    @Query("DELETE FROM level_packs WHERE cefr_level = :level")
    suspend fun delete(level: String)
}

@Dao
interface ResponseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(response: ResponseEntity)

    @Query("SELECT * FROM responses WHERE response_id = :id")
    suspend fun byId(id: String): ResponseEntity?

    @Query("SELECT * FROM responses WHERE attempt_id = :attemptId ORDER BY answered_at")
    fun observeAttempt(attemptId: String): Flow<List<ResponseEntity>>

    @Query("SELECT * FROM responses WHERE user_id = :userId AND sync_state = 'PENDING' ORDER BY answered_at LIMIT :limit")
    suspend fun pending(userId: String, limit: Int): List<ResponseEntity>

    @Query("SELECT COUNT(*) FROM responses WHERE user_id = :userId AND sync_state = 'PENDING'")
    fun observePendingCount(userId: String): Flow<Int>

    @Query(
        """UPDATE responses SET sync_state = 'SYNCED', is_correct = :isCorrect, correct_option = :correctOption,
           explanation = :explanation, synced_at = :syncedAt, failure_reason = NULL
           WHERE response_id = :responseId AND sync_state != 'SYNCED'"""
    )
    suspend fun markSynced(responseId: String, isCorrect: Boolean, correctOption: String, explanation: String?, syncedAt: Long)

    @Query("UPDATE responses SET sync_state = 'FAILED', failure_reason = :reason WHERE response_id = :responseId")
    suspend fun markFailed(responseId: String, reason: String)

    @Query("UPDATE responses SET sync_attempts = sync_attempts + 1 WHERE response_id = :responseId")
    suspend fun countSyncAttempt(responseId: String)

    /** Progress from answers the server has scored (unsynchronised answers are not counted). */
    @Query(
        """SELECT cefr_level, COUNT(*) AS answered, SUM(CASE WHEN is_correct THEN 1 ELSE 0 END) AS correct
           FROM responses WHERE user_id = :userId AND sync_state = 'SYNCED'
           GROUP BY cefr_level ORDER BY cefr_level"""
    )
    fun observeProgress(userId: String): Flow<List<LevelProgressRow>>
}

@Dao
interface ContentDao {
    @Query("DELETE FROM questions WHERE cefr_level = :level")
    suspend fun deleteQuestions(level: String)

    @Upsert
    suspend fun upsertQuestions(questions: List<QuestionEntity>)

    @Upsert
    suspend fun upsertPack(pack: LevelPackEntity)

    /**
     * Replaces a level's questions and pack record in one transaction, so an interrupted
     * download never leaves a partial pack. Responses are unaffected (no foreign key).
     */
    @Transaction
    suspend fun replaceLevel(level: String, questions: List<QuestionEntity>, pack: LevelPackEntity) {
        deleteQuestions(level)
        upsertQuestions(questions)
        upsertPack(pack)
    }
}
