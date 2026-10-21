package de.schmusig.game.swipe.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.schmusig.core.data.AppClock
import de.schmusig.core.data.QuestionRepository
import de.schmusig.core.data.SettingsRepository
import de.schmusig.core.model.AppSettings
import de.schmusig.core.model.CategoryCount
import de.schmusig.core.model.Question
import de.schmusig.core.model.QuestionSelection
import de.schmusig.game.api.PreparedRound
import de.schmusig.game.api.PreparedRoundHolder
import de.schmusig.game.api.SessionRepository
import de.schmusig.game.swipe.SwipeConfig
import de.schmusig.game.swipe.SwipeGameMode
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Warum gerade keine Frage passt - die UI benennt beide Faelle unterschiedlich (8.1). */
enum class EmptyReason { NO_CATEGORY, NO_DIFFICULTY, NO_MATCH }

data class SwipeSetupUiState(
    val loading: Boolean = true,
    val categories: List<CategoryCount> = emptyList(),
    val selectedCategories: Set<String> = emptySet(),
    val selectedDifficulties: Set<Int> = emptySet(),
    val excludeRecentlyPlayed: Boolean = false,
    val matchCount: Int = 0,
    val timerSeconds: Int = AppSettings.DEFAULT_TIMER_SECONDS,
    val preparing: Boolean = false,
    val starting: Boolean = false,
) {
    val catalogEmpty: Boolean get() = !loading && categories.isEmpty()

    val canContinue: Boolean get() = matchCount > 0

    val canStart: Boolean get() = canContinue && timerSeconds > 0

    val emptyReason: EmptyReason?
        get() = when {
            matchCount > 0 -> null
            selectedCategories.isEmpty() -> EmptyReason.NO_CATEGORY
            selectedDifficulties.isEmpty() -> EmptyReason.NO_DIFFICULTY
            else -> EmptyReason.NO_MATCH
        }
}

/**
 * Das Setup des Spielmodus (8.1), geteilt von Schritt 1 und Schritt 2.
 *
 * Darum ist dieses ViewModel an den Setup-Navigationsgraphen gebunden und nicht an einen
 * einzelnen Screen: das Vorladen laeuft in seinem Scope weiter, waehrend der Nutzer schon
 * in Schritt 2 das Drehrad dreht.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SwipeSetupViewModel @Inject constructor(
    private val questionRepository: QuestionRepository,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val preparedRoundHolder: PreparedRoundHolder,
    private val clock: AppClock,
) : ViewModel() {

    private val selection = MutableStateFlow(SelectionState())
    private val localState = MutableStateFlow(LocalState())

    /**
     * Der Live-Zaehler. `mapLatest` bricht eine noch laufende Zaehlabfrage ab, sobald der
     * naechste Chip angetippt wird - sonst koennte ein spaeter zurueckkommendes Ergebnis
     * eine veraltete Zahl anzeigen.
     */
    private val matchCount: StateFlow<Int> = selection
        .mapLatest { state ->
            questionRepository.countSelection(state.toQuestionSelection(clock.now()))
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val uiState: StateFlow<SwipeSetupUiState> = combine(
        selection,
        localState,
        matchCount,
        questionRepository.observeCategoryCounts(),
    ) { current, local, count, categories ->
        SwipeSetupUiState(
            loading = local.loading,
            categories = categories,
            selectedCategories = current.categories,
            selectedDifficulties = current.difficulties,
            excludeRecentlyPlayed = current.excludeRecentlyPlayed,
            matchCount = count,
            timerSeconds = local.timerSeconds,
            preparing = local.preparing,
            starting = local.starting,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SwipeSetupUiState(),
    )

    /** Die gemischte Reihenfolge und der Vorlade-Cache dieser Vorbereitung. */
    private var prepareJob: Job? = null
    private var preparedRound: PreparedRound? = null
    private var seed: Long = 0L

    init {
        viewModelScope.launch {
            val categories = questionRepository.observeCategoryCounts().first()
            val settings = settingsRepository.settings.first()
            val remembered = settingsRepository.lastGameSetup.first()

            // Vorbelegung: die gemerkte Auswahl, sonst sind alle Schalter aktiv (8.1).
            // Kategorien, die es nicht mehr gibt, fallen dabei weg.
            val available = categories.map { it.category }.toSet()
            selection.value = SelectionState(
                categories = remembered.categories
                    ?.intersect(available)
                    ?.takeIf { it.isNotEmpty() }
                    ?: available,
                difficulties = remembered.difficulties?.takeIf { it.isNotEmpty() }
                    ?: Question.DIFFICULTIES.toSet(),
                excludeRecentlyPlayed = remembered.excludeRecentlyPlayed,
            )
            localState.value = localState.value.copy(
                loading = false,
                timerSeconds = remembered.timerSeconds ?: settings.defaultTimerSeconds,
            )
        }
    }

    fun onCategoryToggle(category: String) = updateSelection { state ->
        state.copy(
            categories = if (category in state.categories) {
                state.categories - category
            } else {
                state.categories + category
            },
        )
    }

    fun onAllCategories() {
        val all = uiState.value.categories.map { it.category }.toSet()
        updateSelection { it.copy(categories = all) }
    }

    fun onNoCategories() = updateSelection { it.copy(categories = emptySet()) }

    fun onDifficultyToggle(difficulty: Int) = updateSelection { state ->
        state.copy(
            difficulties = if (difficulty in state.difficulties) {
                state.difficulties - difficulty
            } else {
                state.difficulties + difficulty
            },
        )
    }

    fun onExcludeRecentlyPlayedChange(value: Boolean) = updateSelection {
        it.copy(excludeRecentlyPlayed = value)
    }

    fun onTimerSecondsChange(seconds: Int) {
        localState.value = localState.value.copy(timerSeconds = seconds.coerceAtLeast(0))
    }

    /**
     * "Weiter": das Vorladen startet, die Navigation zu Schritt 2 passiert sofort - der
     * Nutzer wartet nie auf das Laden (8.1).
     */
    fun onContinue() {
        val current = selection.value
        prepareJob?.cancel()
        preparedRound = null
        seed = Random.nextLong()

        localState.value = localState.value.copy(preparing = true)
        prepareJob = viewModelScope.launch {
            val ids = questionRepository
                .selectionIds(current.toQuestionSelection(clock.now()))
                .shuffled(Random(seed))
            val preloaded = questionRepository.loadByIds(ids.take(PRELOAD_COUNT))
            val byId = preloaded.associateBy { it.id }
            preparedRound = PreparedRound(
                orderedIds = ids,
                preloaded = ids.take(PRELOAD_COUNT).mapNotNull { byId[it] },
            )
            localState.value = localState.value.copy(preparing = false)
        }

        viewModelScope.launch {
            settingsRepository.rememberGameSetup(
                categories = current.categories,
                difficulties = current.difficulties,
                excludeRecentlyPlayed = current.excludeRecentlyPlayed,
            )
        }
    }

    /**
     * Zurueck zu Schritt 1 bricht das Vorladen ab und verwirft den Cache - der Filter
     * kann sich jetzt aendern (8.1).
     */
    fun onBackToSelection() {
        prepareJob?.cancel()
        prepareJob = null
        preparedRound = null
        localState.value = localState.value.copy(preparing = false)
    }

    /**
     * "Start" legt erst jetzt die Session an. Waehrend des Setups entsteht keine - sonst
     * sammeln sich abgebrochene Setups als halbfertige Runden an (8.1).
     */
    fun onStart(onStarted: (String) -> Unit) {
        if (localState.value.starting) return
        localState.value = localState.value.copy(starting = true)

        viewModelScope.launch {
            // Wird "Start" gedrueckt, bevor das Vorladen fertig ist, wartet der Button
            // mit Ladezustand - der Bildschirm bleibt bedienbar.
            prepareJob?.join()

            val current = selection.value
            val timerSeconds = localState.value.timerSeconds
            val config = SwipeConfig(
                categories = current.categories.toList(),
                difficulties = current.difficulties.toList(),
                excludeRecentlyPlayed = current.excludeRecentlyPlayed,
                seed = seed,
                timerSeconds = timerSeconds,
            )
            val session = sessionRepository.startSession(
                modeId = SwipeGameMode.MODE_ID,
                configJson = config.encode(),
            )
            preparedRound?.let { preparedRoundHolder.put(session.id, it) }
            settingsRepository.rememberTimerSeconds(timerSeconds)

            localState.value = localState.value.copy(starting = false)
            onStarted(session.id)
        }
    }

    private inline fun updateSelection(block: (SelectionState) -> SelectionState) {
        selection.value = block(selection.value)
        // Eine Aenderung der Auswahl macht eine laufende Vorbereitung ungueltig.
        if (prepareJob != null) onBackToSelection()
    }

    private data class LocalState(
        val loading: Boolean = true,
        val timerSeconds: Int = AppSettings.DEFAULT_TIMER_SECONDS,
        val preparing: Boolean = false,
        val starting: Boolean = false,
    )

    private data class SelectionState(
        val categories: Set<String> = emptySet(),
        val difficulties: Set<Int> = emptySet(),
        val excludeRecentlyPlayed: Boolean = false,
    ) {
        fun toQuestionSelection(now: Instant) = QuestionSelection(
            categories = categories,
            difficulties = difficulties,
            excludePlayedSince = if (excludeRecentlyPlayed) {
                now.minus(SwipeConfig.RECENTLY_PLAYED_HOURS, ChronoUnit.HOURS)
            } else {
                null
            },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val PRELOAD_COUNT = 30
    }
}
