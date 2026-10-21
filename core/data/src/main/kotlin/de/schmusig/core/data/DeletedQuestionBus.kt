package de.schmusig.core.data

import de.schmusig.core.model.Question
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Traegt die zuletzt geloeschte Frage vom Editor zur Liste, die die Undo-Snackbar zeigt
 * (7.2).
 *
 * Geloescht wird im Editor, die Snackbar gehoert aber in die Liste, in die der Nutzer
 * danach zurueckkehrt. Ohne diesen Umweg muessten Katalog und Editor voneinander wissen -
 * genau das verbieten die Modulregeln.
 *
 * Bewusst ein [StateFlow] und kein SharedFlow: wenn der Editor veroeffentlicht, sammelt
 * die Liste im Hintergrund gerade nicht, und ein Ereignis ohne Abnehmer waere verloren.
 * Der Wert bleibt stehen, bis ihn jemand mit [consume] abholt.
 */
@Singleton
class DeletedQuestionBus @Inject constructor() {

    private val state = MutableStateFlow<Question?>(null)

    val pending: StateFlow<Question?> = state.asStateFlow()

    fun publish(question: Question) {
        state.value = question
    }

    /** Holt den Eintrag ab und raeumt ihn weg, damit die Snackbar nur einmal erscheint. */
    fun consume(): Question? = state.value.also { state.value = null }

    fun clear() {
        state.value = null
    }
}
