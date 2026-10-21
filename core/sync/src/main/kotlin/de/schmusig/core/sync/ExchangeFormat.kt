package de.schmusig.core.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Das kanonische Austauschformat (Abschnitt 6.1). Import-Format = Export-Format: jeder
 * Export ist damit ein pruefbares Backup und zugleich ein Abgleichspaket fuer ein
 * zweites Geraet.
 *
 * Beim Lesen sind fast alle Felder optional, damit man eine Importdatei von Hand
 * schreiben kann. `null` statt eines Default-Werts ist hier Absicht: so laesst sich
 * "Feld fehlt" (Default einsetzen) von "Feld ist leer" (ungueltige Zeile) unterscheiden.
 */
@Serializable
data class CatalogFileDto(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAt: String? = null,
    val source: String? = null,
    val questions: List<QuestionDto> = emptyList(),
    val stats: List<QuestionStatsDto> = emptyList(),
)

@Serializable
data class QuestionDto(
    val id: String? = null,
    val contentHash: String? = null,
    val question: String? = null,
    val answer: String? = null,
    val category: String? = null,
    val difficulty: Int? = null,
    val tags: List<String>? = null,
    val distractors: List<String>? = null,
    val flagged: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val revision: Int? = null,
    val deletedAt: String? = null,
)

@Serializable
data class QuestionStatsDto(
    @SerialName("questionId") val questionId: String,
    val timesPlayed: Int = 0,
    val timesCorrect: Int = 0,
    val lastPlayedAt: String? = null,
)

/** Hoechste Formatversion, die diese App lesen kann. */
const val CURRENT_SCHEMA_VERSION: Int = 1

/** Default-Kategorie fuer handgeschriebene Dateien, die das Feld weglassen. */
const val DEFAULT_CATEGORY: String = "Allgemein"

/** Default-Schwierigkeit fuer handgeschriebene Dateien, die das Feld weglassen. */
const val DEFAULT_DIFFICULTY: Int = 2

/** Wert fuer `source` bei einem App-Export. */
const val SOURCE_EXPORT: String = "export"

/** Wert fuer `source` beim mitgelieferten Beispielkatalog. */
const val SOURCE_SEED: String = "seed"
