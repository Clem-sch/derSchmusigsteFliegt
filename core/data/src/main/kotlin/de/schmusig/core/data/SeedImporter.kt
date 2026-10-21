package de.schmusig.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.schmusig.core.database.dao.QuestionDao
import de.schmusig.core.model.AppError
import de.schmusig.core.model.Outcome
import de.schmusig.core.sync.ImportReport
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Spielt den mitgelieferten Beispielkatalog ein (Abschnitt 9).
 *
 * Wichtig: ueber denselben Importpfad wie ein Nutzer-Import, nicht ueber eine vorgebaute
 * Datenbankdatei. Damit ist der erste Start ein echter Test von Parser und Merge-Logik,
 * und es gibt keinen zweiten, divergierenden Ladeweg.
 */
@Singleton
class SeedImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncRepository: SyncRepository,
    private val settingsRepository: SettingsRepository,
    private val questionDao: QuestionDao,
) {

    /**
     * Beim ersten Start einspielen. Gibt `null` zurueck, wenn nichts zu tun war.
     *
     * Das Flag in DataStore wird auch dann gesetzt, wenn die Datenbank schon Fragen
     * enthaelt - sonst wuerde der Seed nach dem Loeschen aller Fragen wieder auftauchen.
     */
    suspend fun importIfNeeded(): Outcome<ImportReport>? {
        if (settingsRepository.seedImported.first()) return null

        if (questionDao.countAllIncludingDeleted() > 0) {
            settingsRepository.setSeedImported(true)
            return null
        }

        val result = importFromAssets()
        if (result is Outcome.Success) {
            settingsRepository.setSeedImported(true)
        }
        return result
    }

    /**
     * Debug-Eintrag "Beispielfragen erneut einspielen" (Abschnitt 9). Beim zweiten Mal
     * muss der Report ausschliesslich *unveraendert* melden - so ist Idempotenz in der
     * laufenden App pruefbar.
     */
    suspend fun importAgain(): Outcome<ImportReport> = importFromAssets()

    private suspend fun importFromAssets(): Outcome<ImportReport> {
        val raw = readAsset() ?: return Outcome.Failure(
            AppError.Io("Beispielkatalog fehlt im App-Paket"),
        )
        return syncRepository.importSeed(raw)
    }

    private suspend fun readAsset(): String? = withContext(Dispatchers.IO) {
        try {
            context.assets.open(ASSET_PATH).use { it.readBytes().decodeToString() }
        } catch (_: IOException) {
            null
        }
    }

    private companion object {
        const val ASSET_PATH = "seed/seed-questions.json"
    }
}
