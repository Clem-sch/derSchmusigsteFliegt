package de.schmusig.quiz.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.schmusig.core.data.QuestionRepository
import de.schmusig.core.model.CatalogInventory
import de.schmusig.core.model.GameSession
import de.schmusig.game.api.GameMode
import de.schmusig.game.api.SessionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ein Eintrag im Hauptmenue pro Spielmodus - mit dem Anzeigenamen des Modus (7.1). */
data class GameModeEntry(val id: String, val displayName: String)

data class MainMenuUiState(
    val inventory: CatalogInventory = CatalogInventory(0, 0),
    val gameModes: List<GameModeEntry> = emptyList(),
    val resumableSession: GameSession? = null,
)

@HiltViewModel
class MainMenuViewModel @Inject constructor(
    questionRepository: QuestionRepository,
    private val sessionRepository: SessionRepository,
    gameModes: Set<@JvmSuppressWildcards GameMode>,
) : ViewModel() {

    /**
     * Die Modi kommen per Multibinding. Das Menue listet, was es findet - ein neues
     * Spielmodul erscheint hier, ohne dass diese Klasse angefasst werden muss.
     */
    private val modeEntries = gameModes
        .map { GameModeEntry(id = it.id, displayName = it.displayName) }
        .sortedBy { it.displayName }

    val uiState: StateFlow<MainMenuUiState> = combine(
        questionRepository.observeInventory(),
        sessionRepository.observeResumableSession(),
    ) { inventory, resumable ->
        MainMenuUiState(
            inventory = inventory,
            gameModes = modeEntries,
            resumableSession = resumable,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = MainMenuUiState(gameModes = modeEntries),
    )

    fun onDiscardResumable(sessionId: String) {
        viewModelScope.launch { sessionRepository.abandon(sessionId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
