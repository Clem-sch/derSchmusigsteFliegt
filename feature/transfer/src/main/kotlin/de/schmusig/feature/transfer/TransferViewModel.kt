package de.schmusig.feature.transfer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.schmusig.core.data.DocumentFileAccess
import de.schmusig.core.data.ExportFilter
import de.schmusig.core.data.ImportPreview
import de.schmusig.core.data.QuestionRepository
import de.schmusig.core.data.SyncRepository
import de.schmusig.core.model.AppError
import de.schmusig.core.model.CategoryCount
import de.schmusig.core.model.Outcome
import de.schmusig.core.sync.ImportMode
import de.schmusig.core.sync.ImportReport
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TransferUiState(
    val mode: ImportMode = ImportMode.MERGE,
    val categories: List<CategoryCount> = emptyList(),
    val exportCategories: Set<String> = emptySet(),
    val busy: Boolean = false,
    val preview: ImportPreview? = null,
    val finishedReport: ImportReport? = null,
    val error: AppError? = null,
    val exportDone: Boolean = false,
) {
    val localQuestionCount: Int get() = categories.sumOf { it.count }
}

@HiltViewModel
class TransferViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val questionRepository: QuestionRepository,
    private val documentFileAccess: DocumentFileAccess,
) : ViewModel() {

    private val localState = MutableStateFlow(TransferUiState())

    val uiState: StateFlow<TransferUiState> = combine(
        localState,
        questionRepository.observeCategoryCounts(),
    ) { state, categories ->
        state.copy(categories = categories)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = TransferUiState(),
    )

    fun onModeChange(mode: ImportMode) = update { it.copy(mode = mode, preview = null) }

    fun onExportCategoryToggle(category: String) = update { state ->
        state.copy(
            exportCategories = if (category in state.exportCategories) {
                state.exportCategories - category
            } else {
                state.exportCategories + category
            },
        )
    }

    fun suggestedExportName(): String =
        "quizkatalog_" + FILE_TIMESTAMP.format(LocalDateTime.now()) + ".json"

    fun onExport(target: Uri) {
        viewModelScope.launch {
            update { it.copy(busy = true, error = null, exportDone = false) }
            val filter = ExportFilter(categories = localState.value.exportCategories)
            when (val export = syncRepository.exportToJson(filter)) {
                is Outcome.Failure -> update { it.copy(busy = false, error = export.error) }
                is Outcome.Success -> when (
                    val written = documentFileAccess.writeText(target, export.value)
                ) {
                    is Outcome.Failure -> update { it.copy(busy = false, error = written.error) }
                    is Outcome.Success -> update { it.copy(busy = false, exportDone = true) }
                }
            }
        }
    }

    fun onImportFileChosen(source: Uri) {
        viewModelScope.launch {
            update { it.copy(busy = true, error = null, finishedReport = null) }
            when (val read = documentFileAccess.readText(source)) {
                is Outcome.Failure -> update { it.copy(busy = false, error = read.error) }
                is Outcome.Success -> {
                    val result = syncRepository.dryRun(read.value, localState.value.mode)
                    applyDryRunResult(result)
                }
            }
        }
    }

    fun onCsvFileChosen(source: Uri) {
        viewModelScope.launch {
            update { it.copy(busy = true, error = null, finishedReport = null) }
            when (val read = documentFileAccess.readText(source)) {
                is Outcome.Failure -> update { it.copy(busy = false, error = read.error) }
                is Outcome.Success -> applyDryRunResult(syncRepository.dryRunCsv(read.value))
            }
        }
    }

    fun onPreviewDiscard() = update { it.copy(preview = null) }

    fun onPreviewConfirm() {
        val preview = localState.value.preview ?: return
        viewModelScope.launch {
            update { it.copy(busy = true) }
            when (val applied = syncRepository.apply(preview)) {
                is Outcome.Failure -> update {
                    it.copy(busy = false, preview = null, error = applied.error)
                }

                is Outcome.Success -> update {
                    it.copy(busy = false, preview = null, finishedReport = applied.value)
                }
            }
        }
    }

    fun onErrorDismiss() = update { it.copy(error = null) }

    fun onReportDismiss() = update { it.copy(finishedReport = null) }

    fun onExportDoneShown() = update { it.copy(exportDone = false) }

    private fun applyDryRunResult(result: Outcome<ImportPreview>) {
        when (result) {
            is Outcome.Failure -> update { it.copy(busy = false, error = result.error) }
            is Outcome.Success -> update { it.copy(busy = false, preview = result.value) }
        }
    }

    private inline fun update(block: (TransferUiState) -> TransferUiState) {
        localState.value = block(localState.value)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val FILE_TIMESTAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
    }
}
