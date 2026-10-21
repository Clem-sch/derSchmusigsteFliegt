package de.schmusig.core.sync

import com.google.common.truth.Truth.assertThat
import de.schmusig.core.model.Outcome
import de.schmusig.core.model.Question
import java.io.File
import java.time.Instant
import org.junit.Test

/**
 * Prueft den mitgelieferten Beispielkatalog (Abschnitt 9) gegen die Regeln des Formats.
 *
 * Der Test liest die Datei dort, wo sie im App-Paket liegt. Dadurch faellt ein Tippfehler
 * in den Seed-Daten hier auf und nicht erst beim ersten Start auf dem Geraet.
 */
class SeedCatalogTest {

    private val seedFile = File("../../app/src/main/assets/seed/seed-questions.json")

    private val file: CatalogFileDto
        get() {
            assertThat(seedFile.isFile).isTrue()
            val decoded = CatalogSerializer.decode(seedFile.readText())
            assertThat(decoded).isInstanceOf(Outcome.Success::class.java)
            return (decoded as Outcome.Success).value
        }

    @Test
    fun `beispielkatalog hat zehn fragen und laesst sich einlesen`() {
        val plan = MergeEngine().plan(file, LocalCatalogIndex.EMPTY, ImportMode.MERGE, NOW)

        assertThat(plan.report.invalid).isEmpty()
        assertThat(plan.inserts).hasSize(10)
        assertThat(plan.report.duplicates).isEmpty()
    }

    @Test
    fun `beispielkatalog deckt alle schwierigkeiten und mehrere kategorien ab`() {
        val inserted = inserted()

        assertThat(inserted.map { it.difficulty }.toSet())
            .containsExactlyElementsIn(Question.DIFFICULTIES)
        assertThat(inserted.map { it.category }.toSet().size).isAtLeast(5)
    }

    @Test
    fun `beispielkatalog enthaelt fragen mit und ohne falschantworten`() {
        val inserted = inserted()

        assertThat(inserted.any { it.distractors.isEmpty() }).isTrue()
        assertThat(inserted.any { it.distractors.size >= 3 }).isTrue()
    }

    @Test
    fun `beispielkatalog enthaelt umlaute und anfuehrungszeichen`() {
        val inserted = inserted()

        assertThat(inserted.any { it.question.contains("ß") || it.question.contains("ö") })
            .isTrue()
        assertThat(inserted.any { it.question.contains("„") }).isTrue()
    }

    @Test
    fun `genau ein eintrag laesst den contentHash weg - die app berechnet ihn`() {
        val withoutHash = file.questions.filter { it.contentHash == null }

        assertThat(withoutHash).hasSize(1)
    }

    @Test
    fun `die mitgelieferten contentHashes stimmen mit der berechnung ueberein`() {
        file.questions.forEach { dto ->
            val declared = dto.contentHash ?: return@forEach
            val computed = ContentHash.of(dto.question.orEmpty(), dto.answer.orEmpty())
            assertThat(declared).isEqualTo(computed)
        }
    }

    @Test
    fun `alle ids sind gesetzt - sonst legen zwei geraete den seed doppelt an`() {
        file.questions.forEach { dto ->
            assertThat(dto.id).isNotNull()
            assertThat(dto.id).isNotEmpty()
        }
        assertThat(file.questions.mapNotNull { it.id }.toSet()).hasSize(10)
    }

    @Test
    fun `der seed ist idempotent`() {
        val first = MergeEngine().plan(file, LocalCatalogIndex.EMPTY, ImportMode.MERGE, NOW)
        val second = MergeEngine().plan(
            file,
            LocalCatalogIndex(first.inserts),
            ImportMode.MERGE,
            NOW,
        )

        assertThat(second.report.unchanged).hasSize(10)
        assertThat(second.inserts).isEmpty()
        assertThat(second.updates).isEmpty()
    }

    @Test
    fun `suche nach kafka und periodensystem findet etwas`() {
        val inserted = inserted()

        assertThat(inserted.any { it.answer.contains("Kafka", ignoreCase = true) }).isTrue()
        assertThat(inserted.any { "periodensystem" in it.tags }).isTrue()
    }

    private fun inserted(): List<Question> =
        MergeEngine().plan(file, LocalCatalogIndex.EMPTY, ImportMode.MERGE, NOW).inserts

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-16T12:00:00Z")
    }
}
