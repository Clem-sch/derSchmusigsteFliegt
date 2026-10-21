package de.schmusig.game.swipe

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import de.schmusig.core.model.Question
import de.schmusig.game.api.GameMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Der Spielmodus "Der schmusigste fliegt".
 *
 * Die [id] bleibt dauerhaft `"swipe"` und wird nie umbenannt - sie steht in
 * `GameSession.modeId` in der Datenbank (Abschnitt 8). Namen duerfen sich aendern,
 * Identifikatoren nicht.
 *
 * Der Anzeigename kommt aus `strings.xml` und nicht aus dem Code; darum braucht der Modus
 * den Application-Context.
 */
@Singleton
class SwipeGameMode @Inject constructor(
    @ApplicationContext private val context: Context,
) : GameMode {

    override val id: String = MODE_ID

    override val displayName: String
        get() = context.getString(R.string.swipe_mode_name)

    /**
     * Dieser Modus zieht jede Frage: Frage und Antwort genuegen ihm. Ein spaeterer
     * Multiple-Choice-Modus wuerde hier `question.distractors.size >= 3` fordern.
     */
    override fun supports(question: Question): Boolean = true

    companion object {
        const val MODE_ID = "swipe"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object SwipeGameModeModule {

    /**
     * Haengt den Modus in die Menge aller Modi. Das Hauptmenue listet diese Menge - ein
     * neues Modul erscheint dort, ohne dass `:app` angefasst werden muss.
     */
    @Provides
    @IntoSet
    @Singleton
    fun provideSwipeGameMode(mode: SwipeGameMode): GameMode = mode
}
