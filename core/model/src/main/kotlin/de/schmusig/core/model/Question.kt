package de.schmusig.core.model

import java.time.Instant

/**
 * Eine Frage im Katalog. Siehe Abschnitt 5.1 des Auftrags.
 *
 * Die Klasse ist bewusst frei von Android- und Datenbank-Bezug: sie wandert unveraendert
 * durch Datenbank, Merge-Engine, Katalog und Spielmodus.
 *
 * [contentHash] wird nie von Hand gesetzt, sondern immer ueber die Hash-Funktion in
 * `:core:sync` berechnet. [distractors] enthaelt ausschliesslich Falschantworten; die
 * richtige Antwort steht nur in [answer] (Abschnitt 5.4).
 */
data class Question(
    val id: String,
    val contentHash: String,
    val question: String,
    val answer: String,
    val category: String,
    val difficulty: Int,
    val tags: List<String> = emptyList(),
    val distractors: List<String> = emptyList(),
    val flagged: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
    val revision: Int = 1,
    val deletedAt: Instant? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null

    /** Wie viele Auswahlmoeglichkeiten ein Multiple-Choice-Modus hieraus bauen koennte. */
    val choiceCount: Int get() = distractors.size + 1

    companion object {
        const val MIN_DIFFICULTY = 1
        const val MAX_DIFFICULTY = 5
        const val MAX_DISTRACTORS = 5

        val DIFFICULTIES: List<Int> = (MIN_DIFFICULTY..MAX_DIFFICULTY).toList()
    }
}
