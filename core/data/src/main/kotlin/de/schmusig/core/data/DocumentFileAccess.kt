package de.schmusig.core.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import de.schmusig.core.model.AppError
import de.schmusig.core.model.Outcome
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lesen und Schreiben der vom Nutzer gewaehlten Dateien.
 *
 * Die App hat keine Storage-Permissions (Abschnitt 2); die Uri kommt immer aus
 * `ACTION_OPEN_DOCUMENT` beziehungsweise `ACTION_CREATE_DOCUMENT`. Der ContentResolver
 * bleibt hier gekapselt, damit die Feature-Module nur mit Text arbeiten.
 */
@Singleton
class DocumentFileAccess @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun readText(uri: Uri): Outcome<String> = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().decodeToString()
            }
            if (text == null) {
                Outcome.Failure(AppError.Io("Datei konnte nicht geoeffnet werden"))
            } else {
                Outcome.Success(text)
            }
        } catch (error: IOException) {
            Outcome.Failure(AppError.Io(error.message))
        } catch (error: SecurityException) {
            Outcome.Failure(AppError.Io(error.message))
        }
    }

    suspend fun writeText(uri: Uri, text: String): Outcome<Unit> = withContext(Dispatchers.IO) {
        try {
            // "wt" kuerzt eine bestehende Datei - ohne das bleibt bei einer kuerzeren
            // Ausgabe der Rest der alten Datei stehen.
            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(text.encodeToByteArray())
            } ?: return@withContext Outcome.Failure(
                AppError.Io("Datei konnte nicht geschrieben werden"),
            )
            Outcome.Success(Unit)
        } catch (error: IOException) {
            Outcome.Failure(AppError.Io(error.message))
        } catch (error: SecurityException) {
            Outcome.Failure(AppError.Io(error.message))
        }
    }
}
