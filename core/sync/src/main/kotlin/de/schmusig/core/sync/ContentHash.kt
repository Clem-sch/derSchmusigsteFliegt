package de.schmusig.core.sync

import java.security.MessageDigest

/**
 * Normalisierung und Inhalts-Hash nach Abschnitt 5.2.
 *
 * Diese Implementierung ist die **einzige** im Projekt. Sie muss auf allen Geraeten
 * identisch rechnen, darum:
 *  - `lowercase()` ohne Locale-Argument (Kotlin rechnet dann locale-unabhaengig; mit
 *    Geraete-Locale wuerde z. B. die tuerkische Behandlung von "I" den Hash veraendern),
 *  - UTF-8 als feste Byte-Kodierung,
 *  - Hex in Kleinbuchstaben, ohne Trennzeichen.
 *
 * `distractors` gehen bewusst **nicht** in den Hash ein (Abschnitt 5.4): zwei Fragen mit
 * gleichem Wortlaut und gleicher Antwort sind Dubletten, auch wenn nur eine
 * Falschantworten mitbringt.
 */
object ContentHash {

    private val whitespace = Regex("\\s+")

    /** `s.trim().lowercase().replace(Regex("\\s+"), " ")` */
    fun normalize(value: String): String =
        value.trim().lowercase().replace(whitespace, " ")

    /** `sha256( normalize(question) + "\u0000" + normalize(answer) )` */
    fun of(question: String, answer: String): String {
        val payload = normalize(question) + "\u0000" + normalize(answer)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val unsigned = byte.toInt() and 0xFF
                append(HEX[unsigned shr 4])
                append(HEX[unsigned and 0x0F])
            }
        }
    }

    private const val HEX = "0123456789abcdef"
}
