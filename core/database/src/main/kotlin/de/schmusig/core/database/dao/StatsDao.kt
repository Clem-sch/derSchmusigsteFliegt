package de.schmusig.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import de.schmusig.core.database.entity.QuestionStatsEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {

    @Query("SELECT * FROM question_stats WHERE questionId = :questionId")
    suspend fun find(questionId: String): QuestionStatsEntity?

    @Query("SELECT * FROM question_stats")
    suspend fun loadAll(): List<QuestionStatsEntity>

    @Query("SELECT * FROM question_stats WHERE questionId IN (:ids)")
    suspend fun loadByIds(ids: List<String>): List<QuestionStatsEntity>

    @Query(
        "SELECT qs.questionId FROM question_stats qs " +
            "WHERE qs.lastPlayedAt IS NOT NULL AND qs.lastPlayedAt >= :since",
    )
    suspend fun idsPlayedSince(since: Instant): List<String>

    @Query(
        "SELECT q.category AS category, COUNT(*) AS count FROM question_stats qs " +
            "JOIN questions q ON q.id = qs.questionId " +
            "WHERE q.deletedAt IS NULL AND qs.timesPlayed > 0 " +
            "GROUP BY q.category ORDER BY q.category COLLATE NOCASE ASC",
    )
    fun observePlayedPerCategory(): Flow<List<CategoryCountRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: List<QuestionStatsEntity>)

    @Query("DELETE FROM question_stats")
    suspend fun deleteAll()

    /**
     * Fortschreiben der Statistik beim Abschliessen einer Runde (8.4).
     *
     * Bewusst lesen-rechnen-schreiben in einer Transaktion statt eines `INSERT ... ON
     * CONFLICT`-Einzeilers: so steht die Rechenregel in Kotlin und ist im Test der
     * Session-Engine nachvollziehbar.
     */
    @Transaction
    suspend fun applyRoundResult(playedCorrect: Map<String, Int>, playedAt: Instant) {
        if (playedCorrect.isEmpty()) return
        val existing = loadByIds(playedCorrect.keys.toList()).associateBy { it.questionId }
        val updated = playedCorrect.map { (questionId, correctCount) ->
            val previous = existing[questionId]
            QuestionStatsEntity(
                questionId = questionId,
                timesPlayed = (previous?.timesPlayed ?: 0) + 1,
                timesCorrect = (previous?.timesCorrect ?: 0) + correctCount,
                lastPlayedAt = playedAt,
            )
        }
        upsert(updated)
    }
}
