package de.schmusig.core.model

import java.time.Instant

/**
 * Gerätelokale Statistik zu einer Frage (Abschnitt 5.1 / 6.5).
 *
 * Getrennt von [Question] gehalten, weil sie beim Geraete-Abgleich **nicht** gemergt
 * werden darf: "12-mal gespielt" ist eine Eigenschaft dieses Geraets, nicht der Frage.
 */
data class QuestionStats(
    val questionId: String,
    val timesPlayed: Int = 0,
    val timesCorrect: Int = 0,
    val lastPlayedAt: Instant? = null,
)
