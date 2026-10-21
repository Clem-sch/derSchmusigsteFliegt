package de.schmusig.game.api

import com.google.common.truth.Truth.assertThat
import de.schmusig.core.model.Question
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QuestionQueueTest {

    private val allQuestions = (1..100).map { index -> question("q$index") }
    private val byId = allQuestions.associateBy { it.id }
    private var loadCalls = 0

    private val loader: suspend (List<String>) -> List<Question> = { ids ->
        loadCalls++
        // Die Datenbank liefert `IN (...)` in beliebiger Reihenfolge - hier absichtlich
        // umgedreht, damit der Test die Sortierung der Warteschlange prueft.
        ids.mapNotNull { byId[it] }.reversed()
    }

    @Test
    fun `die warteschlange haelt die gemischte reihenfolge der id liste`() = runTest {
        val ids = allQuestions.take(5).map { it.id }
        val queue = QuestionQueue(ids, loader)

        val drawn = (1..5).map { queue.next()?.id }

        assertThat(drawn).isEqualTo(ids)
    }

    @Test
    fun `die warteschlange laeuft leer und gibt dann null`() = runTest {
        val queue = QuestionQueue(listOf("q1", "q2"), loader)

        queue.next()
        queue.next()

        assertThat(queue.next()).isNull()
        assertThat(queue.remaining).isEqualTo(0)
    }

    @Test
    fun `uebersprungene fragen kommen spaeter wieder`() = runTest {
        val queue = QuestionQueue(listOf("q1", "q2"), loader)

        val first = queue.next()
        requireNotNull(first)
        queue.requeue(first)
        queue.next()

        assertThat(queue.next()?.id).isEqualTo(first.id)
    }

    @Test
    fun `eine uebersprungene frage kommt nicht sofort noch einmal`() = runTest {
        val queue = QuestionQueue(listOf("q1", "q2", "q3"), loader)

        val first = requireNotNull(queue.next())
        queue.requeue(first)

        assertThat(queue.next()?.id).isNotEqualTo(first.id)
    }

    @Test
    fun `vorgeladene fragen werden ohne datenbankzugriff ausgegeben`() = runTest {
        val ids = allQuestions.take(30).map { it.id }
        val preloaded = allQuestions.take(30)
        val queue = QuestionQueue(ids, loader, preloaded = preloaded)
        loadCalls = 0

        // Die ersten 20 Karten duerfen den Loader nicht anfassen: ueber der
        // Nachfuellgrenze von 10 wird nichts nachgeladen, und danach ist die ID-Liste
        // ohnehin erschoepft.
        repeat(20) { queue.next() }

        assertThat(loadCalls).isEqualTo(0)
    }

    @Test
    fun `nachgefuellt wird erst wenn der puffer unter die grenze faellt`() = runTest {
        val ids = allQuestions.map { it.id }
        val queue = QuestionQueue(ids, loader)

        queue.prefetch()
        val callsAfterPrefetch = loadCalls

        // 30 sind vorgeladen. Nach 21 Karten liegen noch 9 im Puffer - bis dahin wurde
        // nicht nachgeladen.
        repeat(21) { queue.next() }

        assertThat(loadCalls).isEqualTo(callsAfterPrefetch)

        // Der naechste Zug sieht weniger als 10 vorgeladene und fuellt nach.
        queue.next()
        assertThat(loadCalls).isGreaterThan(callsAfterPrefetch)
    }

    @Test
    fun `fehlende fragen blockieren die warteschlange nicht`() = runTest {
        // "weg" existiert nicht mehr - etwa weil sie zwischen Setup und Start geloescht
        // wurde.
        val queue = QuestionQueue(listOf("weg", "q1"), loader)

        assertThat(queue.next()?.id).isEqualTo("q1")
        assertThat(queue.next()).isNull()
    }

    private fun question(id: String) = Question(
        id = id,
        contentHash = "hash-$id",
        question = "Frage $id",
        answer = "Antwort $id",
        category = "Test",
        difficulty = 1,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
