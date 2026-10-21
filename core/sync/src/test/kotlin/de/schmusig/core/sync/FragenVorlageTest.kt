package de.schmusig.core.sync

import com.google.common.truth.Truth.assertThat
import de.schmusig.core.model.Outcome
import java.io.File
import java.time.Instant
import org.junit.Test

/**
 * Prueft die Vorlage `fragen-vorlage.json` aus dem Projektwurzelverzeichnis gegen den
 * echten Parser.
 *
 * Eine Vorlage, die sich nicht importieren laesst, ist schlimmer als keine - darum faellt
 * ein Fehler darin hier auf und nicht erst, wenn jemand sie befuellt hat. Der Test
 * belegt zugleich die Aussagen in FRAGEN-FORMAT.md.
 */
class FragenVorlageTest {

    private val templateFile = File("../../fragen-vorlage.json")

    private val file: CatalogFileDto
        get() {
            assertThat(templateFile.isFile).isTrue()
            val decoded = CatalogSerializer.decode(templateFile.readText())
            assertThat(decoded).isInstanceOf(Outcome.Success::class.java)
            return (decoded as Outcome.Success).value
        }

    @Test
    fun `die vorlage laesst sich ohne ungueltige zeilen importieren`() {
        val plan = MergeEngine().plan(file, LocalCatalogIndex.EMPTY, ImportMode.MERGE, NOW)

        assertThat(plan.report.invalid).isEmpty()
        assertThat(plan.report.duplicates).isEmpty()
        assertThat(plan.inserts).hasSize(3)
    }

    @Test
    fun `der kommentar im kopf stoert den parser nicht`() {
        // FRAGEN-FORMAT.md sagt zu: unbekannte Felder werden ignoriert, man darf eigene
        // Notizen in die Datei schreiben.
        assertThat(templateFile.readText()).contains("_hinweis")
        assertThat(file.questions).hasSize(3)
    }

    @Test
    fun `die vorlage zeigt das pflichtprogramm mit den dokumentierten defaults`() {
        val minimal = engineWithFixedIds("eins", "zwei", "drei")
            .plan(file, LocalCatalogIndex.EMPTY, ImportMode.MERGE, NOW)
            .inserts
            .last()

        assertThat(minimal.category).isEqualTo(DEFAULT_CATEGORY)
        assertThat(minimal.difficulty).isEqualTo(DEFAULT_DIFFICULTY)
        assertThat(minimal.id).isEqualTo("drei")
        assertThat(minimal.revision).isEqualTo(1)
        assertThat(minimal.deletedAt).isNull()
        assertThat(minimal.flagged).isFalse()
    }

    @Test
    fun `die vorlage enthaelt ein beispiel mit drei falschantworten`() {
        val withDistractors = MergeEngine()
            .plan(file, LocalCatalogIndex.EMPTY, ImportMode.MERGE, NOW)
            .inserts
            .first { it.distractors.isNotEmpty() }

        assertThat(withDistractors.distractors).hasSize(3)
        assertThat(withDistractors.distractors).doesNotContain(withDistractors.answer)
    }

    @Test
    fun `zweimal importieren legt nichts doppelt an`() {
        // Die Zusage aus FRAGEN-FORMAT.md: eine handgeschriebene Datei ohne IDs darf man
        // gefahrlos erneut importieren - die Zeilen gelten dann als Dubletten.
        val first = MergeEngine().plan(file, LocalCatalogIndex.EMPTY, ImportMode.MERGE, NOW)
        val second = MergeEngine().plan(
            file,
            LocalCatalogIndex(first.inserts),
            ImportMode.MERGE,
            NOW,
        )

        assertThat(second.inserts).isEmpty()
        assertThat(second.updates).isEmpty()
        assertThat(second.report.duplicates).hasSize(3)
        assertThat(second.report.unchanged).isEmpty()
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-16T12:00:00Z")
    }
}
