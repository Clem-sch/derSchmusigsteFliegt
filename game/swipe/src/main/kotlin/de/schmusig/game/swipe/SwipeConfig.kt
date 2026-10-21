package de.schmusig.game.swipe

import de.schmusig.core.model.QuestionSelection
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Was in `GameSession.configJson` landet (5.3).
 *
 * Der [seed] steht hier, damit ein Resume dieselbe Fragenreihenfolge fortsetzt: die
 * Reihenfolge wird nicht gespeichert, sondern aus Filter und Seed neu berechnet.
 */
@Serializable
data class SwipeConfig(
    val categories: List<String> = emptyList(),
    val difficulties: List<Int> = emptyList(),
    val excludeRecentlyPlayed: Boolean = false,
    val seed: Long = 0L,
    val timerSeconds: Int = 0,
) {
    /**
     * Die Auswahl zum Zeitpunkt [now] wieder aufloesen. "Zuletzt gespielt" ist relativ
     * zur Gegenwart - bei einem Resume am naechsten Tag verschiebt sich das Fenster
     * dadurch leicht. Das ist der richtige Kompromiss: ein eingefrorener Zeitpunkt wuerde
     * Fragen ausschliessen, die laengst wieder dran sein dürfen.
     */
    fun toSelection(now: Instant): QuestionSelection = QuestionSelection(
        categories = categories.toSet(),
        difficulties = difficulties.toSet(),
        excludePlayedSince = if (excludeRecentlyPlayed) {
            now.minus(RECENTLY_PLAYED_HOURS, ChronoUnit.HOURS)
        } else {
            null
        },
    )

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val RECENTLY_PLAYED_HOURS = 24L

        private val json = Json { ignoreUnknownKeys = true }

        fun decode(raw: String): SwipeConfig =
            runCatching { json.decodeFromString(serializer(), raw) }
                .getOrDefault(SwipeConfig())
    }
}
