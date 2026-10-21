package de.schmusig.core.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import de.schmusig.core.database.dao.QuestionDao
import de.schmusig.core.database.dao.StatsDao
import de.schmusig.core.model.AppError
import de.schmusig.core.model.CatalogInventory
import de.schmusig.core.model.CatalogQuery
import de.schmusig.core.model.CategoryCount
import de.schmusig.core.model.Outcome
import de.schmusig.core.model.Question
import de.schmusig.core.model.QuestionDraft
import de.schmusig.core.model.QuestionSelection
import de.schmusig.core.model.QuestionStats
import de.schmusig.core.sync.ContentHash
import de.schmusig.core.sync.DistractorRules
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class OfflineQuestionRepository @Inject constructor(
    private val questionDao: QuestionDao,
    private val statsDao: StatsDao,
    private val clock: AppClock,
) : QuestionRepository {

    override fun pagedQuestions(query: CatalogQuery): Flow<PagingData<Question>> =
        Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = PAGE_SIZE * 2,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = { questionDao.pagingSource(CatalogSql.pagingQuery(query)) },
        ).flow.map { pagingData -> pagingData.map { it.toDomain() } }

    override fun observeMatchCount(query: CatalogQuery): Flow<Int> =
        questionDao.observeMatchCount(CatalogSql.countQuery(query))

    override fun observeInventory(): Flow<CatalogInventory> =
        combine(
            questionDao.observeLiveCount(),
            questionDao.observeCategoryCount(),
        ) { questions, categories ->
            CatalogInventory(questionCount = questions, categoryCount = categories)
        }

    override fun observeCategories(): Flow<List<String>> = questionDao.observeCategories()

    override fun observeCategoryCounts(): Flow<List<CategoryCount>> =
        questionDao.observeCategoryCounts().map { rows -> rows.map { it.toDomain() } }

    override fun observeQuestion(id: String): Flow<Question?> =
        questionDao.observeById(id).map { it?.toDomain() }

    override suspend fun findById(id: String): Question? =
        questionDao.findById(id)?.toDomain()

    override suspend fun findDuplicate(question: String, answer: String): Question? =
        questionDao.findLiveByContentHash(ContentHash.of(question, answer))?.toDomain()

    override suspend fun save(draft: QuestionDraft): Outcome<Question> {
        val question = draft.question.trim()
        if (question.isEmpty()) {
            return invalid(AppError.Validation.Field.QUESTION)
        }
        val answer = draft.answer.trim()
        if (answer.isEmpty()) {
            return invalid(AppError.Validation.Field.ANSWER)
        }
        val category = draft.category.trim()
        if (category.isEmpty()) {
            return invalid(AppError.Validation.Field.CATEGORY)
        }
        if (draft.difficulty !in Question.MIN_DIFFICULTY..Question.MAX_DIFFICULTY) {
            return invalid(AppError.Validation.Field.DIFFICULTY)
        }

        val distractors = DistractorRules.sanitize(draft.distractors, answer).values
        val tags = normalizeTags(draft.tags)
        val now = clock.now()

        val existing = draft.id?.let { questionDao.findById(it) }
        val candidate = Question(
            id = existing?.id ?: draft.id ?: UUID.randomUUID().toString(),
            contentHash = ContentHash.of(question, answer),
            question = question,
            answer = answer,
            category = category,
            difficulty = draft.difficulty,
            tags = tags,
            distractors = distractors,
            flagged = draft.flagged,
            createdAt = existing?.createdAt ?: now,
            updatedAt = existing?.updatedAt ?: now,
            revision = existing?.revision ?: 1,
            // Speichern hebt einen Tombstone auf: wer eine geloeschte Frage bearbeitet,
            // will sie zurueck.
            deletedAt = null,
        )

        val previous = existing?.toDomain()
        val contentChanged = previous == null || previous.differsInContentFrom(candidate)
        val toWrite = if (contentChanged) {
            // Jede inhaltliche Aenderung setzt updatedAt und erhoeht revision - sonst
            // propagiert sie beim Geraete-Abgleich nicht (7.3).
            candidate.copy(
                updatedAt = now,
                revision = (previous?.revision ?: 0) + 1,
            )
        } else {
            candidate
        }

        return runCatchingStorage {
            questionDao.upsert(toWrite.toEntity())
            toWrite
        }
    }

    override suspend fun softDelete(id: String): Outcome<Question> {
        val existing = questionDao.findById(id)?.toDomain()
            ?: return Outcome.Failure(AppError.NotFound(id))
        val now = clock.now()
        return runCatchingStorage {
            questionDao.upsert(
                existing.copy(
                    deletedAt = now,
                    updatedAt = now,
                    revision = existing.revision + 1,
                ).toEntity(),
            )
            existing
        }
    }

    override suspend fun undoSoftDelete(previous: Question): Outcome<Unit> {
        val current = questionDao.findById(previous.id)
        val now = clock.now()
        // Die Revision zaehlt vom aktuellen Stand weiter, damit die Wiederherstellung
        // ihren eigenen Tombstone ueberholt und beim Abgleich gewinnt.
        val revision = (current?.revision ?: previous.revision) + 1
        return runCatchingStorage {
            questionDao.upsert(
                previous.copy(
                    deletedAt = null,
                    updatedAt = now,
                    revision = revision,
                ).toEntity(),
            )
        }
    }

    override suspend fun countSelection(selection: QuestionSelection): Int {
        if (selection.matchesNothing) return 0
        return questionDao.countMatches(CatalogSql.selectionCountQuery(selection))
    }

    override suspend fun selectionIds(selection: QuestionSelection): List<String> {
        if (selection.matchesNothing) return emptyList()
        return questionDao.selectIds(CatalogSql.selectionIdsQuery(selection))
    }

    override suspend fun loadByIds(ids: List<String>): List<Question> {
        if (ids.isEmpty()) return emptyList()
        return ids.chunked(SQLITE_ARG_LIMIT)
            .flatMap { chunk -> questionDao.loadByIds(chunk) }
            .map { it.toDomain() }
    }

    override suspend fun statsFor(ids: List<String>): List<QuestionStats> {
        if (ids.isEmpty()) return emptyList()
        return ids.chunked(SQLITE_ARG_LIMIT)
            .flatMap { chunk -> statsDao.loadByIds(chunk) }
            .map { it.toDomain() }
    }

    override suspend fun countTombstones(): Int = questionDao.countTombstones()

    override suspend fun purgeTombstones(before: Instant): Outcome<Int> =
        runCatchingStorage { questionDao.purgeTombstones(before) }

    private fun invalid(field: AppError.Validation.Field): Outcome<Question> =
        Outcome.Failure(AppError.Validation(field))

    private companion object {
        const val PAGE_SIZE = 40

        /** SQLite erlaubt pro Statement nur eine begrenzte Zahl gebundener Argumente. */
        const val SQLITE_ARG_LIMIT = 900
    }
}

/**
 * Inhaltlicher Vergleich ohne die Felder, die die App selbst verwaltet. Nur wenn hier
 * etwas abweicht, zaehlt das Speichern als Aenderung.
 */
internal fun Question.differsInContentFrom(other: Question): Boolean =
    question != other.question ||
        answer != other.answer ||
        category != other.category ||
        difficulty != other.difficulty ||
        tags != other.tags ||
        distractors != other.distractors ||
        flagged != other.flagged ||
        deletedAt != other.deletedAt

internal fun normalizeTags(raw: List<String>): List<String> {
    val seen = LinkedHashSet<String>(raw.size)
    raw.forEach { tag ->
        val normalized = tag.trim().lowercase()
        if (normalized.isNotEmpty()) seen += normalized
    }
    return seen.toList()
}

internal inline fun <T> runCatchingStorage(block: () -> T): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (error: RuntimeException) {
        Outcome.Failure(AppError.Storage(error.message))
    }
