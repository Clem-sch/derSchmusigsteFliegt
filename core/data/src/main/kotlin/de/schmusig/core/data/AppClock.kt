package de.schmusig.core.data

import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zeitquelle der App. Abstrahiert, damit Tests Zeitstempel festnageln koennen.
 *
 * Alle Werte werden auf Millisekunden gekuerzt: die Datenbank speichert Millisekunden
 * seit Epoch, das JSON schreibt ISO-8601. Ohne Kuerzung wuerde ein Export direkt nach
 * dem Anlegen Nanosekunden enthalten, die der Datenbankwert nicht hat - der Round-Trip
 * waere dann feldweise nicht mehr identisch.
 */
interface AppClock {
    fun now(): Instant
}

@Singleton
class SystemAppClock @Inject constructor() : AppClock {
    override fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
}
