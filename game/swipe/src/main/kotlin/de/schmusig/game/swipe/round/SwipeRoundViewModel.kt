package de.schmusig.game.swipe.round

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.schmusig.core.data.AppClock
import de.schmusig.core.data.QuestionRepository
import de.schmusig.core.data.SettingsRepository
import de.schmusig.core.model.AnswerVisibility
import de.schmusig.core.model.Question
import de.schmusig.core.model.SessionState
import de.schmusig.core.model.Verdict
import de.schmusig.game.api.PreparedRoundHolder
import de.schmusig.game.api.QuestionQueue
import de.schmusig.game.api.SessionRepository
import de.schmusig.game.swipe.SwipeConfig
import java.time.Duration
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SwipeRoundUiState(
    val loading: Boolean = true,
    val current: Question? = null,
    val answerVisibility: AnswerVisibility = AnswerVisibility.IMMEDIATE,
    val answerRevealed: Boolean = false,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 1,
    val answeredCount: Int = 0,
    val timeUp: Boolean = false,
    val outOfQuestions: Boolean = false,
    val alreadyInReview: Boolean = false,
) {
    val answerVisible: Boolean
        get() = answerVisibility == AnswerVisibility.IMMEDIATE || answerRevealed

    val progress: Float
        get() = if (totalSeconds <= 0) 0f else remainingSeconds.toFloat() / totalSeconds

    /** Die letzten 30 Sekunden werden farblich abgesetzt (8.2). */
    val inFinalStretch: Boolean get() = !timeUp && remainingSeconds in 1..FINAL_STRETCH_SECONDS

    val roundOver: Boolean get() = timeUp || outOfQuestions

    companion object {
        const val FINAL_STRETCH_SECONDS = 30
    }
}

/**
 * Die Kartenphase (8.2).
 *
 * Jeder Swipe schreibt sofort eine Attempt-Zeile - der Session-Cache ist persistent, nicht
 * im RAM. Rotation, App-Kill oder leerer Akku kosten darum keine Runde.
 */
@HiltViewModel
class SwipeRoundViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val questionRepository: QuestionRepository,
    private val settingsRepository: SettingsRepository,
    private val preparedRoundHolder: PreparedRoundHolder,
    private val clock: AppClock,
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle[ARG_SESSION_ID]) {
        "Die Kartenphase braucht eine Session-ID"
    }

    private val _uiState = MutableStateFlow(SwipeRoundUiState())
    val uiState: StateFlow<SwipeRoundUiState> = _uiState.asStateFlow()

    private var queue: QuestionQueue? = null
    private var cardShownAtElapsed: Long = 0L

    init {
        viewModelScope.launch { start() }
    }

    private suspend fun start() {
        val session = sessionRepository.session(sessionId)
        if (session == null) {
            _uiState.value = _uiState.value.copy(loading = false, outOfQuestions = true)
            return
        }
        if (session.state == SessionState.REVIEW) {
            // Eine Runde, deren Zeit schon um war: direkt in die Auswertung.
            _uiState.value = _uiState.value.copy(loading = false, alreadyInReview = true)
            return
        }

        val config = SwipeConfig.decode(session.configJson)
        val visibility = settingsRepository.settings.first().answerVisibility

        // Beim Resume duerfen Fragen, die schon gewertet wurden, nicht erneut kommen;
        // uebersprungene schon.
        val attempts = sessionRepository.attempts(sessionId)
        val settled = attempts
            .filter { it.verdict != Verdict.SKIPPED }
            .map { it.questionId }
            .toSet()

        val prepared = preparedRoundHolder.take(sessionId)
        val orderedIds = prepared?.orderedIds
            // Ohne vorbereitete Runde - etwa nach einem Prozess-Neustart - wird die
            // Reihenfolge aus Filter und Seed neu berechnet. Derselbe Seed ergibt
            // dieselbe Reihenfolge (8.2).
            ?: questionRepository
                .selectionIds(config.toSelection(clock.now()))
                .shuffled(Random(config.seed))

        val remainingIds = orderedIds.filterNot { it in settled }
        val preloaded = prepared?.preloaded?.filterNot { it.id in settled }.orEmpty()

        queue = QuestionQueue(
            orderedIds = remainingIds,
            loadQuestions = { ids -> questionRepository.loadByIds(ids) },
            preloaded = preloaded.take(remainingIds.size),
        ).also { it.prefetch() }

        val elapsed = Duration.between(session.startedAt, clock.now()).seconds.toInt()
        val remaining = (config.timerSeconds - elapsed).coerceAtLeast(0)

        _uiState.value = _uiState.value.copy(
            loading = false,
            answerVisibility = visibility,
            totalSeconds = config.timerSeconds.coerceAtLeast(1),
            remainingSeconds = remaining,
            answeredCount = attempts.count { it.verdict != Verdict.SKIPPED },
        )

        if (remaining <= 0) {
            finishByTime()
            return
        }

        drawNext()
        runTimer(remaining)
    }

    /**
     * Der Timer laeuft gegen `startedAt` und tickt ueber `elapsedRealtime` - also nicht
     * als Zaehler, der bei Pause stehenbleibt (8.3). `elapsedRealtime` laeuft auch
     * weiter, wenn das Geraet schlaeft.
     */
    private suspend fun runTimer(initialRemaining: Int) {
        val anchorElapsed = SystemClock.elapsedRealtime()
        var remaining = initialRemaining
        while (remaining > 0) {
            delay(TICK_MS)
            val sinceAnchor = (SystemClock.elapsedRealtime() - anchorElapsed) / 1000
            remaining = (initialRemaining - sinceAnchor).toInt().coerceAtLeast(0)
            if (_uiState.value.roundOver) return
            _uiState.value = _uiState.value.copy(remainingSeconds = remaining)
        }
        finishByTime()
    }

    private suspend fun finishByTime() {
        sessionRepository.markReview(sessionId)
        _uiState.value = _uiState.value.copy(timeUp = true, remainingSeconds = 0)
    }

    private suspend fun drawNext() {
        val queue = queue ?: return
        val next = queue.next()
        if (next == null) {
            // Gehen die gefilterten Fragen aus, endet die Phase vorzeitig mit Hinweis.
            sessionRepository.markReview(sessionId)
            _uiState.value = _uiState.value.copy(
                current = null,
                outOfQuestions = true,
            )
            return
        }
        cardShownAtElapsed = SystemClock.elapsedRealtime()
        _uiState.value = _uiState.value.copy(
            current = next,
            answerRevealed = false,
        )
    }

    fun onReveal() {
        _uiState.value = _uiState.value.copy(answerRevealed = true)
    }

    fun onVerdict(verdict: Verdict) {
        val question = _uiState.value.current ?: return
        if (_uiState.value.roundOver) return

        viewModelScope.launch {
            val durationMs = SystemClock.elapsedRealtime() - cardShownAtElapsed
            sessionRepository.recordAttempt(
                sessionId = sessionId,
                questionId = question.id,
                verdict = verdict,
                durationMs = durationMs,
            )
            if (verdict == Verdict.SKIPPED) {
                // Uebersprungen: die Frage wandert zurueck in den Pool (8.2).
                queue?.requeue(question)
            } else {
                _uiState.value = _uiState.value.copy(
                    answeredCount = _uiState.value.answeredCount + 1,
                )
            }
            drawNext()
        }
    }

    fun onAbort(onAborted: () -> Unit) {
        viewModelScope.launch {
            sessionRepository.abandon(sessionId)
            onAborted()
        }
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
        private const val TICK_MS = 250L
    }
}
