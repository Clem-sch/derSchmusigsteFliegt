package de.schmusig.core.model

/** Sortierung der Katalogliste (7.2). */
enum class CatalogSort { RECENTLY_UPDATED, CATEGORY, DIFFICULTY }

/**
 * Filter- und Sortierzustand des Katalogs. [categories] leer bedeutet "keine
 * Kategorie-Einschraenkung"; dasselbe gilt fuer [difficulties].
 */
data class CatalogQuery(
    val text: String = "",
    val categories: Set<String> = emptySet(),
    val difficulties: Set<Int> = emptySet(),
    val onlyFlagged: Boolean = false,
    val onlyWithDistractors: Boolean = false,
    val sort: CatalogSort = CatalogSort.RECENTLY_UPDATED,
) {
    val hasActiveFilter: Boolean
        get() = categories.isNotEmpty() ||
            difficulties.isNotEmpty() ||
            onlyFlagged ||
            onlyWithDistractors
}

/** Kategorie mit Bestandszahl, fuer Filter-Chips und das Spiel-Setup. */
data class CategoryCount(val category: String, val count: Int)

/** Bestandsanzeige im Hauptmenue (7.1). */
data class CatalogInventory(val questionCount: Int, val categoryCount: Int)
