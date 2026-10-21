package de.schmusig.core.data

import androidx.paging.PagingData
import de.schmusig.core.model.CatalogInventory
import de.schmusig.core.model.CatalogQuery
import de.schmusig.core.model.CategoryCount
import de.schmusig.core.model.Outcome
import de.schmusig.core.model.Question
import de.schmusig.core.model.QuestionDraft
import de.schmusig.core.model.QuestionSelection
import de.schmusig.core.model.QuestionStats
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Die Schnittstelle, ueber die Katalog, Editor und Spielmodi an Fragen kommen.
 *
 * Bewusst ohne Room-, Datei- oder Importbegriffe: die Spielmodule sollen nicht wissen,
 * woher die Fragen kommen (Abschnitt 1).
 */
interface QuestionRepository {

    fun pagedQuestions(query: CatalogQuery): Flow<PagingData<Question>>

    fun observeMatchCount(query: CatalogQuery): Flow<Int>

    fun observeInventory(): Flow<CatalogInventory>

    fun observeCategories(): Flow<List<String>>

    fun observeCategoryCounts(): Flow<List<CategoryCount>>

    fun observeQuestion(id: String): Flow<Question?>

    suspend fun findById(id: String): Question?

    /** Dubletten-Warnung im Editor (7.3): gleiche Frage, gleiche Antwort. */
    suspend fun findDuplicate(question: String, answer: String): Question?

    suspend fun save(draft: QuestionDraft): Outcome<Question>

    /** Setzt den Tombstone und gibt den Stand vor dem Loeschen fuer Undo zurueck (6.4). */
    suspend fun softDelete(id: String): Outcome<Question>

    suspend fun undoSoftDelete(previous: Question): Outcome<Unit>

    suspend fun countSelection(selection: QuestionSelection): Int

    suspend fun selectionIds(selection: QuestionSelection): List<String>

    suspend fun loadByIds(ids: List<String>): List<Question>

    suspend fun statsFor(ids: List<String>): List<QuestionStats>

    suspend fun countTombstones(): Int

    suspend fun purgeTombstones(before: Instant): Outcome<Int>
}
