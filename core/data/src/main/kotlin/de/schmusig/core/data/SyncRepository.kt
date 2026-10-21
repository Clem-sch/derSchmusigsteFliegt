package de.schmusig.core.data

import androidx.room.withTransaction
import de.schmusig.core.database.SchmusigDatabase
import de.schmusig.core.database.dao.QuestionDao
import de.schmusig.core.database.dao.StatsDao
import de.schmusig.core.model.Outcome
import de.schmusig.core.model.Question
import de.schmusig.core.sync.CatalogFileDto
import de.schmusig.core.sync.CatalogSerializer
import de.schmusig.core.sync.CsvQuestions
import de.schmusig.core.sync.ImportMode
import de.schmusig.core.sync.ImportReport
import de.schmusig.core.sync.LocalCatalogIndex
import de.schmusig.core.sync.MergeEngine
import de.schmusig.core.sync.MergePlan
import de.schmusig.core.sync.SOURCE_SEED
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Einschraenkung eines Exports (6.6). Leere Mengen heissen "alles". */
data class ExportFilter(
    val categories: Set<String> = emptySet(),
    val difficulties: Set<Int> = emptySet(),
) {
    val isAll: Boolean get() = categories.isEmpty() && difficulties.isEmpty()

    fun matches(question: Question): Boolean =
        (categories.isEmpty() || question.category in categories) &&
            (difficulties.isEmpty() || question.difficulty in difficulties)
}

/**
 * Das Ergebnis eines Dry-Runs: der Report fuer die Anzeige und - nur innerhalb der
 * Datenschicht sichtbar - der Plan, den eine Bestaetigung ausfuehren wuerde.
 */
class ImportPreview internal constructor(
    val report: ImportReport,
    internal val plan: MergePlan,
)

/**
 * Import und Export (Abschnitt 6).
 *
 * Der Ablauf ist immer zweistufig: [dryRun] rechnet und schreibt nichts, [apply] legt
 * erst ein Auto-Backup an und schreibt dann in einer Transaktion.
 */
interface SyncRepository {

    suspend fun exportToJson(filter: ExportFilter = ExportFilter()): Outcome<String>

    suspend fun dryRun(raw: String, mode: ImportMode): Outcome<ImportPreview>

    /** Fragen aus einer Tabelle anlegen (6.7) - ausdruecklich kein Abgleich. */
    suspend fun dryRunCsv(raw: String): Outcome<ImportPreview>

    suspend fun apply(preview: ImportPreview): Outcome<ImportReport>

    suspend fun importSeed(raw: String): Outcome<ImportReport>
}

@Singleton
class OfflineSyncRepository @Inject constructor(
    private val database: SchmusigDatabase,
    private val questionDao: QuestionDao,
    private val statsDao: StatsDao,
    private val autoBackupStore: AutoBackupStore,
    private val clock: AppClock,
) : SyncRepository {

    private val engine = MergeEngine()

    override suspend fun exportToJson(filter: ExportFilter): Outcome<String> =
        withContext(Dispatchers.IO) {
            runCatchingStorage {
                val questions = questionDao.loadAllIncludingDeleted()
                    .map { it.toDomain() }
                    .filter { filter.isAll || filter.matches(it) }
                val keptIds = questions.mapTo(mutableSetOf()) { it.id }
                val stats = statsDao.loadAll()
                    .map { it.toDomain() }
                    .filter { it.questionId in keptIds }
                CatalogSerializer.encode(
                    questions = questions,
                    stats = stats,
                    exportedAt = clock.now(),
                )
            }
        }

    override suspend fun dryRun(raw: String, mode: ImportMode): Outcome<ImportPreview> =
        withContext(Dispatchers.IO) {
            when (val decoded = CatalogSerializer.decode(raw)) {
                is Outcome.Failure -> decoded
                is Outcome.Success -> planFor(decoded.value, mode)
            }
        }

    override suspend fun dryRunCsv(raw: String): Outcome<ImportPreview> =
        withContext(Dispatchers.IO) {
            when (val parsed = CsvQuestions.parse(raw)) {
                is Outcome.Failure -> parsed
                is Outcome.Success -> planFor(
                    CatalogFileDto(questions = parsed.value, source = "csv"),
                    ImportMode.MERGE,
                )
            }
        }

    override suspend fun importSeed(raw: String): Outcome<ImportReport> =
        withContext(Dispatchers.IO) {
            // Der Seed laeuft durch denselben Pfad wie ein Nutzer-Import (Abschnitt 9),
            // nur ohne UI und ohne Auto-Backup - beim ersten Start gibt es nichts zu
            // sichern.
            when (val decoded = CatalogSerializer.decode(raw)) {
                is Outcome.Failure -> decoded
                is Outcome.Success -> when (
                    val preview = planFor(decoded.value.copy(source = SOURCE_SEED), ImportMode.MERGE)
                ) {
                    is Outcome.Failure -> preview
                    is Outcome.Success -> write(preview.value.plan)
                }
            }
        }

    override suspend fun apply(preview: ImportPreview): Outcome<ImportReport> =
        withContext(Dispatchers.IO) {
            if (!preview.report.writesSomething) {
                // Nichts zu schreiben heisst auch: kein Backup anlegen.
                return@withContext Outcome.Success(preview.report)
            }
            when (val backup = createBackup()) {
                is Outcome.Failure -> backup
                is Outcome.Success -> write(preview.plan)
            }
        }

    private suspend fun planFor(
        file: CatalogFileDto,
        mode: ImportMode,
    ): Outcome<ImportPreview> = runCatchingStorage {
        val local = LocalCatalogIndex(
            questionDao.loadAllIncludingDeleted().map { it.toDomain() },
        )
        val plan = engine.plan(file = file, local = local, mode = mode, now = clock.now())
        ImportPreview(report = plan.report, plan = plan)
    }

    private suspend fun createBackup(): Outcome<Unit> =
        when (val export = exportToJson()) {
            is Outcome.Failure -> export
            is Outcome.Success -> when (
                val written = autoBackupStore.write(export.value, clock.now())
            ) {
                is Outcome.Failure -> Outcome.Failure(written.error)
                is Outcome.Success -> Outcome.Success(Unit)
            }
        }

    /** Alles oder nichts: Fragen, Index und - nur bei RESTORE - Statistik. */
    private suspend fun write(plan: MergePlan): Outcome<ImportReport> = runCatchingStorage {
        database.withTransaction {
            questionDao.applyPlan(
                inserts = plan.inserts.map { it.toEntity() },
                updates = plan.updates.map { it.toEntity() },
                replaceEverything = plan.replaceEverything,
            )
            if (plan.mode == ImportMode.RESTORE) {
                statsDao.deleteAll()
                if (plan.stats.isNotEmpty()) {
                    statsDao.upsert(plan.stats.map { it.toEntity() })
                }
            }
        }
        plan.report
    }
}
