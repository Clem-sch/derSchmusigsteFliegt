package de.schmusig.core.model

/** Theme-Wahl aus den Einstellungen (7.4). */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/**
 * Wann die Antwort auf der Spielkarte zu sehen ist (7.4).
 *
 * [IMMEDIATE] ist der Moderator-Betrieb und der Default: wer die Karten haelt, liest vor
 * und braucht die Loesung sofort. [ON_TAP] ist das Selbststudium.
 */
enum class AnswerVisibility { IMMEDIATE, ON_TAP }

data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val answerVisibility: AnswerVisibility = AnswerVisibility.IMMEDIATE,
    val defaultTimerSeconds: Int = DEFAULT_TIMER_SECONDS,
) {
    companion object {
        const val DEFAULT_TIMER_SECONDS = 5 * 60
    }
}

/**
 * Die zuletzt im Spiel-Setup gewaehlten Filter. Werden gemerkt und beim naechsten Start
 * vorbelegt (8.1). `null` bei den Mengen heisst "noch nie gewaehlt" - dann sind im Setup
 * alle Schalter aktiv.
 */
data class LastGameSetup(
    val categories: Set<String>? = null,
    val difficulties: Set<Int>? = null,
    val excludeRecentlyPlayed: Boolean = false,
    val timerSeconds: Int? = null,
)
