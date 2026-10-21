package de.schmusig.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.schmusig.core.data.AutoBackupStore
import de.schmusig.core.data.BackupFile
import de.schmusig.core.data.DocumentFileAccess
import de.schmusig.core.data.QuestionRepository
import de.schmusig.core.data.SeedImporter
import de.schmusig.core.data.SettingsRepository
import de.schmusig.core.model.AnswerVisibility
import de.schmusig.core.model.AppSettings
import de.schmusig.core.model.Outcome
import de.schmusig.core.model.ThemeChoice
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val tombstoneCount: Int = 0,
    val backups: List<BackupFile> = emptyList(),
    val busy: Boolean = false,
)

sealed interface SettingsEvent {
    data class TombstonesPurged(val count: Int) : SettingsEvent
    data class SeedImported(val new: Int, val unchanged: Int, val updated: Int) : SettingsEvent
    data object SeedFailed : SettingsEvent
    data object BackupExported : SettingsEvent
    data object BackupExportFailed : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val questionRepository: QuestionRepository,
    private val autoBackupStore: AutoBackupStore,
    private val documentFileAccess: DocumentFileAccess,
    private val seedImporter: SeedImporter,
) : ViewModel() {

    private val localState = MutableStateFlow(LocalState())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        localState,
    ) { settings, local ->
        SettingsUiState(
            settings = settings,
            tombstoneCount = local.tombstoneCount,
            backups = local.backups,
            busy = local.busy,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SettingsUiState(),
    )

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    init {
        refresh()
    }

    fun onThemeChange(choice: ThemeChoice) {
        viewModelScope.launch { settingsRepository.setTheme(choice) }
    }

    fun onAnswerVisibilityChange(visibility: AnswerVisibility) {
        viewModelScope.launch { settingsRepository.setAnswerVisibility(visibility) }
    }

    fun onDefaultTimerChange(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setDefaultTimerSeconds(
                seconds.coerceIn(MIN_TIMER_SECONDS, MAX_TIMER_SECONDS),
            )
        }
    }

    fun onPurgeTombstones() {
        viewModelScope.launch {
            localState.value = localState.value.copy(busy = true)
            val before = Instant.now().minus(TOMBSTONE_RETENTION_DAYS.toLong(), ChronoUnit.DAYS)
            val result = questionRepository.purgeTombstones(before)
            localState.value = localState.value.copy(busy = false)
            if (result is Outcome.Success) {
                eventChannel.send(SettingsEvent.TombstonesPurged(result.value))
            }
            refresh()
        }
    }

    fun onSeedAgain() {
        viewModelScope.launch {
            localState.value = localState.value.copy(busy = true)
            val result = seedImporter.importAgain()
            localState.value = localState.value.copy(busy = false)
            when (result) {
                is Outcome.Success -> eventChannel.send(
                    SettingsEvent.SeedImported(
                        new = result.value.inserted.size,
                        unchanged = result.value.unchanged.size,
                        updated = result.value.updated.size,
                    ),
                )

                is Outcome.Failure -> eventChannel.send(SettingsEvent.SeedFailed)
            }
            refresh()
        }
    }

    fun onExportBackup(name: String, target: Uri) {
        viewModelScope.launch {
            val content = autoBackupStore.read(name)
            if (content !is Outcome.Success) {
                eventChannel.send(SettingsEvent.BackupExportFailed)
                return@launch
            }
            when (documentFileAccess.writeText(target, content.value)) {
                is Outcome.Success -> eventChannel.send(SettingsEvent.BackupExported)
                is Outcome.Failure -> eventChannel.send(SettingsEvent.BackupExportFailed)
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            localState.value = localState.value.copy(
                tombstoneCount = questionRepository.countTombstones(),
                backups = autoBackupStore.list(),
            )
        }
    }

    private data class LocalState(
        val tombstoneCount: Int = 0,
        val backups: List<BackupFile> = emptyList(),
        val busy: Boolean = false,
    )

    companion object {
        const val TOMBSTONE_RETENTION_DAYS = 30
        const val MIN_TIMER_SECONDS = 30
        const val MAX_TIMER_SECONDS = 60 * 60
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
