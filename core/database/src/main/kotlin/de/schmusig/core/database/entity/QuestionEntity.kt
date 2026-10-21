package de.schmusig.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "questions",
    indices = [
        Index("category"),
        Index("difficulty"),
        Index("contentHash"),
        Index("deletedAt"),
    ],
)
data class QuestionEntity(
    @PrimaryKey val id: String,
    val contentHash: String,
    val question: String,
    val answer: String,
    val category: String,
    val difficulty: Int,
    val tags: List<String>,
    val distractors: List<String>,
    val flagged: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val revision: Int,
    val deletedAt: Instant?,
)

/**
 * Volltextindex ueber Frage, Antwort, Kategorie und Tags (Abschnitt 5.1).
 *
 * Eigenstaendige FTS4-Tabelle statt `contentEntity`: der Primaerschluessel von
 * [QuestionEntity] ist eine UUID und laesst sich nicht auf die rowid einer
 * External-Content-Tabelle abbilden. Stattdessen wird der Index in denselben
 * Transaktionen wie die Fragen selbst aus der Fragentabelle neu befuellt
 * (siehe `QuestionDao.reindexAll` / `reindexOne`), bleibt also abgeleitet statt
 * doppelt gepflegt.
 *
 * `questionId` ist nicht indexiert - es ist der Rueckverweis, nicht Suchtext.
 */
@Entity(tableName = "questions_fts")
@Fts4(notIndexed = ["questionId"])
data class QuestionFtsEntity(
    @ColumnInfo(name = "questionId") val questionId: String,
    val question: String,
    val answer: String,
    val category: String,
    val tags: String,
)
