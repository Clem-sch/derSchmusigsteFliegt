package de.schmusig.game.api

import com.google.common.truth.Truth.assertThat
import de.schmusig.core.model.Attempt
import de.schmusig.core.model.Verdict
import java.time.Instant
import org.junit.Test

/**
 * Abschnitt 11: "ein Test der Session-Engine (Attempt-Korrektur in der Review wirkt
 * korrekt auf den Statistik-Commit)".
 */
class RoundStatsTest {

    @Test
    fun `richtige und falsche wertungen zaehlen als gespielt`() {
        val result = RoundStats.playedCorrect(
            listOf(
                attempt("a", 0, Verdict.CORRECT),
                attempt("b", 1, Verdict.WRONG),
            ),
        )

        assertThat(result).containsExactly("a", 1, "b", 0)
    }

    @Test
    fun `uebersprungene fragen zaehlen nicht als gespielt`() {
        val result = RoundStats.playedCorrect(
            listOf(
                attempt("a", 0, Verdict.CORRECT),
                attempt("b", 1, Verdict.SKIPPED),
            ),
        )

        assertThat(result.keys).containsExactly("a")
    }

    @Test
    fun `eine korrektur in der review wirkt auf den commit`() {
        val beforeCorrection = listOf(attempt("a", 0, Verdict.WRONG))
        assertThat(RoundStats.playedCorrect(beforeCorrection)).containsExactly("a", 0)

        // In der Review wurde "falsch" auf "richtig" umgeschaltet.
        val afterCorrection = listOf(attempt("a", 0, Verdict.CORRECT))
        assertThat(RoundStats.playedCorrect(afterCorrection)).containsExactly("a", 1)
    }

    @Test
    fun `eine korrektur auf uebersprungen nimmt die frage aus dem commit`() {
        val afterCorrection = listOf(attempt("a", 0, Verdict.SKIPPED))

        assertThat(RoundStats.playedCorrect(afterCorrection)).isEmpty()
    }

    @Test
    fun `eine frage die uebersprungen und spaeter gewertet wurde zaehlt einmal`() {
        val result = RoundStats.playedCorrect(
            listOf(
                attempt("a", 0, Verdict.SKIPPED),
                attempt("a", 1, Verdict.CORRECT),
            ),
        )

        assertThat(result).containsExactly("a", 1)
    }

    @Test
    fun `bei mehreren wertungen derselben frage gilt die letzte`() {
        val result = RoundStats.playedCorrect(
            listOf(
                attempt("a", 1, Verdict.CORRECT),
                attempt("a", 0, Verdict.WRONG),
            ),
        )

        // Trotz der Eingabereihenfolge entscheidet die Position in der Runde.
        assertThat(result).containsExactly("a", 1)
    }

    @Test
    fun `eine runde ohne wertungen schreibt nichts`() {
        assertThat(RoundStats.playedCorrect(emptyList())).isEmpty()
    }

    private fun attempt(questionId: String, position: Int, verdict: Verdict) = Attempt(
        id = "attempt-$questionId-$position",
        sessionId = "session",
        questionId = questionId,
        position = position,
        verdict = verdict,
        answeredAt = Instant.parse("2026-10-16T10:00:00Z"),
        durationMs = 1_000,
    )
}
