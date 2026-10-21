package de.schmusig.game.swipe.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.schmusig.core.data.QuestionRepository
import de.schmusig.core.model.Attempt
import de.schmusig.core.model.Question
import de.schmusig.core.model.Verdict
import de.schmusig.game.api.SessionRepository
import de.schmusig.game.swipe.SwipeConfig
import de.schmusig.game.swipe.SwipeGameMode
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReviewRow(
    val attempt: Attempt,
    val question: Question?,
)

data class SwipeReviewUiState(
    val loading: Boolean = true,
    val rows: List<ReviewRow> = emptyList(),
    val finishing: Boolean = false,
) {
    val correct: Int get() = rows.count { it.attempt.verdict == Verdict.CORRECT }
    val wrong: Int get() = rows.count { it.attempt.verdict == Verdict.WRONG }
    val skipped: Int get() = rows.count { it.attempt.verdict == Verdict.SKIPPED }
}

/**
 * Die Review-Liste (8.4) - der Kern der Auswertung.
 *
 * Die Wertungen kommen aus der Datenbank, nicht aus dem Speicher der Kartenphase: nur so
 * steht die Liste auch nach einem Neustart vollstaendig da. Geaenderte Wertungen werden
 * sofort geschrieben, die Statistik aber erst beim Abschliessen fortgeschrieben - sonst
 * wuerden Korrekturen nicht wirken.
 */
@HiltViewModel
class SwipeReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val questionRepository: QuestionRepository,
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle[ARG_SESSION_ID]) {
        "Die Auswertung braucht eine Session-ID"
    }

    private val _uiState = MutableStateFlow(SwipeReviewUiState())
    val uiState: StateFlow<SwipeReviewUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    /**
     * Wird auch nach der Rueckkehr aus dem Editor aufgerufen: dort kann sich Kategorie,
     * Schwierigkeit oder der Text geaendert haben.
     */
    fun reload() {
        viewModelScope.launch {
            val attempts = sessionRepository.attempts(sessionId)
            val questions = questionRepository
                .loadByIds(attempts.map { it.questionId }.distinct())
                .associateBy { it.id }
            _uiState.value = SwipeReviewUiState(
                loading = false,
                rows = attempts.map { attempt ->
                    ReviewRow(attempt = attempt, question = questions[attempt.questionId])
                },
            )
        }
    }

    /**
     * Fehlswipes passieren staendig; ohne Korrekturmoeglichkeit ist die Statistik
     * wertlos (8.4).
     */
    fun onVerdictToggle(attemptId: String) {
        val row = _uiState.value.rows.firstOrNull { it.attempt.id == attemptId } ?: return
        val next = row.attempt.verdict.next()
        // Optimistisch anzeigen und parallel schreiben - die Liste soll ohne Verzoegerung
        // reagieren.
        _uiState.value = _uiState.value.copy(
            rows = _uiState.value.rows.map { current ->
                if (current.attempt.id == attemptId) {
                    current.copy(attempt = current.attempt.copy(verdict = next))
                } else {
                    current
                }
            },
        )
        viewModelScope.launch { sessionRepository.setVerdict(attemptId, next) }
    }

    /**
     * "Neue Runde mit gleichen Einstellungen" (8.4).
     *
     * Die laufende Runde wird dabei abgeschlossen - inklusive Statistik-Commit. Sonst
     * bliebe sie im Zustand REVIEW stehen und die App wuerde sie beim naechsten Start als
     * offene Runde anbieten, obwohl der Nutzer sie gerade hinter sich gelassen hat.
     *
     * Der Seed wird neu gezogen, sonst kaeme dieselbe Fragenreihenfolge noch einmal.
     */
    fun onNewRound(onStarted: (String) -> Unit) {
        if (_uiState.value.finishing) return
        _uiState.value = _uiState.value.copy(finishing = true)
        viewModelScope.launch {
            val previous = sessionRepository.session(sessionId)
            sessionRepository.finishSession(sessionId)

            val config = SwipeConfig.decode(previous?.configJson.orEmpty())
                .copy(seed = Random.nextLong())
            val session = sessionRepository.startSession(
                modeId = SwipeGameMode.MODE_ID,
                configJson = config.encode(),
            )
            _uiState.value = _uiState.value.copy(finishing = false)
            onStarted(session.id)
        }
    }

    fun onFinish(onFinished: () -> Unit) {
        if (_uiState.value.finishing) return
        _uiState.value = _uiState.value.copy(finishing = true)
        viewModelScope.launch {
            sessionRepository.finishSession(sessionId)
            _uiState.value = _uiState.value.copy(finishing = false)
            onFinished()
        }
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
    }
}
