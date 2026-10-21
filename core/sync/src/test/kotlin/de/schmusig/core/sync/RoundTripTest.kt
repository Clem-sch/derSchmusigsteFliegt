package de.schmusig.core.sync

import com.google.common.truth.Truth.assertThat
import de.schmusig.core.model.Outcome
import de.schmusig.core.model.QuestionStats
import org.junit.Test

/** Abschnitt 11, Tests 2, 3, 5 und 6. */
class RoundTripTest {

    private val engine = MergeEngine(idGenerator = { error("darf keine ID erzeugen muessen") })

    private val bestand = listOf(
        question(id = "a", difficulty = 1),
        question(
            id = "b",
            question = "Wer schrieb „Die Verwandlung“?",
            answer = "Franz Kafka",
            category = "Literatur",
            difficulty = 3,
            tags = listOf("literatur", "klassiker"),
            distractors = listOf("Thomas Mann", "Hermann Hesse", "Robert Musil"),
            flagged = true,
            createdAt = T0,
            updatedAt = T1,
            revision = 4,
        ),
        question(id = "c", question = "Geloeschte Frage?", answer = "Weg", deletedAt = T1),
    )

    @Test
    fun `round trip - export parse merge in leere datenbank ergibt identischen bestand`() {
        val raw = CatalogSerializer.encode(bestand, emptyList(), T2)

        val decoded = CatalogSerializer.decode(raw)
        assertThat(decoded).isInstanceOf(Outcome.Success::class.java)
        val file = (decoded as Outcome.Success).value

        val plan = engine.plan(file, LocalCatalogIndex.EMPTY, ImportMode.MERGE, T2)

        // Feld fuer Feld, inklusive IDs, Zeitstempel, Revisionen und Tombstone.
        assertThat(plan.inserts).containsExactlyElementsIn(bestand).inOrder()
        assertThat(plan.report.invalid).isEmpty()
        assertThat(plan.report.duplicates).isEmpty()
    }

    @Test
    fun `idempotenz - derselbe import zweimal meldet beim zweiten mal nur unveraendert`() {
        val raw = CatalogSerializer.encode(bestand, emptyList(), T2)
        val file = (CatalogSerializer.decode(raw) as Outcome.Success).value

        val first = engine.plan(file, LocalCatalogIndex.EMPTY, ImportMode.MERGE, T2)
        val afterFirst = LocalCatalogIndex(first.inserts)

        val second = engine.plan(file, afterFirst, ImportMode.MERGE, T2)

        assertThat(second.inserts).isEmpty()
        assertThat(second.updates).isEmpty()
        assertThat(second.report.unchanged).hasSize(bestand.size)
        assertThat(second.report.duplicates).isEmpty()
        assertThat(second.report.conflicts).isEmpty()
    }

    @Test
    fun `statistik wird im merge modus ignoriert`() {
        val file = fileOf(
            *bestand.toTypedArray(),
            stats = listOf(QuestionStatsDto("a", timesPlayed = 12, timesCorrect = 9)),
        )

        val plan = engine.plan(file, LocalCatalogIndex.EMPTY, ImportMode.MERGE, T2)

        assertThat(plan.stats).isEmpty()
        assertThat(plan.report.statsApplied).isEqualTo(0)
    }

    @Test
    fun `statistik wird im restore modus uebernommen`() {
        val file = fileOf(
            *bestand.toTypedArray(),
            stats = listOf(
                QuestionStatsDto(
                    questionId = "a",
                    timesPlayed = 12,
                    timesCorrect = 9,
                    lastPlayedAt = T1.toString(),
                ),
            ),
        )

        val plan = engine.plan(file, LocalCatalogIndex(bestand), ImportMode.RESTORE, T2)

        assertThat(plan.replaceEverything).isTrue()
        assertThat(plan.stats).containsExactly(
            QuestionStats(questionId = "a", timesPlayed = 12, timesCorrect = 9, lastPlayedAt = T1),
        )
        assertThat(plan.report.statsApplied).isEqualTo(1)
        assertThat(plan.report.removedByRestore).isEqualTo(bestand.size)
    }

    @Test
    fun `statistik zu unbekannten fragen wird beim restore verworfen`() {
        val file = fileOf(
            bestand.first(),
            stats = listOf(QuestionStatsDto("gibt-es-nicht", timesPlayed = 3)),
        )

        val plan = engine.plan(file, LocalCatalogIndex.EMPTY, ImportMode.RESTORE, T2)

        assertThat(plan.stats).isEmpty()
    }

    @Test
    fun `minimaldatei mit nur frage und antwort importiert mit defaults`() {
        val raw = """
            {
              "questions": [
                { "question": "Wie heißt die Hauptstadt von Italien?", "answer": "Rom" }
              ]
            }
        """.trimIndent()

        val file = (CatalogSerializer.decode(raw) as Outcome.Success).value
        val plan = MergeEngine(idGenerator = { "erzeugte-id" })
            .plan(file, LocalCatalogIndex.EMPTY, ImportMode.MERGE, T2)

        assertThat(plan.inserts).hasSize(1)
        val inserted = plan.inserts.single()
        assertThat(inserted.id).isEqualTo("erzeugte-id")
        assertThat(inserted.category).isEqualTo(DEFAULT_CATEGORY)
        assertThat(inserted.difficulty).isEqualTo(DEFAULT_DIFFICULTY)
        assertThat(inserted.tags).isEmpty()
        assertThat(inserted.distractors).isEmpty()
        assertThat(inserted.flagged).isFalse()
        assertThat(inserted.revision).isEqualTo(1)
        assertThat(inserted.deletedAt).isNull()
        assertThat(inserted.createdAt).isEqualTo(T2)
        assertThat(inserted.updatedAt).isEqualTo(T2)
        assertThat(inserted.contentHash)
            .isEqualTo(ContentHash.of("Wie heißt die Hauptstadt von Italien?", "Rom"))
    }

    @Test
    fun `unbekannte felder werden beim lesen ignoriert`() {
        val raw = """
            {
              "schemaVersion": 1,
              "zukunftsfeld": { "irgendwas": true },
              "questions": [
                {
                  "question": "Frage?",
                  "answer": "Antwort",
                  "nochEinNeuesFeld": 42
                }
              ]
            }
        """.trimIndent()

        val decoded = CatalogSerializer.decode(raw)

        assertThat(decoded).isInstanceOf(Outcome.Success::class.java)
        assertThat((decoded as Outcome.Success).value.questions).hasSize(1)
    }

    @Test
    fun `export schreibt alle felder`() {
        val raw = CatalogSerializer.encode(listOf(bestand[1]), emptyList(), T2)

        listOf(
            "\"id\"",
            "\"contentHash\"",
            "\"question\"",
            "\"answer\"",
            "\"category\"",
            "\"difficulty\"",
            "\"tags\"",
            "\"distractors\"",
            "\"flagged\"",
            "\"createdAt\"",
            "\"updatedAt\"",
            "\"revision\"",
            "\"deletedAt\"",
            "\"schemaVersion\"",
            "\"exportedAt\"",
            "\"source\"",
            "\"stats\"",
        ).forEach { field ->
            assertThat(raw).contains(field)
        }
    }

    @Test
    fun `tombstones gehen in den export ein`() {
        val raw = CatalogSerializer.encode(bestand, emptyList(), T2)
        assertThat(raw).contains("Geloeschte Frage?")
        assertThat(raw).contains("\"deletedAt\": \"$T1\"")
    }
}
