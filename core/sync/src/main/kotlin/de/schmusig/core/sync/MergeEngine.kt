package de.schmusig.core.sync

import de.schmusig.core.model.Question
import de.schmusig.core.model.QuestionStats
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * Der lokale Bestand, aufbereitet fuer den Merge. Enthaelt auch Tombstones, denn eine
 * eingehende Zeile mit bekannter ID muss ihren geloeschten lokalen Zwilling finden.
 *
 * Die Hash-Suche fuer Regel 3 laesst Tombstones bewusst aus: eine lokal geloeschte Frage
 * soll man wieder anlegen koennen, ohne dass der Import sie als Dublette abweist.
 */
class LocalCatalogIndex(questions: List<Question>) {

    private val questionsById: Map<String, Question> = questions.associateBy { it.id }
    private val questionsByHash: Map<String, Question> = questions
        .filterNot { it.isDeleted }
        .associateBy { it.contentHash }

    val size: Int = questions.size

    fun byId(id: String): Question? = questionsById[id]

    fun byContentHash(hash: String): Question? = questionsByHash[hash]

    companion object {
        val EMPTY = LocalCatalogIndex(emptyList())
    }
}

/**
 * Was der Merge schreiben wuerde. Der Dry-Run gibt denselben Plan zurueck, nur dass ihn
 * niemand ausfuehrt (Abschnitt 6.6). Das Ausfuehren passiert in `:core:data` in einer
 * Transaktion - die Engine selbst kennt keine Datenbank.
 */
data class MergePlan(
    val mode: ImportMode,
    val inserts: List<Question> = emptyList(),
    val updates: List<Question> = emptyList(),
    val stats: List<QuestionStats> = emptyList(),
    val replaceEverything: Boolean = false,
    val report: ImportReport,
)

/**
 * Die Merge-Regeln aus Abschnitt 6.3. Reine Funktion ueber Datei und lokalem Bestand -
 * genau darum laesst sie sich ohne Android und ohne Datenbank vollstaendig testen.
 */
class MergeEngine(
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {

    fun plan(
        file: CatalogFileDto,
        local: LocalCatalogIndex,
        mode: ImportMode,
        now: Instant,
    ): MergePlan {
        val invalid = mutableListOf<InvalidEntry>()
        val notices = mutableListOf<DistractorNotice>()
        val parsedQuestions = mutableListOf<Question>()

        file.questions.forEachIndexed { index, dto ->
            when (val parsed = parseRow(dto, index, now)) {
                is RowResult.Invalid -> invalid += parsed.entry
                is RowResult.Valid -> {
                    parsedQuestions += parsed.question
                    notices += parsed.notices
                }
            }
        }

        return when (mode) {
            ImportMode.RESTORE -> planRestore(parsedQuestions, invalid, notices, file, local)
            ImportMode.MERGE -> planMerge(parsedQuestions, invalid, notices, local)
        }
    }

    // ----------------------------------------------------------------- RESTORE

    private fun planRestore(
        questions: List<Question>,
        invalid: List<InvalidEntry>,
        notices: List<DistractorNotice>,
        file: CatalogFileDto,
        local: LocalCatalogIndex,
    ): MergePlan {
        // Bei RESTORE ersetzt die Datei den Bestand vollstaendig, Statistik inklusive.
        val knownIds = questions.mapTo(mutableSetOf()) { it.id }
        val stats = file.stats
            .filter { it.questionId in knownIds }
            .mapNotNull { dto -> dto.toStatsOrNull() }

        return MergePlan(
            mode = ImportMode.RESTORE,
            inserts = questions,
            stats = stats,
            replaceEverything = true,
            report = ImportReport(
                mode = ImportMode.RESTORE,
                inserted = questions.map { it.toReportEntry() },
                invalid = invalid,
                distractorNotices = notices,
                statsApplied = stats.size,
                removedByRestore = local.size,
            ),
        )
    }

    // ------------------------------------------------------------------- MERGE

    private fun planMerge(
        questions: List<Question>,
        invalid: List<InvalidEntry>,
        notices: List<DistractorNotice>,
        local: LocalCatalogIndex,
    ): MergePlan {
        val inserts = mutableListOf<Question>()
        val updates = mutableListOf<Question>()
        val inserted = mutableListOf<ReportEntry>()
        val updated = mutableListOf<ReportEntry>()
        val unchanged = mutableListOf<ReportEntry>()
        val deleted = mutableListOf<ReportEntry>()
        val skippedOlder = mutableListOf<ReportEntry>()
        val duplicates = mutableListOf<DuplicateEntry>()
        val conflicts = mutableListOf<ReportEntry>()

        // Dubletten *innerhalb* einer Datei muessen ebenfalls auffallen, sonst landen zwei
        // identische Zeilen derselben Datei beide im Bestand.
        val pendingById = mutableMapOf<String, Question>()
        val pendingByHash = mutableMapOf<String, Question>()

        questions.forEach { incoming ->
            val existing = local.byId(incoming.id) ?: pendingById[incoming.id]

            if (existing == null) {
                val sameContent = local.byContentHash(incoming.contentHash)
                    ?: pendingByHash[incoming.contentHash]
                if (sameContent != null) {
                    // Regel 3: ID unbekannt, Inhalt bereits vorhanden -> nicht einfuegen.
                    duplicates += DuplicateEntry(
                        incomingQuestion = incoming.question,
                        existingId = sameContent.id,
                        existingQuestion = sameContent.question,
                    )
                    return@forEach
                }
                // Regel 2: ID unbekannt, Inhalt unbekannt -> einfuegen.
                inserts += incoming
                pendingById[incoming.id] = incoming
                pendingByHash[incoming.contentHash] = incoming
                inserted += incoming.toReportEntry()
                return@forEach
            }

            // Regel 4: ID bekannt.
            val isNewer = incoming.updatedAt.isAfter(existing.updatedAt)
            val isSameMoment = incoming.updatedAt == existing.updatedAt

            when {
                incoming.isDeleted && isNewer -> {
                    val tombstone = existing.copy(
                        deletedAt = incoming.deletedAt,
                        updatedAt = incoming.updatedAt,
                        revision = maxOf(incoming.revision, existing.revision + 1),
                    )
                    updates += tombstone
                    deleted += tombstone.toReportEntry()
                }

                // Vergleich ueber *alle* Felder, nicht ueber den contentHash: der deckt nur
                // Frage und Antwort ab und wuerde importierte Falschantworten, Tags oder
                // Schwierigkeiten stillschweigend verwerfen (Warnung in 6.3).
                incoming == existing -> unchanged += existing.toReportEntry()

                isNewer -> {
                    val merged = incoming.withEarliestCreatedAt(existing)
                    updates += merged
                    updated += merged.toReportEntry()
                }

                // Zeitstempel-Gleichstand bei unterschiedlichem Inhalt: Revision gewinnt.
                isSameMoment && incoming.revision > existing.revision -> {
                    val merged = incoming.withEarliestCreatedAt(existing)
                    updates += merged
                    updated += merged.toReportEntry()
                }

                isSameMoment -> conflicts += existing.toReportEntry()

                else -> skippedOlder += existing.toReportEntry()
            }
        }

        return MergePlan(
            mode = ImportMode.MERGE,
            inserts = inserts,
            updates = updates,
            // Statistik wird im MERGE-Modus bewusst ignoriert (Abschnitt 6.5).
            stats = emptyList(),
            replaceEverything = false,
            report = ImportReport(
                mode = ImportMode.MERGE,
                inserted = inserted,
                updated = updated,
                unchanged = unchanged,
                deleted = deleted,
                skippedOlder = skippedOlder,
                duplicates = duplicates,
                conflicts = conflicts,
                invalid = invalid,
                distractorNotices = notices,
                statsApplied = 0,
            ),
        )
    }

    // ------------------------------------------------------------------ Zeilen

    private fun parseRow(dto: QuestionDto, index: Int, now: Instant): RowResult {
        val question = dto.question?.trim().orEmpty()
        if (question.isEmpty()) {
            return RowResult.Invalid(
                InvalidEntry(index, dto.question, InvalidReason.EMPTY_QUESTION),
            )
        }
        val answer = dto.answer?.trim().orEmpty()
        if (answer.isEmpty()) {
            return RowResult.Invalid(InvalidEntry(index, question, InvalidReason.EMPTY_ANSWER))
        }

        // Fehlendes Feld -> Default; vorhandenes, aber leeres Feld -> ungueltige Zeile.
        val rawCategory = dto.category
        val category = if (rawCategory == null) {
            DEFAULT_CATEGORY
        } else {
            val trimmed = rawCategory.trim()
            if (trimmed.isEmpty()) {
                return RowResult.Invalid(
                    InvalidEntry(index, question, InvalidReason.EMPTY_CATEGORY),
                )
            }
            trimmed
        }

        val difficulty = dto.difficulty ?: DEFAULT_DIFFICULTY
        if (difficulty !in Question.MIN_DIFFICULTY..Question.MAX_DIFFICULTY) {
            return RowResult.Invalid(
                InvalidEntry(index, question, InvalidReason.DIFFICULTY_OUT_OF_RANGE),
            )
        }

        val createdAt = parseOptionalInstant(dto.createdAt)
            ?: return badTimestamp(index, question)
        val updatedAt = parseOptionalInstant(dto.updatedAt)
            ?: return badTimestamp(index, question)
        val deletedAt = parseOptionalInstant(dto.deletedAt)
            ?: return badTimestamp(index, question)

        val sanitization = DistractorRules.sanitize(dto.distractors.orEmpty(), answer)

        val parsed = Question(
            id = dto.id?.trim()?.takeIf { it.isNotEmpty() } ?: idGenerator(),
            // Der Hash wird immer neu gerechnet: ein Wert aus der Datei koennte zum Inhalt
            // nicht passen, und Regel 3 haengt an ihm.
            contentHash = ContentHash.of(question, answer),
            question = question,
            answer = answer,
            category = category,
            difficulty = difficulty,
            tags = normalizeTags(dto.tags.orEmpty()),
            distractors = sanitization.values,
            flagged = dto.flagged ?: false,
            createdAt = createdAt.value ?: now,
            updatedAt = updatedAt.value ?: now,
            revision = (dto.revision ?: 1).coerceAtLeast(1),
            deletedAt = deletedAt.value,
        )

        return RowResult.Valid(parsed, sanitization.toNotices(question))
    }

    private fun badTimestamp(index: Int, question: String): RowResult.Invalid =
        RowResult.Invalid(InvalidEntry(index, question, InvalidReason.BAD_TIMESTAMP))

    private sealed interface RowResult {
        data class Valid(val question: Question, val notices: List<DistractorNotice>) : RowResult
        data class Invalid(val entry: InvalidEntry) : RowResult
    }
}

/** Tags klein geschrieben, getrimmt, dedupliziert - Reihenfolge bleibt erhalten (5.1). */
internal fun normalizeTags(raw: List<String>): List<String> {
    val seen = LinkedHashSet<String>(raw.size)
    raw.forEach { tag ->
        val normalized = tag.trim().lowercase()
        if (normalized.isNotEmpty()) seen += normalized
    }
    return seen.toList()
}

/** Traeger fuer "geparst, Wert darf null sein" - `null` als Rueckgabe heisst "kaputt". */
private class ParsedInstant(val value: Instant?)

private fun parseOptionalInstant(raw: String?): ParsedInstant? {
    if (raw == null || raw.isBlank()) return ParsedInstant(null)
    return try {
        ParsedInstant(Instant.parse(raw.trim()))
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun QuestionStatsDto.toStatsOrNull(): QuestionStats? {
    val lastPlayed = parseOptionalInstant(lastPlayedAt) ?: return null
    return QuestionStats(
        questionId = questionId,
        timesPlayed = timesPlayed.coerceAtLeast(0),
        timesCorrect = timesCorrect.coerceAtLeast(0),
        lastPlayedAt = lastPlayed.value,
    )
}

/** Anlegedatum ist historisch: beim Aktualisieren gewinnt der fruehere Wert. */
private fun Question.withEarliestCreatedAt(other: Question): Question =
    copy(createdAt = minOf(createdAt, other.createdAt))

private fun Question.toReportEntry() = ReportEntry(
    id = id,
    question = question,
    category = category,
)

private fun DistractorSanitization.toNotices(question: String): List<DistractorNotice> =
    buildList {
        if (removedEqualToAnswer > 0) {
            add(
                DistractorNotice(
                    question,
                    DistractorNoticeKind.EQUAL_TO_ANSWER,
                    removedEqualToAnswer,
                ),
            )
        }
        if (removedDuplicates > 0) {
            add(DistractorNotice(question, DistractorNoticeKind.DUPLICATE, removedDuplicates))
        }
        if (removedEmpty > 0) {
            add(DistractorNotice(question, DistractorNoticeKind.EMPTY, removedEmpty))
        }
        if (truncated > 0) {
            add(DistractorNotice(question, DistractorNoticeKind.TRUNCATED, truncated))
        }
    }
