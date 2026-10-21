package de.schmusig.core.sync

import com.google.common.truth.Truth.assertThat
import de.schmusig.core.model.AppError
import de.schmusig.core.model.Outcome
import org.junit.Test

/** Abschnitt 11, Test 9: kaputte Eingaben. */
class BrokenInputTest {

    private val engine = MergeEngine(idGenerator = { "neu" })

    @Test
    fun `ungueltiges json wird als formatfehler abgelehnt`() {
        val decoded = CatalogSerializer.decode("{ das ist kein json")

        assertThat(decoded).isInstanceOf(Outcome.Failure::class.java)
        assertThat((decoded as Outcome.Failure).error).isInstanceOf(AppError.Format::class.java)
    }

    @Test
    fun `hoehere schemaVersion wird abgelehnt und nichts geschrieben`() {
        val decoded = CatalogSerializer.decode(
            """{ "schemaVersion": 999, "questions": [] }""",
        )

        assertThat(decoded).isInstanceOf(Outcome.Failure::class.java)
        val error = (decoded as Outcome.Failure).error
        assertThat(error).isInstanceOf(AppError.UnsupportedSchema::class.java)
        assertThat((error as AppError.UnsupportedSchema).found).isEqualTo(999)
    }

    @Test
    fun `leere frage wird verworfen ohne den import abzubrechen`() {
        val raw = """
            {
              "questions": [
                { "question": "   ", "answer": "Rom" },
                { "question": "Gute Frage?", "answer": "Gute Antwort" }
              ]
            }
        """.trimIndent()
        val file = (CatalogSerializer.decode(raw) as Outcome.Success).value

        val plan = engine.plan(file, LocalCatalogIndex.EMPTY, ImportMode.MERGE, T1)

        assertThat(plan.inserts).hasSize(1)
        assertThat(plan.report.invalid).hasSize(1)
        assertThat(plan.report.invalid.single().reason).isEqualTo(InvalidReason.EMPTY_QUESTION)
        assertThat(plan.report.invalid.single().index).isEqualTo(0)
    }

    @Test
    fun `leere antwort wird verworfen`() {
        val file = CatalogFileDto(questions = listOf(QuestionDto(question = "Frage?", answer = "")))

        val plan = engine.plan(file, LocalCatalogIndex.EMPTY, ImportMode.MERGE, T1)

        assertThat(plan.inserts).isEmpty()
        assertThat(plan.report.invalid.single().reason).isEqualTo(InvalidReason.EMPTY_ANSWER)
    }

    @Test
    fun `leere kategorie wird verworfen wenn das feld vorhanden ist`() {
        val file = CatalogFileDto(
            questions = listOf(QuestionDto(question = "Frage?", answer = "Antwort", category = " ")),
        )

        val plan = engine.plan(file, LocalCatalogIndex.EMPTY, ImportMode.MERGE, T1)

        assertThat(plan.inserts).isEmpty()
        assertThat(plan.report.invalid.single().reason).isEqualTo(InvalidReason.EMPTY_CATEGORY)
    }

    @Test
    fun `schwierigkeit ausserhalb von eins bis fuenf wird verworfen`() {
        val file = CatalogFileDto(
            questions = listOf(
                QuestionDto(question = "Frage?", answer = "Antwort", difficulty = 7),
                QuestionDto(question = "Frage 2?", answer = "Antwort 2", difficulty = 0),
            ),
        )

        val plan = engine.plan(file, LocalCatalogIndex.EMPTY, ImportMode.MERGE, T1)

        assertThat(plan.inserts).isEmpty()
        assertThat(plan.report.invalid.map { it.reason })
            .containsExactly(
                InvalidReason.DIFFICULTY_OUT_OF_RANGE,
                InvalidReason.DIFFICULTY_OUT_OF_RANGE,
            )
    }

    @Test
    fun `kaputter zeitstempel wird verworfen`() {
        val file = CatalogFileDto(
            questions = listOf(
                QuestionDto(question = "Frage?", answer = "Antwort", updatedAt = "gestern"),
            ),
        )

        val plan = engine.plan(file, LocalCatalogIndex.EMPTY, ImportMode.MERGE, T1)

        assertThat(plan.inserts).isEmpty()
        assertThat(plan.report.invalid.single().reason).isEqualTo(InvalidReason.BAD_TIMESTAMP)
    }

    @Test
    fun `ungueltige zeilen fuehren zu keinem teilweise geschriebenen bestand`() {
        // Der Plan enthaelt ausschliesslich gueltige Zeilen; das Schreiben selbst laeuft
        // in einer Transaktion in :core:data.
        val file = CatalogFileDto(
            questions = listOf(
                QuestionDto(question = "Gut?", answer = "Ja"),
                QuestionDto(question = "", answer = "Nein"),
                QuestionDto(question = "Auch gut?", answer = "Ja", difficulty = 9),
            ),
        )

        val plan = engine.plan(file, LocalCatalogIndex.EMPTY, ImportMode.MERGE, T1)

        assertThat(plan.inserts).hasSize(1)
        assertThat(plan.inserts.single().question).isEqualTo("Gut?")
        assertThat(plan.report.invalid).hasSize(2)
        assertThat(plan.report.totalRows).isEqualTo(3)
    }

    @Test
    fun `gleiche schemaVersion wird akzeptiert`() {
        val decoded = CatalogSerializer.decode(
            """{ "schemaVersion": $CURRENT_SCHEMA_VERSION, "questions": [] }""",
        )

        assertThat(decoded).isInstanceOf(Outcome.Success::class.java)
    }
}
