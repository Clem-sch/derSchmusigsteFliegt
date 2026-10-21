package de.schmusig.core.sync

/** Modus eines Imports (Abschnitt 6.2). */
enum class ImportMode { MERGE, RESTORE }

/** Ein Datensatz, wie er im Report angezeigt wird. */
data class ReportEntry(
    val id: String,
    val question: String,
    val category: String,
)

/** Eine eingehende Zeile, die inhaltlich schon im Bestand ist (Regel 3). */
data class DuplicateEntry(
    val incomingQuestion: String,
    val existingId: String,
    val existingQuestion: String,
)

enum class InvalidReason {
    EMPTY_QUESTION,
    EMPTY_ANSWER,
    EMPTY_CATEGORY,
    DIFFICULTY_OUT_OF_RANGE,
    BAD_TIMESTAMP,
}

/** Eine verworfene Zeile mit Begruendung; bricht den Import nicht ab (Regel 1). */
data class InvalidEntry(
    val index: Int,
    val question: String?,
    val reason: InvalidReason,
)

enum class DistractorNoticeKind { EQUAL_TO_ANSWER, DUPLICATE, EMPTY, TRUNCATED }

/** Hinweis, dass Falschantworten einer Zeile bereinigt werden mussten (5.4). */
data class DistractorNotice(
    val question: String,
    val kind: DistractorNoticeKind,
    val count: Int,
)

/**
 * Ergebnis eines Dry-Runs beziehungsweise eines ausgefuehrten Imports (Abschnitt 6.6).
 * Dieselbe Struktur beschreibt beides - der Dry-Run rechnet nur, ohne zu schreiben.
 */
data class ImportReport(
    val mode: ImportMode,
    val inserted: List<ReportEntry> = emptyList(),
    val updated: List<ReportEntry> = emptyList(),
    val unchanged: List<ReportEntry> = emptyList(),
    val deleted: List<ReportEntry> = emptyList(),
    val skippedOlder: List<ReportEntry> = emptyList(),
    val duplicates: List<DuplicateEntry> = emptyList(),
    val conflicts: List<ReportEntry> = emptyList(),
    val invalid: List<InvalidEntry> = emptyList(),
    val distractorNotices: List<DistractorNotice> = emptyList(),
    val statsApplied: Int = 0,
    val removedByRestore: Int = 0,
) {
    val totalRows: Int
        get() = inserted.size + updated.size + unchanged.size + deleted.size +
            skippedOlder.size + duplicates.size + conflicts.size + invalid.size

    val writesSomething: Boolean
        get() = inserted.isNotEmpty() || updated.isNotEmpty() || deleted.isNotEmpty() ||
            statsApplied > 0 || removedByRestore > 0
}
