package de.schmusig.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.schmusig.core.data.SeedImporter
import de.schmusig.core.data.SettingsRepository
import de.schmusig.core.model.ThemeChoice
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Haelt, was die ganze App betrifft: das Theme und den Beispielkatalog beim ersten Start.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val seedImporter: SeedImporter,
) : ViewModel() {

    val theme: StateFlow<ThemeChoice> = settingsRepository.settings
        .map { it.theme }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemeChoice.SYSTEM,
        )

    init {
        // Der Seed laeuft durch denselben Importpfad wie ein Nutzer-Import (Abschnitt 9);
        // ist schon etwas eingespielt, tut der Aufruf nichts.
        viewModelScope.launch { seedImporter.importIfNeeded() }
    }
}
