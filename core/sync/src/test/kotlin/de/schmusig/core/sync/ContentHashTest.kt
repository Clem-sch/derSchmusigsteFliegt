package de.schmusig.core.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Abschnitt 11, Test 1. */
class ContentHashTest {

    @Test
    fun `normalisierung kuerzt trimmt und faltet leerzeichen`() {
        assertThat(ContentHash.normalize("  Hallo   Welt  ")).isEqualTo("hallo welt")
        assertThat(ContentHash.normalize("Hallo\t\nWelt")).isEqualTo("hallo welt")
    }

    @Test
    fun `hash ist stabil gegen gross klein schreibung`() {
        val a = ContentHash.of("Hauptstadt von Italien?", "Rom")
        val b = ContentHash.of("HAUPTSTADT VON ITALIEN?", "ROM")
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `hash ist stabil gegen mehrfache und umschliessende leerzeichen`() {
        val a = ContentHash.of("Hauptstadt von Italien?", "Rom")
        val b = ContentHash.of("  Hauptstadt   von  Italien?  ", "  Rom ")
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `umlaute bleiben erhalten und veraendern den hash`() {
        assertThat(ContentHash.normalize("Fußgängerübergang")).isEqualTo("fußgängerübergang")
        val withUmlaut = ContentHash.of("Wie heißt die Stadt?", "Köln")
        val withoutUmlaut = ContentHash.of("Wie heisst die Stadt?", "Koeln")
        assertThat(withUmlaut).isNotEqualTo(withoutUmlaut)
    }

    @Test
    fun `frage und antwort sind durch das trennzeichen nicht vertauschbar`() {
        // Ohne das \u0000-Trennzeichen waere "ab" + "c" nicht von "a" + "bc" zu
        // unterscheiden.
        assertThat(ContentHash.of("ab", "c")).isNotEqualTo(ContentHash.of("a", "bc"))
    }

    @Test
    fun `hash ist sha256 in hex kleinbuchstaben`() {
        val hash = ContentHash.of("a", "b")
        assertThat(hash).hasLength(64)
        assertThat(hash).matches("[0-9a-f]{64}")
    }

    @Test
    fun `hash eines bekannten wertes bleibt konstant`() {
        // Nagelt die Berechnung fest: aendert sich dieser Wert, koennen zwei
        // App-Versionen dieselbe Frage nicht mehr als Dublette erkennen.
        assertThat(ContentHash.of("Wie heißt die Hauptstadt von Italien?", "Rom"))
            .isEqualTo("9ba4e1fc93f9bd174d35ffe27169485a910ccd8ea443409c04642cdc5ee360d8")
    }
}
