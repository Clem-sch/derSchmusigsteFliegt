package de.schmusig.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.schmusig.core.model.AppError
import de.schmusig.core.model.Outcome
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ein automatisches Backup im App-internen Ordner. */
data class BackupFile(
    val name: String,
    val createdAt: Instant,
    val sizeBytes: Long,
)

/**
 * Das Sicherheitsnetz vor jedem Import (Abschnitt 6.6): vor dem Schreiben legt die App
 * einen vollstaendigen Export im App-internen Ordner ab, rollierend die letzten fuenf.
 *
 * Intern heisst: `filesDir`, also ohne Permission und ohne SAF - der Nutzer muss nichts
 * bestaetigen, damit das Netz auch dann haelt, wenn er den Dialog wegklickt.
 */
@Singleton
class AutoBackupStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    suspend fun write(content: String, at: Instant): Outcome<BackupFile> =
        withContext(Dispatchers.IO) {
            try {
                val name = "auto_" + TIMESTAMP.format(at.atOffset(ZoneOffset.UTC)) + ".json"
                val file = File(directory, name)
                file.writeText(content)
                pruneOldest()
                Outcome.Success(
                    BackupFile(
                        name = file.name,
                        createdAt = Instant.ofEpochMilli(file.lastModified()),
                        sizeBytes = file.length(),
                    ),
                )
            } catch (error: IOException) {
                Outcome.Failure(AppError.Io(error.message))
            }
        }

    suspend fun list(): List<BackupFile> = withContext(Dispatchers.IO) {
        directory.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                BackupFile(
                    name = file.name,
                    createdAt = Instant.ofEpochMilli(file.lastModified()),
                    sizeBytes = file.length(),
                )
            }
            .orEmpty()
    }

    suspend fun read(name: String): Outcome<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(directory, File(name).name)
            if (!file.isFile) {
                Outcome.Failure(AppError.NotFound(name))
            } else {
                Outcome.Success(file.readText())
            }
        } catch (error: IOException) {
            Outcome.Failure(AppError.Io(error.message))
        }
    }

    private fun pruneOldest() {
        val files = directory.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(KEEP).forEach { it.delete() }
    }

    private companion object {
        const val DIRECTORY = "backups"
        const val KEEP = 5
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")
    }
}
