package de.schmusig.core.sync

import de.schmusig.core.model.AppError
import de.schmusig.core.model.Outcome
import de.schmusig.core.model.Question
import de.schmusig.core.model.QuestionStats
import java.time.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Liest und schreibt das kanonische JSON (Abschnitt 6.1).
 *
 * Beim Schreiben setzt die App alle Felder; beim Lesen werden unbekannte Felder ignoriert,
 * damit eine Datei aus einer neueren Minor-Version nicht am Parser scheitert. Eine
 * hoehere `schemaVersion` wird dagegen klar abgelehnt - dann fehlt moeglicherweise
 * Bedeutung, nicht nur ein Feld.
 */
object CatalogSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(
        questions: List<Question>,
        stats: List<QuestionStats>,
        exportedAt: Instant,
        source: String = SOURCE_EXPORT,
    ): String {
        val dto = CatalogFileDto(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            exportedAt = exportedAt.toString(),
            source = source,
            questions = questions.map { it.toDto() },
            stats = stats.map { it.toDto() },
        )
        return json.encodeToString(CatalogFileDto.serializer(), dto)
    }

    fun decode(raw: String): Outcome<CatalogFileDto> {
        val dto = try {
            json.decodeFromString(CatalogFileDto.serializer(), raw)
        } catch (error: SerializationException) {
            return Outcome.Failure(AppError.Format(error.message))
        } catch (error: IllegalArgumentException) {
            return Outcome.Failure(AppError.Format(error.message))
        }

        if (dto.schemaVersion > CURRENT_SCHEMA_VERSION) {
            return Outcome.Failure(
                AppError.UnsupportedSchema(
                    found = dto.schemaVersion,
                    supported = CURRENT_SCHEMA_VERSION,
                ),
            )
        }
        return Outcome.Success(dto)
    }
}

private fun Question.toDto() = QuestionDto(
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

private fun QuestionStats.toDto() = QuestionStatsDto(
    questionId = questionId,
    timesPlayed = timesPlayed,
    timesCorrect = timesCorrect,
    lastPlayedAt = lastPlayedAt?.toString(),
)
