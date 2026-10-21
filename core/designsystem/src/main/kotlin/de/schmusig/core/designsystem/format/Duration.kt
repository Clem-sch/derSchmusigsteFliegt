package de.schmusig.core.designsystem.format

/** `mm:ss`, auch jenseits von 59 Minuten. Fuer die Restzeit auf der Spielkarte (8.2). */
fun formatClock(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60
    return "%02d:%02d".format(minutes, seconds)
}

/**
 * Dauer in Worten, fuer Einstellungen und Setup-Presets: "5 min", "1:30 min", "45 s".
 * Bewusst knapp - die Zahl ist die Information, nicht der Satz darum.
 */
fun formatDurationLabel(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60
    return when {
        minutes == 0 -> "$seconds s"
        seconds == 0 -> "$minutes min"
        else -> "%d:%02d min".format(minutes, seconds)
    }
}
