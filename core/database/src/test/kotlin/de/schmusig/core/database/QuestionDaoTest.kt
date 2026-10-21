package de.schmusig.core.database

import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import de.schmusig.core.database.dao.QuestionDao
import de.schmusig.core.database.entity.QuestionEntity
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * DAO-Tests mit In-Memory-Room (Abschnitt 11): Suche, Filter, Tombstone-Filterung.
 *
 * Der Volltextindex wird in denselben Transaktionen wie die Fragen gepflegt - genau das
 * ist hier zu pruefen, denn ein auseinandergelaufener Index faellt in der App sonst erst
 * als "Suche findet nichts" auf.
 */
@RunWith(RobolectricTestRunner::class)
class QuestionDaoTest {

    private lateinit var database: SchmusigDatabase
    private lateinit var dao: QuestionDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SchmusigDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.questionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `volltextsuche findet ueber die frage`() = runTest {
        dao.upsert(entity(id = "a", question = "Wer schrieb die Verwandlung?", answer = "Kafka"))

        assertThat(searchIds("verwandlung")).containsExactly("a")
    }

    @Test
    fun `volltextsuche findet ueber die antwort`() = runTest {
        dao.upsert(entity(id = "a", question = "Wer schrieb die Verwandlung?", answer = "Kafka"))

        assertThat(searchIds("kafka")).containsExactly("a")
    }

    @Test
    fun `volltextsuche findet ueber einen tag`() = runTest {
        dao.upsert(
            entity(
                id = "a",
                question = "Welches Element hat das Symbol Fe?",
                answer = "Eisen",
                tags = listOf("periodensystem", "elemente"),
            ),
        )

        assertThat(searchIds("periodensystem")).containsExactly("a")
    }

    @Test
    fun `volltextsuche findet ueber die kategorie`() = runTest {
        dao.upsert(entity(id = "a", category = "Geografie"))

        assertThat(searchIds("geografie")).containsExactly("a")
    }

    @Test
    fun `praefixsuche findet beim tippen`() = runTest {
        dao.upsert(entity(id = "a", question = "Wer schrieb die Verwandlung?", answer = "Kafka"))

        assertThat(searchIds("verwand")).containsExactly("a")
    }

    @Test
    fun `geloeschte fragen erscheinen nicht in der liste`() = runTest {
        dao.upsert(entity(id = "a"))
        dao.upsert(entity(id = "b"))

        dao.upsert(entity(id = "b").copy(deletedAt = NOW))

        assertThat(liveIds()).containsExactly("a")
        // Der Datensatz bleibt als Loeschmarke in der Datenbank (6.4).
        assertThat(dao.findById("b")).isNotNull()
        assertThat(dao.countTombstones()).isEqualTo(1)
    }

    @Test
    fun `geloeschte fragen verschwinden auch aus dem volltextindex`() = runTest {
        dao.upsert(entity(id = "a", question = "Wer schrieb die Verwandlung?", answer = "Kafka"))
        assertThat(searchIds("kafka")).containsExactly("a")

        dao.upsert(
            entity(id = "a", question = "Wer schrieb die Verwandlung?", answer = "Kafka")
                .copy(deletedAt = NOW),
        )

        assertThat(searchIds("kafka")).isEmpty()
    }

    @Test
    fun `eine wiederhergestellte frage ist wieder findbar`() = runTest {
        val question = entity(id = "a", question = "Hauptstadt von Italien?", answer = "Rom")
        dao.upsert(question.copy(deletedAt = NOW))
        assertThat(searchIds("rom")).isEmpty()

        dao.upsert(question.copy(deletedAt = null))

        assertThat(searchIds("rom")).containsExactly("a")
    }

    @Test
    fun `der index folgt einer textaenderung`() = runTest {
        dao.upsert(entity(id = "a", question = "Alte Frage?", answer = "Alte Antwort"))
        assertThat(searchIds("alte")).containsExactly("a")

        dao.upsert(entity(id = "a", question = "Neue Frage?", answer = "Neue Antwort"))

        assertThat(searchIds("alte")).isEmpty()
        assertThat(searchIds("neue")).containsExactly("a")
    }

    @Test
    fun `filter auf kategorie und schwierigkeit`() = runTest {
        dao.upsert(entity(id = "a", category = "Geografie", difficulty = 1))
        dao.upsert(entity(id = "b", category = "Geografie", difficulty = 5))
        dao.upsert(entity(id = "c", category = "Literatur", difficulty = 1))

        val ids = dao.selectIds(
            SimpleSQLiteQuery(
                "SELECT id FROM questions WHERE deletedAt IS NULL " +
                    "AND category IN (?) AND difficulty IN (?)",
                arrayOf("Geografie", 1),
            ),
        )

        assertThat(ids).containsExactly("a")
    }

    @Test
    fun `nur markierte fragen`() = runTest {
        dao.upsert(entity(id = "a", flagged = true))
        dao.upsert(entity(id = "b", flagged = false))

        val ids = dao.selectIds(
            SimpleSQLiteQuery("SELECT id FROM questions WHERE deletedAt IS NULL AND flagged = 1"),
        )

        assertThat(ids).containsExactly("a")
    }

    @Test
    fun `nur fragen mit falschantworten`() = runTest {
        dao.upsert(entity(id = "a", distractors = listOf("Mailand")))
        dao.upsert(entity(id = "b", distractors = emptyList()))

        val ids = dao.selectIds(
            SimpleSQLiteQuery(
                "SELECT id FROM questions WHERE deletedAt IS NULL " +
                    "AND distractors IS NOT NULL AND distractors <> '[]' AND distractors <> ''",
            ),
        )

        assertThat(ids).containsExactly("a")
    }

    @Test
    fun `listen und zeitstempel ueberleben den weg durch die datenbank`() = runTest {
        val original = entity(
            id = "a",
            tags = listOf("eins", "zwei"),
            distractors = listOf("Mailand", "Neapel"),
        ).copy(createdAt = NOW, updatedAt = NOW, revision = 7)

        dao.upsert(original)

        assertThat(dao.findById("a")).isEqualTo(original)
    }

    @Test
    fun `kategorien werden mit anzahl gruppiert und ohne loeschmarken gezaehlt`() = runTest {
        dao.upsert(entity(id = "a", category = "Geografie"))
        dao.upsert(entity(id = "b", category = "Geografie"))
        dao.upsert(entity(id = "c", category = "Literatur"))
        dao.upsert(entity(id = "d", category = "Literatur").copy(deletedAt = NOW))

        val counts = dao.observeCategoryCounts().first()

        assertThat(counts.map { it.category to it.count })
            .containsExactly("Geografie" to 2, "Literatur" to 1)
            .inOrder()
    }

    @Test
    fun `ein merge plan schreibt alles oder nichts`() = runTest {
        dao.upsert(entity(id = "alt"))

        dao.applyPlan(
            inserts = listOf(entity(id = "neu1"), entity(id = "neu2")),
            updates = listOf(entity(id = "alt", category = "Geaendert")),
            replaceEverything = false,
        )

        assertThat(liveIds()).containsExactly("alt", "neu1", "neu2")
        assertThat(dao.findById("alt")?.category).isEqualTo("Geaendert")
    }

    @Test
    fun `restore ersetzt den gesamten bestand`() = runTest {
        dao.upsert(entity(id = "alt"))

        dao.applyPlan(
            inserts = listOf(entity(id = "neu")),
            updates = emptyList(),
            replaceEverything = true,
        )

        assertThat(liveIds()).containsExactly("neu")
        assertThat(dao.findById("alt")).isNull()
    }

    @Test
    fun `loeschmarken aufraeumen entfernt nur alte`() = runTest {
        val old = NOW.minusSeconds(60L * 60 * 24 * 40)
        dao.upsert(entity(id = "alt").copy(deletedAt = old))
        dao.upsert(entity(id = "neu").copy(deletedAt = NOW))

        val removed = dao.purgeTombstones(NOW.minusSeconds(60L * 60 * 24 * 30))

        assertThat(removed).isEqualTo(1)
        assertThat(dao.findById("alt")).isNull()
        assertThat(dao.findById("neu")).isNotNull()
    }

    private suspend fun searchIds(query: String): List<String> = dao.selectIds(
        SimpleSQLiteQuery(
            "SELECT id FROM questions WHERE deletedAt IS NULL " +
                "AND id IN (SELECT questionId FROM questions_fts WHERE questions_fts MATCH ?)",
            arrayOf("$query*"),
        ),
    )

    private suspend fun liveIds(): List<String> = dao.selectIds(
        SimpleSQLiteQuery("SELECT id FROM questions WHERE deletedAt IS NULL ORDER BY id"),
    )

    private fun entity(
        id: String,
        question: String = "Frage $id?",
        answer: String = "Antwort $id",
        category: String = "Allgemein",
        difficulty: Int = 2,
        tags: List<String> = emptyList(),
        distractors: List<String> = emptyList(),
        flagged: Boolean = false,
    ) = QuestionEntity(
        id = id,
        contentHash = "hash-$id",
        question = question,
        answer = answer,
        category = category,
        difficulty = difficulty,
        tags = tags,
        distractors = distractors,
        flagged = flagged,
        createdAt = NOW,
        updatedAt = NOW,
        revision = 1,
        deletedAt = null,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-16T10:00:00Z")
    }
}
