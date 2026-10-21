package de.schmusig.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Gerätelokale Statistik, in einer eigenen Tabelle (Abschnitt 5.1).
 *
 * Kein Foreign Key auf `questions`: der Zaehler soll einen RESTORE und das Aufraeumen von
 * Tombstones ueberleben, ohne dass die Datenbank Zeilen hinter unserem Ruecken loescht.
 */
@Entity(tableName = "question_stats")
data class QuestionStatsEntity(
    @PrimaryKey val questionId: String,
    val timesPlayed: Int,
    val timesCorrect: Int,
    val lastPlayedAt: Instant?,
)

@Entity(
    tableName = "game_sessions",
    indices = [Index("state"), Index("modeId")],
)
data class GameSessionEntity(
    @PrimaryKey val id: String,
    val modeId: String,
    val configJson: String,
    val state: String,
    val startedAt: Instant,
    val endedAt: Instant?,
)

@Entity(
    tableName = "attempts",
    foreignKeys = [
        ForeignKey(
            entity = GameSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("questionId")],
)
data class AttemptEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val questionId: String,
    val position: Int,
    val verdict: String,
    val answeredAt: Instant,
    val durationMs: Long,
)
