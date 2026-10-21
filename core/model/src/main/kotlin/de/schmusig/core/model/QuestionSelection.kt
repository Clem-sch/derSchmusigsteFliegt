package de.schmusig.core.model

import java.time.Instant

/**
 * Welche Fragen ein Spielmodus ziehen darf (Schritt 1 des Setups, 8.1).
 *
 * Bewusst ohne Spielbezug formuliert, damit die Datenschicht die Auswahl aufloesen kann,
 * ohne einen Spielmodus zu kennen.
 *
 * Anders als bei [CatalogQuery] ist eine leere Menge hier **keine** fehlende
 * Einschraenkung, sondern "alles abgewaehlt": das Setup schickt bei "Alle" die
 * vollstaendige Menge mit. Darum kann [matchesNothing] den Fall sauber von "nichts passt
 * zu dieser Kombination" trennen, und die UI kann beide Faelle unterschiedlich benennen.
 */
data class QuestionSelection(
    val categories: Set<String>,
    val difficulties: Set<Int>,
    val excludePlayedSince: Instant? = null,
) {
    val noCategorySelected: Boolean get() = categories.isEmpty()

    val noDifficultySelected: Boolean get() = difficulties.isEmpty()

    val matchesNothing: Boolean get() = noCategorySelected || noDifficultySelected
}
