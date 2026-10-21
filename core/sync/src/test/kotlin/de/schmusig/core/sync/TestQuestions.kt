package de.schmusig.core.sync

import de.schmusig.core.model.Question
import java.time.Instant

internal val T0: Instant = Instant.parse("2026-10-16T10:00:00Z")
internal val T1: Instant = Instant.parse("2026-10-17T10:00:00Z")
internal val T2: Instant = Instant.parse("2026-10-18T10:00:00Z")

/** Baut eine vollstaendige Frage mit korrekt gerechnetem Hash. */
internal fun question(
    id: String,
    question: String = "Wie heißt die Hauptstadt von Italien?",
    answer: String = "Rom",
    category: String = "Geografie",
    difficulty: Int = 1,
    tags: List<String> = listOf("hauptstädte", "europa"),
    distractors: List<String> = emptyList(),
    flagged: Boolean = false,
    createdAt: Instant = T0,
    updatedAt: Instant = T0,
    revision: Int = 1,
    deletedAt: Instant? = null,
) = Question(
    id = id,
    contentHash = ContentHash.of(question, answer),
    question = question,
    answer = answer,
    category = category,
    difficulty = difficulty,
    tags = tags,
    distractors = distractors,
    flagged = flagged,
    createdAt = createdAt,
    updatedAt = updatedAt,
    revision = revision,
    deletedAt = deletedAt,
)

internal fun Question.toDto() = QuestionDto(
    id = id,
    contentHash = contentHash,
    question = question,
    answer = answer,
    category = category,
    difficulty = difficulty,
    tags = tags,
    distractors = distractors,
    flagged = flagged,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    revision = revision,
    deletedAt = deletedAt?.toString(),
)

internal fun fileOf(vararg questions: Question, stats: List<QuestionStatsDto> = emptyList()) =
    CatalogFileDto(
        schemaVersion = CURRENT_SCHEMA_VERSION,
        exportedAt = T0.toString(),
        source = SOURCE_EXPORT,
        questions = questions.map { it.toDto() },
        stats = stats,
    )

/** Fester ID-Generator, damit erwartete Werte in Tests nachvollziehbar bleiben. */
internal fun engineWithFixedIds(vararg ids: String): MergeEngine {
    val iterator = ids.iterator()
    return MergeEngine(idGenerator = { if (iterator.hasNext()) iterator.next() else "generated" })
}
