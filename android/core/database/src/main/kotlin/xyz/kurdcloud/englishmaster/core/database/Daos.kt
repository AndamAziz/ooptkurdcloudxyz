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
}

@Dao
interface LevelPackDao {
    @Upsert
    suspend fun upsert(pack: LevelPackEntity)

    @Query("SELECT * FROM level_packs ORDER BY cefr_level")
    fun observeAll(): Flow<List<LevelPackEntity>>

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
