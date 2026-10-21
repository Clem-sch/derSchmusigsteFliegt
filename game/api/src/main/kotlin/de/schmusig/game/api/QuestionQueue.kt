package de.schmusig.game.api

import de.schmusig.core.model.Question
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Eine vorbereitete Runde: die gemischte Reihenfolge und die ersten vollstaendig
 * geladenen Fragen (8.1).
 */
data class PreparedRound(
    val orderedIds: List<String>,
    val preloaded: List<Question>,
)

/**
 * Uebergibt die im Setup vorbereitete Runde an die Kartenphase.
 *
 * Bewusst nur im Speicher und nur fuer genau eine Session: der Sinn des Vorladens ist,
 * dass waehrend der Swipe-Animation garantiert kein Datenbankzugriff stattfindet. Nach
 * einem Prozess-Neustart ist der Inhalt weg - dann baut die Kartenphase die Warteschlange
 * aus dem Seed in der Session-Konfiguration neu auf.
 */
@Singleton
class PreparedRoundHolder @Inject constructor() {

    private var entry: Pair<String, PreparedRound>? = null

    fun put(sessionId: String, round: PreparedRound) {
        entry = sessionId to round
    }

    fun take(sessionId: String): PreparedRound? {
        val current = entry ?: return null
        if (current.first != sessionId) return null
        entry = null
        return current.second
    }

    fun clear() {
        entry = null
    }
}

/**
 * Die Warteschlange der Kartenphase (8.2).
 *
 * Faul: aus der gemischten ID-Liste werden immer nur so viele Fragen vollstaendig
 * geladen, dass der Puffer nicht leerlaeuft. Nachgefuellt wird, sobald weniger als
 * [LOW_WATER_MARK] vorgeladene uebrig sind - also nie waehrend einer Animation.
 *
 * Uebersprungene Fragen wandern zurueck in den Pool und koennen spaeter wieder kommen.
 * Sie landen hinten, damit nicht dieselbe Karte sofort erneut erscheint.
 */
class QuestionQueue(
    private val orderedIds: List<String>,
    private val loadQuestions: suspend (List<String>) -> List<Question>,
    preloaded: List<Question> = emptyList(),
) {

    private val mutex = Mutex()
    private val buffer = ArrayDeque(preloaded)
    private val requeued = ArrayDeque<Question>()
    private var cursor = preloaded.size

    /** Wie viele Fragen die Runde ueberhaupt noch hergeben kann. */
    val remaining: Int
        get() = buffer.size + requeued.size + (orderedIds.size - cursor)

    suspend fun prefetch() = mutex.withLock { fill() }

    suspend fun next(): Question? = mutex.withLock {
        if (buffer.size < LOW_WATER_MARK) fill()
        buffer.removeFirstOrNull() ?: requeued.removeFirstOrNull()
    }

    suspend fun requeue(question: Question) = mutex.withLock {
        requeued.addLast(question)
    }

    private suspend fun fill() {
        while (buffer.size < PREFETCH_TARGET && cursor < orderedIds.size) {
            val end = minOf(cursor + CHUNK, orderedIds.size)
            val chunk = orderedIds.subList(cursor, end)
            cursor = end
            // Die Reihenfolge der gemischten ID-Liste muss erhalten bleiben; die
            // Datenbank liefert `IN (...)` in beliebiger Reihenfolge zurueck.
            val loaded = loadQuestions(chunk).associateBy { it.id }
            chunk.forEach { id -> loaded[id]?.let(buffer::addLast) }
        }
    }

    private companion object {
        const val PREFETCH_TARGET = 30
        const val LOW_WATER_MARK = 10
        const val CHUNK = 30
    }
}
