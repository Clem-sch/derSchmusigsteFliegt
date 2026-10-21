package de.schmusig.core.sync

import com.google.common.truth.Truth.assertThat
import de.schmusig.core.model.Outcome
import org.junit.Test

/** Abschnitt 11, Test 7. */
class DistractorRulesTest {

    @Test
    fun `eintrag gleich der richtigen antwort wird verworfen`() {
        val result = DistractorRules.sanitize(listOf("Rom", "Mailand"), answer = "Rom")

        assertThat(result.values).containsExactly("Mailand")
        assertThat(result.removedEqualToAnswer).isEqualTo(1)
    }

    @Test
    fun `der vergleich mit der antwort laeuft ueber die normalisierte form`() {
        val result = DistractorRules.sanitize(listOf("  ROM  ", "Mailand"), answer = "Rom")

        assertThat(result.values).containsExactly("Mailand")
        assertThat(result.removedEqualToAnswer).isEqualTo(1)
    }

    @Test
    fun `dubletten werden zusammengefasst und leere entfernt`() {
        val result = DistractorRules.sanitize(
            listOf("Mailand", "", "  ", "mailand", "Neapel"),
            answer = "Rom",
        )

        assertThat(result.values).containsExactly("Mailand", "Neapel").inOrder()
        assertThat(result.removedEmpty).isEqualTo(2)
        assertThat(result.removedDuplicates).isEqualTo(1)
    }

    @Test
    fun `mehr als fuenf eintraege werden abgeschnitten`() {
        val result = DistractorRules.sanitize(
            listOf("a", "b", "c", "d", "e", "f", "g"),
            answer = "z",
        )

        assertThat(result.values).hasSize(5)
        assertThat(result.truncated).isEqualTo(2)
    }

    @Test
    fun `abschneiden wird im report gemeldet`() {
        val incoming = question(
            id = "a",
            distractors = listOf("a", "b", "c", "d", "e", "f", "g"),
        )

        val plan = MergeEngine()
            .plan(fileOf(incoming), LocalCatalogIndex.EMPTY, ImportMode.MERGE, T1)

        assertThat(plan.inserts.single().distractors).hasSize(5)
        assertThat(plan.report.distractorNotices.map { it.kind })
            .contains(DistractorNoticeKind.TRUNCATED)
    }

    @Test
    fun `reihenfolge uebersteht den round trip`() {
        val original = question(
            id = "a",
            distractors = listOf("Zuerst", "Dann", "Zuletzt"),
        )
        val raw = CatalogSerializer.encode(listOf(original), emptyList(), T2)
        val file = (CatalogSerializer.decode(raw) as Outcome.Success).value

        val plan = MergeEngine().plan(file, LocalCatalogIndex.EMPTY, ImportMode.MERGE, T2)

        assertThat(plan.inserts.single().distractors)
            .containsExactly("Zuerst", "Dann", "Zuletzt")
            .inOrder()
    }

    @Test
    fun `live validierung markiert antwort treffer und dubletten`() {
        val errors = DistractorRules.validateRows(
            rows = listOf("Mailand", "Rom", "", "mailand"),
            answer = "Rom",
        )

        assertThat(errors[0]).isNull()
        assertThat(errors[1]).isEqualTo(DistractorRowError.EQUALS_ANSWER)
        assertThat(errors[2]).isNull()
        assertThat(errors[3]).isEqualTo(DistractorRowError.DUPLICATE)
    }

    @Test
    fun `falschantworten gehen nicht in den contentHash ein`() {
        val ohne = question(id = "a", distractors = emptyList())
        val mit = question(id = "b", distractors = listOf("Mailand"))

        assertThat(ohne.contentHash).isEqualTo(mit.contentHash)
    }
}
