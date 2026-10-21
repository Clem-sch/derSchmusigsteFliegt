package de.schmusig.core.model

import java.time.Instant

enum class SessionState { RUNNING, REVIEW, FINISHED, ABANDONED }

enum class Verdict {
    CORRECT,
    WRONG,
    SKIPPED,
    ;

    /** Reihenfolge fuer das Umschalten in der Review-Liste (8.4). */
    fun next(): Verdict = when (this) {
        CORRECT -> WRONG
        WRONG -> SKIPPED
        SKIPPED -> CORRECT
    }
}

/**
 * Eine gespielte Runde. [modeId] ist der dauerhafte Identifikator des Spielmodus und wird
 * nie umbenannt (Abschnitt 8); [configJson] haelt die modus-spezifische Konfiguration,
 * damit ein Resume dieselbe Fragenreihenfolge fortsetzen kann.
 *
 * Sessions und Attempts werden nicht exportiert und nicht gemergt (Abschnitt 5.3).
 */
data class GameSession(
    val id: String,
    val modeId: String,
    val configJson: String,
    val state: SessionState,
    val startedAt: Instant,
    val endedAt: Instant? = null,
)

/** Eine Wertung innerhalb einer Runde. */
data class Attempt(
    val id: String,
    val sessionId: String,
    val questionId: String,
    val position: Int,
    val verdict: Verdict,
    val answeredAt: Instant,
    val durationMs: Long,
)
