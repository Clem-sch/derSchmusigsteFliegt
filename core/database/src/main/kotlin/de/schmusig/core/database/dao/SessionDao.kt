package de.schmusig.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.schmusig.core.database.entity.AttemptEntity
import de.schmusig.core.database.entity.GameSessionEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Query("SELECT * FROM game_sessions WHERE id = :id")
    suspend fun findSession(id: String): GameSessionEntity?

    @Query("SELECT * FROM game_sessions WHERE id = :id")
    fun observeSession(id: String): Flow<GameSessionEntity?>

    /** Eine unterbrochene Runde zum Fortsetzen anbieten (8.2). */
    @Query(
        "SELECT * FROM game_sessions WHERE state IN ('RUNNING', 'REVIEW') " +
            "ORDER BY startedAt DESC LIMIT 1",
    )
    fun observeResumable(): Flow<GameSessionEntity?>

    @Query(
        "SELECT * FROM game_sessions WHERE state IN ('RUNNING', 'REVIEW') " +
            "ORDER BY startedAt DESC LIMIT 1",
    )
    suspend fun findResumable(): GameSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: GameSessionEntity)

    @Query("UPDATE game_sessions SET state = :state, endedAt = :endedAt WHERE id = :id")
    suspend fun updateSessionState(id: String, state: String, endedAt: Instant?)

    @Query("DELETE FROM game_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttempt(attempt: AttemptEntity)

    @Query("SELECT * FROM attempts WHERE sessionId = :sessionId ORDER BY position ASC")
    suspend fun attemptsFor(sessionId: String): List<AttemptEntity>

    @Query("SELECT * FROM attempts WHERE sessionId = :sessionId ORDER BY position ASC")
    fun observeAttemptsFor(sessionId: String): Flow<List<AttemptEntity>>

    @Query("SELECT questionId FROM attempts WHERE sessionId = :sessionId")
    suspend fun questionIdsFor(sessionId: String): List<String>

    @Query("UPDATE attempts SET verdict = :verdict WHERE id = :id")
    suspend fun updateVerdict(id: String, verdict: String)

    @Query("SELECT COALESCE(MAX(position), -1) FROM attempts WHERE sessionId = :sessionId")
    suspend fun lastPositionFor(sessionId: String): Int
}
