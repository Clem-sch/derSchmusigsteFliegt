package de.schmusig.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import de.schmusig.core.database.entity.QuestionEntity
import de.schmusig.core.database.entity.QuestionFtsEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Kategorie mit Bestandszahl, direkt aus einer Gruppierung gelesen. */
data class CategoryCountRow(val category: String, val count: Int)

@Dao
interface QuestionDao {

    // ------------------------------------------------------------------ Lesen

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun findById(id: String): QuestionEntity?

    @Query("SELECT * FROM questions WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: String): Flow<QuestionEntity?>

    /** Fuer die Dubletten-Warnung im Editor (7.3). Tombstones zaehlen nicht. */
    @Query("SELECT * FROM questions WHERE contentHash = :hash AND deletedAt IS NULL LIMIT 1")
    suspend fun findLiveByContentHash(hash: String): QuestionEntity?

    /** Vollstaendiger Bestand inklusive Tombstones - Basis fuer Export und Merge. */
    @Query("SELECT * FROM questions ORDER BY createdAt ASC")
    suspend fun loadAllIncludingDeleted(): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    suspend fun loadByIds(ids: List<String>): List<QuestionEntity>

    @Query("SELECT COUNT(*) FROM questions WHERE deletedAt IS NULL")
    fun observeLiveCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT category) FROM questions WHERE deletedAt IS NULL")
    fun observeCategoryCount(): Flow<Int>

    @Query(
        "SELECT category AS category, COUNT(*) AS count FROM questions " +
            "WHERE deletedAt IS NULL GROUP BY category ORDER BY category COLLATE NOCASE ASC",
    )
    fun observeCategoryCounts(): Flow<List<CategoryCountRow>>

    @Query(
        "SELECT DISTINCT category FROM questions WHERE deletedAt IS NULL " +
            "ORDER BY category COLLATE NOCASE ASC",
    )
    fun observeCategories(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun countAllIncludingDeleted(): Int

    @Query("SELECT COUNT(*) FROM questions WHERE deletedAt IS NOT NULL")
    suspend fun countTombstones(): Int

    @Query("SELECT COUNT(*) FROM questions WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun countTombstonesOlderThan(before: Instant): Int

    // Filter und Sortierung des Katalogs sind dynamisch; die SQL dafuer baut
    // `CatalogQueryBuilder` in :core:data.
    @RawQuery(observedEntities = [QuestionEntity::class, QuestionFtsEntity::class])
    fun pagingSource(query: SupportSQLiteQuery): PagingSource<Int, QuestionEntity>

    @RawQuery(observedEntities = [QuestionEntity::class, QuestionFtsEntity::class])
    fun observeMatchCount(query: SupportSQLiteQuery): Flow<Int>

    @RawQuery
    suspend fun selectIds(query: SupportSQLiteQuery): List<String>

    @RawQuery
    suspend fun countMatches(query: SupportSQLiteQuery): Int

    // --------------------------------------------------------------- Schreiben

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Query("DELETE FROM questions")
    suspend fun deleteAllQuestions()

    @Query("DELETE FROM questions WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeTombstonesOlderThan(before: Instant): Int

    // ------------------------------------------------------------------- FTS
    //
    // Der Index wird immer aus der Fragentabelle abgeleitet, nie unabhaengig gepflegt:
    // so kann er nicht auseinanderlaufen. Alle Schreibwege unten laufen in einer
    // Transaktion zusammen mit der Aenderung an `questions`.

    @Insert
    suspend fun insertFtsRows(rows: List<QuestionFtsEntity>)

    @Query("DELETE FROM questions_fts")
    suspend fun clearFts()

    @Query("DELETE FROM questions_fts WHERE questionId = :id")
    suspend fun deleteFtsRow(id: String)

    @Query(
        "SELECT id AS questionId, question, answer, category, tags FROM questions " +
            "WHERE deletedAt IS NULL",
    )
    suspend fun ftsRowsForAllLive(): List<QuestionFtsEntity>

    @Query(
        "SELECT id AS questionId, question, answer, category, tags FROM questions " +
            "WHERE id = :id AND deletedAt IS NULL",
    )
    suspend fun ftsRowFor(id: String): QuestionFtsEntity?

    @Transaction
    suspend fun reindexAll() {
        clearFts()
        insertFtsRows(ftsRowsForAllLive())
    }

    /** Eine Frage anlegen, aendern oder per Tombstone ausblenden - Index inklusive. */
    @Transaction
    suspend fun upsert(question: QuestionEntity) {
        insertQuestions(listOf(question))
        deleteFtsRow(question.id)
        ftsRowFor(question.id)?.let { insertFtsRows(listOf(it)) }
    }

    /**
     * Schreibt einen Merge-Plan in **einer** Transaktion (Abschnitt 6.6). Entweder liegt
     * danach alles aus dem Plan in der Datenbank oder nichts davon.
     */
    @Transaction
    suspend fun applyPlan(
        inserts: List<QuestionEntity>,
        updates: List<QuestionEntity>,
        replaceEverything: Boolean,
    ) {
        if (replaceEverything) deleteAllQuestions()
        if (inserts.isNotEmpty()) insertQuestions(inserts)
        if (updates.isNotEmpty()) insertQuestions(updates)
        reindexAll()
    }

    @Transaction
    suspend fun purgeTombstones(before: Instant): Int {
        val removed = purgeTombstonesOlderThan(before)
        reindexAll()
        return removed
    }
}
