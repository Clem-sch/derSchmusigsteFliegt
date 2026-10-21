package de.schmusig.core.sync

import de.schmusig.core.model.Question

/** Was beim Bereinigen einer Falschantworten-Liste entfernt oder gekappt wurde. */
data class DistractorSanitization(
    val values: List<String>,
    val removedEmpty: Int = 0,
    val removedDuplicates: Int = 0,
    val removedEqualToAnswer: Int = 0,
    val truncated: Int = 0,
) {
    val changedAnything: Boolean
        get() = removedEmpty > 0 || removedDuplicates > 0 ||
            removedEqualToAnswer > 0 || truncated > 0
}

/**
 * Die Regeln aus Abschnitt 5.4. Sie gelten an genau zwei Stellen - beim Speichern im
 * Editor und beim Import - und sind darum hier einmal implementiert.
 *
 * Reihenfolge ist bedeutsam: erst leere Eintraege weg, dann Treffer auf die richtige
 * Antwort, dann Dubletten, dann kappen. Die verbleibende Reihenfolge der Eintraege bleibt
 * erhalten (sie hat keine Bedeutung, uebersteht aber den Round-Trip unveraendert).
 */
object DistractorRules {

    fun sanitize(raw: List<String>, answer: String): DistractorSanitization {
        val trimmed = raw.map { it.trim() }
        val nonEmpty = trimmed.filter { it.isNotEmpty() }
        val removedEmpty = trimmed.size - nonEmpty.size

        val normalizedAnswer = ContentHash.normalize(answer)
        val notAnswer = nonEmpty.filter { ContentHash.normalize(it) != normalizedAnswer }
        val removedEqualToAnswer = nonEmpty.size - notAnswer.size

        val seen = LinkedHashMap<String, String>(notAnswer.size)
        notAnswer.forEach { candidate ->
            seen.putIfAbsent(ContentHash.normalize(candidate), candidate)
        }
        val deduplicated = seen.values.toList()
        val removedDuplicates = notAnswer.size - deduplicated.size

        val capped = deduplicated.take(Question.MAX_DISTRACTORS)
        val truncated = deduplicated.size - capped.size

        return DistractorSanitization(
            values = capped,
            removedEmpty = removedEmpty,
            removedDuplicates = removedDuplicates,
            removedEqualToAnswer = removedEqualToAnswer,
            truncated = truncated,
        )
    }

    /**
     * Live-Validierung je Zeile im Editor (7.3). Gibt fuer jeden Index zurueck, ob und
     * warum der Eintrag unbrauchbar ist; leere Zeilen sind in Ordnung, sie werden beim
     * Speichern stillschweigend entfernt.
     */
    fun validateRows(rows: List<String>, answer: String): List<DistractorRowError?> {
        val normalizedAnswer = ContentHash.normalize(answer)
        val seenBefore = mutableSetOf<String>()
        return rows.map { row ->
            val value = row.trim()
            val normalized = ContentHash.normalize(value)
            when {
                value.isEmpty() -> null
                normalizedAnswer.isNotEmpty() && normalized == normalizedAnswer ->
                    DistractorRowError.EQUALS_ANSWER

                !seenBefore.add(normalized) -> DistractorRowError.DUPLICATE
                else -> null
            }
        }
    }
}

enum class DistractorRowError { EQUALS_ANSWER, DUPLICATE }
