package de.schmusig.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import de.schmusig.core.model.AnswerVisibility
import de.schmusig.core.model.AppSettings
import de.schmusig.core.model.LastGameSetup
import de.schmusig.core.model.Question
import de.schmusig.core.model.ThemeChoice
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "schmusig_settings",
)

/**
 * Einstellungen und gemerkte Setup-Auswahl (7.4 / 8.1) in DataStore.
 *
 * Auch das Flag fuer den Beispielkatalog liegt hier, nicht in der Datenbank: es muss das
 * Loeschen aller Fragen ueberleben, damit der Seed nicht wieder erscheint (Abschnitt 9).
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val store = context.settingsDataStore

    private val preferences: Flow<Preferences> = store.data
        .catch { error ->
            // Eine kaputte Preferences-Datei darf die App nicht am Start hindern.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }

    val settings: Flow<AppSettings> = preferences.map { prefs ->
        AppSettings(
            theme = prefs[Keys.theme]?.toThemeChoice() ?: ThemeChoice.SYSTEM,
            answerVisibility = prefs[Keys.answerVisibility]?.toAnswerVisibility()
                ?: AnswerVisibility.IMMEDIATE,
            defaultTimerSeconds = prefs[Keys.defaultTimerSeconds]
                ?: AppSettings.DEFAULT_TIMER_SECONDS,
        )
    }

    val lastGameSetup: Flow<LastGameSetup> = preferences.map { prefs ->
        LastGameSetup(
            categories = prefs[Keys.lastCategories],
            difficulties = prefs[Keys.lastDifficulties]
                ?.mapNotNull { it.toIntOrNull() }
                ?.filter { it in Question.MIN_DIFFICULTY..Question.MAX_DIFFICULTY }
                ?.toSet(),
            excludeRecentlyPlayed = prefs[Keys.excludeRecentlyPlayed] ?: false,
            timerSeconds = prefs[Keys.lastTimerSeconds],
        )
    }

    val seedImported: Flow<Boolean> = preferences.map { prefs ->
        prefs[Keys.seedImported] ?: false
    }

    suspend fun setTheme(choice: ThemeChoice) = edit { it[Keys.theme] = choice.name }

    suspend fun setAnswerVisibility(visibility: AnswerVisibility) =
        edit { it[Keys.answerVisibility] = visibility.name }

    suspend fun setDefaultTimerSeconds(seconds: Int) =
        edit { it[Keys.defaultTimerSeconds] = seconds.coerceAtLeast(0) }

    suspend fun setSeedImported(imported: Boolean) =
        edit { it[Keys.seedImported] = imported }

    suspend fun rememberGameSetup(
        categories: Set<String>,
        difficulties: Set<Int>,
        excludeRecentlyPlayed: Boolean,
    ) = edit { prefs ->
        prefs[Keys.lastCategories] = categories
        prefs[Keys.lastDifficulties] = difficulties.map { it.toString() }.toSet()
        prefs[Keys.excludeRecentlyPlayed] = excludeRecentlyPlayed
    }

    suspend fun rememberTimerSeconds(seconds: Int) =
        edit { it[Keys.lastTimerSeconds] = seconds.coerceAtLeast(0) }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            store.edit(block)
        } catch (_: IOException) {
            // Einstellungen sind Komfort, kein Datenbestand: ein fehlgeschlagener
            // Schreibvorgang darf keine Exception durch die Schichten schicken.
        }
    }

    private fun String.toThemeChoice(): ThemeChoice? =
        ThemeChoice.entries.firstOrNull { it.name == this }

    private fun String.toAnswerVisibility(): AnswerVisibility? =
        AnswerVisibility.entries.firstOrNull { it.name == this }

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val answerVisibility = stringPreferencesKey("answer_visibility")
        val defaultTimerSeconds = intPreferencesKey("default_timer_seconds")
        val seedImported = booleanPreferencesKey("seed_imported")
        val lastCategories = stringSetPreferencesKey("last_categories")
        val lastDifficulties = stringSetPreferencesKey("last_difficulties")
        val excludeRecentlyPlayed = booleanPreferencesKey("exclude_recently_played")
        val lastTimerSeconds = intPreferencesKey("last_timer_seconds")
    }
}
