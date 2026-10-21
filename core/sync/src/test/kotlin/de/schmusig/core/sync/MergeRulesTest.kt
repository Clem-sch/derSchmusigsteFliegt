package de.schmusig.core.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Abschnitt 11, Test 4: jede Regel aus 6.3 einzeln. */
class MergeRulesTest {

    private val engine = engineWithFixedIds("neu-1", "neu-2", "neu-3")

    @Test
    fun `regel 2 - unbekannte id und unbekannter inhalt wird eingefuegt`() {
        val incoming = question(id = "a")

        val plan = engine.plan(fileOf(incoming), LocalCatalogIndex.EMPTY, ImportMode.MERGE, T1)

        assertThat(plan.inserts).containsExactly(incoming)
        assertThat(plan.report.inserted).hasSize(1)
        assertThat(plan.report.unchanged).isEmpty()
    }

    @Test
    fun `regel 3 - unbekannte id aber bekannter contentHash wird als dublette gemeldet`() {
        val local = question(id = "lokal")
        val incoming = question(id = "fremd")

        val plan = engine.plan(
            fileOf(incoming),
            LocalCatalogIndex(listOf(local)),
            ImportMode.MERGE,
            T1,
        )

        assertThat(plan.inserts).isEmpty()
        assertThat(plan.updates).isEmpty()
        assertThat(plan.report.duplicates).hasSize(1)
        assertThat(plan.report.duplicates.single().existingId).isEqualTo("lokal")
    }

    @Test
    fun `regel 3 greift auch fuer dubletten innerhalb einer datei`() {
        val first = question(id = "a")
        val second = question(id = "b")

        val plan = engine.plan(
            fileOf(first, second),
            LocalCatalogIndex.EMPTY,
            ImportMode.MERGE,
            T1,
        )

        assertThat(plan.inserts).containsExactly(first)
        assertThat(plan.report.duplicates).hasSize(1)
    }

    @Test
    fun `regel 4 - identischer datensatz bleibt unveraendert`() {
        val local = question(id = "a")

        val plan = engine.plan(
            fileOf(local),
            LocalCatalogIndex(listOf(local)),
            ImportMode.MERGE,
            T1,
        )

        assertThat(plan.inserts).isEmpty()
        assertThat(plan.updates).isEmpty()
        assertThat(plan.report.unchanged).hasSize(1)
    }

    @Test
    fun `regel 4 - neuerer datensatz gewinnt`() {
        val local = question(id = "a", category = "Geografie", updatedAt = T0)
        val incoming = question(id = "a", category = "Erdkunde", updatedAt = T1, revision = 2)

        val plan = engine.plan(
            fileOf(incoming),
            LocalCatalogIndex(listOf(local)),
            ImportMode.MERGE,
            T2,
        )

        assertThat(plan.updates).hasSize(1)
        assertThat(plan.updates.single().category).isEqualTo("Erdkunde")
        assertThat(plan.report.updated).hasSize(1)
    }

    @Test
    fun `regel 4 - aelterer datensatz wird uebersprungen`() {
        val local = question(id = "a", category = "Erdkunde", updatedAt = T2, revision = 3)
        val incoming = question(id = "a", category = "Geografie", updatedAt = T0, revision = 1)

        val plan = engine.plan(
            fileOf(incoming),
            LocalCatalogIndex(listOf(local)),
            ImportMode.MERGE,
            T2,
        )

        assertThat(plan.updates).isEmpty()
        assertThat(plan.report.skippedOlder).hasSize(1)
    }

    @Test
    fun `regel 4 - tombstone wird uebernommen`() {
        val local = question(id = "a", updatedAt = T0)
        val incoming = question(id = "a", updatedAt = T1, revision = 2, deletedAt = T1)

        val plan = engine.plan(
            fileOf(incoming),
            LocalCatalogIndex(listOf(local)),
            ImportMode.MERGE,
            T2,
        )

        assertThat(plan.updates).hasSize(1)
        assertThat(plan.updates.single().deletedAt).isEqualTo(T1)
        assertThat(plan.report.deleted).hasSize(1)
        assertThat(plan.report.updated).isEmpty()
    }

    @Test
    fun `zeitstempel gleichstand - hoehere revision gewinnt`() {
        val local = question(id = "a", category = "Geografie", updatedAt = T1, revision = 1)
        val incoming = question(id = "a", category = "Erdkunde", updatedAt = T1, revision = 2)

        val plan = engine.plan(
            fileOf(incoming),
            LocalCatalogIndex(listOf(local)),
            ImportMode.MERGE,
            T2,
        )

        assertThat(plan.updates.single().category).isEqualTo("Erdkunde")
        assertThat(plan.report.conflicts).isEmpty()
    }

    @Test
    fun `zeitstempel und revision gleichstand - lokal gewinnt und konflikt wird gemeldet`() {
        val local = question(id = "a", category = "Geografie", updatedAt = T1, revision = 2)
        val incoming = question(id = "a", category = "Erdkunde", updatedAt = T1, revision = 2)

        val plan = engine.plan(
            fileOf(incoming),
            LocalCatalogIndex(listOf(local)),
            ImportMode.MERGE,
            T2,
        )

        assertThat(plan.updates).isEmpty()
        assertThat(plan.report.conflicts).hasSize(1)
        assertThat(plan.report.conflicts.single().category).isEqualTo("Geografie")
    }

    @Test
    fun `lokal geloeschte frage kann unter neuer id wieder angelegt werden`() {
        // Tombstones zaehlen bei der Hash-Suche nicht mit - sonst liesse sich eine
        // geloeschte Frage nie wieder aufnehmen.
        val deletedLocal = question(id = "alt", deletedAt = T0)
        val incoming = question(id = "neu")

        val plan = engine.plan(
            fileOf(incoming),
            LocalCatalogIndex(listOf(deletedLocal)),
            ImportMode.MERGE,
            T1,
        )

        assertThat(plan.inserts).hasSize(1)
        assertThat(plan.report.duplicates).isEmpty()
    }

    /** Abschnitt 11, Test 8: die Warnung aus 6.3. */
    @Test
    fun `nur ergaenzte falschantworten gelten als aktualisiert nicht als unveraendert`() {
        val local = question(id = "a", distractors = emptyList(), updatedAt = T0)
        val incoming = question(
            id = "a",
            distractors = listOf("Mailand", "Neapel", "Turin"),
            updatedAt = T1,
            revision = 2,
        )
        // Gleicher Wortlaut, gleiche Antwort - also derselbe contentHash.
        assertThat(incoming.contentHash).isEqualTo(local.contentHash)

        val plan = engine.plan(
            fileOf(incoming),
            LocalCatalogIndex(listOf(local)),
            ImportMode.MERGE,
            T2,
        )

        assertThat(plan.report.unchanged).isEmpty()
        assertThat(plan.report.updated).hasSize(1)
        assertThat(plan.updates.single().distractors)
            .containsExactly("Mailand", "Neapel", "Turin")
            .inOrder()
    }

    @Test
    fun `anlegedatum bleibt beim aktualisieren der fruehere wert`() {
        val local = question(id = "a", createdAt = T0, updatedAt = T0)
        val incoming = question(id = "a", category = "Erdkunde", createdAt = T1, updatedAt = T1)

        val plan = engine.plan(
            fileOf(incoming),
            LocalCatalogIndex(listOf(local)),
            ImportMode.MERGE,
            T2,
        )

        assertThat(plan.updates.single().createdAt).isEqualTo(T0)
    }
}
