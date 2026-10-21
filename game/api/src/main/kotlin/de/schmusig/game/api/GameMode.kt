package de.schmusig.game.api

import de.schmusig.core.model.Question

/**
 * Das Scharnier fuer kuenftige Spielmodi (Abschnitt 4).
 *
 * Ein neuer Modus ist ein neues Modul, das diese Schnittstelle implementiert und sich per
 * Multibinding in die Menge der Modi haengt. Das Hauptmenue listet alles, was es findet -
 * es muss dafuer nicht angefasst werden.
 *
 * Ein spaeterer Millionaer-Modus implementiert [supports] als
 * `question.distractors.size >= 3`. Die Faehigkeit wird damit **aus den Daten
 * abgeleitet** und nicht als Feld deklariert, so koennen Deklaration und Inhalt nicht
 * auseinanderlaufen.
 */
interface GameMode {
    val id: String
    val displayName: String

    /** Darf dieser Modus diese Frage ziehen? */
    fun supports(question: Question): Boolean
}
