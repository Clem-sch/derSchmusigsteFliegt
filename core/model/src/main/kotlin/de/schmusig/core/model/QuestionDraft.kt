package de.schmusig.core.model

/**
 * Die bearbeitbaren Felder einer Frage, wie der Editor sie liefert (7.3).
 *
 * Alles, was die App selbst setzt - `contentHash`, `updatedAt`, `revision`, Zeitstempel -
 * steht hier bewusst nicht drin: das macht das Repository, damit es nicht an zwei Stellen
 * passieren kann.
 */
data class QuestionDraft(
    val id: String? = null,
    val question: String = "",
    val answer: String = "",
    val category: String = "",
    val difficulty: Int = 2,
    val tags: List<String> = emptyList(),
    val distractors: List<String> = emptyList(),
    val flagged: Boolean = false,
) {
    val isNew: Boolean get() = id == null
}
