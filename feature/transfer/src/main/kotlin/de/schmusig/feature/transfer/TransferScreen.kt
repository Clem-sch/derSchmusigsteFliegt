@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.schmusig.feature.transfer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schmusig.core.designsystem.component.SectionCard
import de.schmusig.core.model.AppError
import de.schmusig.core.sync.DistractorNotice
import de.schmusig.core.sync.DistractorNoticeKind
import de.schmusig.core.sync.ImportMode
import de.schmusig.core.sync.ImportReport
import de.schmusig.core.sync.InvalidReason

@Composable
fun TransferRoute(
    onNavigateBack: () -> Unit,
    viewModel: TransferViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::onExport) }

    // Viele Dateimanager liefern JSON als application/octet-stream oder text/plain -
    // darum mehrere Typen zulassen und nicht am MIME-Type scheitern.
    val openJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::onImportFileChosen) }

    val openCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::onCsvFileChosen) }

    val exportDoneMessage = stringResource(R.string.transfer_export_done)
    LaunchedEffect(uiState.exportDone) {
        if (uiState.exportDone) {
            snackbarHostState.showSnackbar(exportDoneMessage)
            viewModel.onExportDoneShown()
        }
    }

    TransferScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onModeChange = viewModel::onModeChange,
        onExportCategoryToggle = viewModel::onExportCategoryToggle,
        onExportClick = { createDocument.launch(viewModel.suggestedExportName()) },
        onImportClick = { openJson.launch(JSON_MIME_TYPES) },
        onCsvClick = { openCsv.launch(CSV_MIME_TYPES) },
        onPreviewConfirm = viewModel::onPreviewConfirm,
        onPreviewDiscard = viewModel::onPreviewDiscard,
        onErrorDismiss = viewModel::onErrorDismiss,
        onReportDismiss = viewModel::onReportDismiss,
    )
}

private val JSON_MIME_TYPES = arrayOf(
    "application/json",
    "text/plain",
    "application/octet-stream",
)

private val CSV_MIME_TYPES = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "application/octet-stream",
)

@Composable
internal fun TransferScreen(
    uiState: TransferUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onModeChange: (ImportMode) -> Unit,
    onExportCategoryToggle: (String) -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onCsvClick: () -> Unit,
    onPreviewConfirm: () -> Unit,
    onPreviewDiscard: () -> Unit,
    onErrorDismiss: () -> Unit,
    onReportDismiss: () -> Unit,
) {
    var showRestoreWarning by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transfer_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.transfer_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ExportSection(
                uiState = uiState,
                onExportCategoryToggle = onExportCategoryToggle,
                onExportClick = onExportClick,
            )

            ImportSection(
                uiState = uiState,
                onModeChange = { mode ->
                    if (mode == ImportMode.RESTORE) {
                        showRestoreWarning = true
                    } else {
                        onModeChange(mode)
                    }
                },
                onImportClick = onImportClick,
            )

            SectionCard {
                Text(
                    text = stringResource(R.string.transfer_csv_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.transfer_csv_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCsvClick, enabled = !uiState.busy) {
                    Text(stringResource(R.string.transfer_csv_action))
                }
            }

            if (uiState.busy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    uiState.preview?.let { preview ->
        ReportDialog(
            title = stringResource(R.string.transfer_preview_title),
            report = preview.report,
            confirmLabel = stringResource(R.string.transfer_preview_confirm),
            confirmEnabled = preview.report.writesSomething,
            onConfirm = onPreviewConfirm,
            dismissLabel = stringResource(R.string.transfer_preview_discard),
            onDismiss = onPreviewDiscard,
        )
    }

    uiState.finishedReport?.let { report ->
        ReportDialog(
            title = stringResource(R.string.transfer_report_title),
            report = report,
            confirmLabel = stringResource(R.string.transfer_close),
            confirmEnabled = true,
            onConfirm = onReportDismiss,
            dismissLabel = null,
            onDismiss = onReportDismiss,
        )
    }

    uiState.error?.let { error ->
        AlertDialog(
            onDismissRequest = onErrorDismiss,
            title = { Text(stringResource(R.string.transfer_error_title)) },
            text = { Text(errorMessage(error)) },
            confirmButton = {
                TextButton(onClick = onErrorDismiss) {
                    Text(stringResource(R.string.transfer_close))
                }
            },
        )
    }

    if (showRestoreWarning) {
        AlertDialog(
            onDismissRequest = { showRestoreWarning = false },
            title = { Text(stringResource(R.string.transfer_restore_warning_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.transfer_restore_warning_text,
                        uiState.localQuestionCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreWarning = false
                        onModeChange(ImportMode.RESTORE)
                    },
                ) {
                    Text(stringResource(R.string.transfer_restore_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreWarning = false }) {
                    Text(stringResource(R.string.transfer_cancel))
                }
            },
        )
    }
}

@Composable
private fun ExportSection(
    uiState: TransferUiState,
    onExportCategoryToggle: (String) -> Unit,
    onExportClick: () -> Unit,
) {
    SectionCard {
        Text(
            text = stringResource(R.string.transfer_export_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.transfer_export_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.transfer_export_filter),
            style = MaterialTheme.typography.labelMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            uiState.categories.forEach { entry ->
                FilterChip(
                    selected = entry.category in uiState.exportCategories,
                    onClick = { onExportCategoryToggle(entry.category) },
                    label = { Text("${entry.category} ${entry.count}") },
                )
            }
        }
        Text(
            text = stringResource(R.string.transfer_export_filter_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onExportClick, enabled = !uiState.busy) {
            Text(stringResource(R.string.transfer_export_action))
        }
    }
}

@Composable
private fun ImportSection(
    uiState: TransferUiState,
    onModeChange: (ImportMode) -> Unit,
    onImportClick: () -> Unit,
) {
    SectionCard {
        Text(
            text = stringResource(R.string.transfer_import_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.transfer_import_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.transfer_mode_title),
            style = MaterialTheme.typography.labelMedium,
        )
        Column(Modifier.selectableGroup()) {
            ModeRow(
                selected = uiState.mode == ImportMode.MERGE,
                title = stringResource(R.string.transfer_mode_merge),
                description = stringResource(R.string.transfer_mode_merge_hint),
                onClick = { onModeChange(ImportMode.MERGE) },
            )
            ModeRow(
                selected = uiState.mode == ImportMode.RESTORE,
                title = stringResource(R.string.transfer_mode_restore),
                description = stringResource(R.string.transfer_mode_restore_hint),
                onClick = { onModeChange(ImportMode.RESTORE) },
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onImportClick, enabled = !uiState.busy) {
            Text(stringResource(R.string.transfer_import_action))
        }
    }
}

@Composable
private fun ModeRow(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Der Report als Dialog mit aufklappbaren Listen je Kategorie (6.6). Der Dry-Run und der
 * ausgefuehrte Import zeigen dieselbe Darstellung - es ist dieselbe Rechnung.
 */
@Composable
private fun ReportDialog(
    title: String,
    report: ImportReport,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    dismissLabel: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (!report.writesSomething && report.unchanged.isEmpty()) {
                    Text(stringResource(R.string.transfer_preview_nothing))
                }

                CountSection(
                    label = stringResource(R.string.transfer_count_inserted),
                    count = report.inserted.size,
                    lines = report.inserted.map { it.question },
                )
                CountSection(
                    label = stringResource(R.string.transfer_count_unchanged),
                    count = report.unchanged.size,
                    lines = report.unchanged.map { it.question },
                )
                CountSection(
                    label = stringResource(R.string.transfer_count_updated),
                    count = report.updated.size,
                    lines = report.updated.map { it.question },
                )
                CountSection(
                    label = stringResource(R.string.transfer_count_deleted),
                    count = report.deleted.size,
                    lines = report.deleted.map { it.question },
                )
                CountSection(
                    label = stringResource(R.string.transfer_count_skipped),
                    count = report.skippedOlder.size,
                    lines = report.skippedOlder.map { it.question },
                )
                CountSection(
                    label = stringResource(R.string.transfer_count_duplicates),
                    count = report.duplicates.size,
                    lines = report.duplicates.map { duplicate ->
                        stringResource(
                            R.string.transfer_duplicate_entry,
                            duplicate.incomingQuestion,
                            duplicate.existingQuestion,
                        )
                    },
                )
                CountSection(
                    label = stringResource(R.string.transfer_count_conflicts),
                    count = report.conflicts.size,
                    lines = report.conflicts.map { it.question },
                )
                CountSection(
                    label = stringResource(R.string.transfer_count_invalid),
                    count = report.invalid.size,
                    lines = report.invalid.map { entry ->
                        stringResource(
                            R.string.transfer_invalid_entry,
                            entry.index + 1,
                            stringResource(entry.reason.labelRes()),
                        )
                    },
                )
                if (report.statsApplied > 0) {
                    Text(
                        text = "${report.statsApplied} " +
                            stringResource(R.string.transfer_count_stats),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (report.distractorNotices.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    report.distractorNotices.forEach { notice ->
                        Text(
                            text = noticeText(notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
        },
        dismissButton = if (dismissLabel != null) {
            { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
        } else {
            null
        },
    )
}

@Composable
private fun CountSection(label: String, count: Int, lines: List<String>) {
    if (count == 0) return
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
        )
        AnimatedVisibility(visible = expanded) {
            Column {
                lines.take(MAX_REPORT_LINES).forEach { line ->
                    Text(
                        text = "· $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                    )
                }
                if (lines.size > MAX_REPORT_LINES) {
                    Text(
                        text = "… ${lines.size - MAX_REPORT_LINES}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun noticeText(notice: DistractorNotice): String = when (notice.kind) {
    DistractorNoticeKind.EQUAL_TO_ANSWER ->
        stringResource(R.string.transfer_notice_equal_to_answer, notice.count)

    DistractorNoticeKind.DUPLICATE ->
        stringResource(R.string.transfer_notice_duplicate, notice.count)

    DistractorNoticeKind.EMPTY ->
        stringResource(R.string.transfer_notice_empty, notice.count)

    DistractorNoticeKind.TRUNCATED ->
        stringResource(R.string.transfer_notice_truncated, notice.count)
}

private fun InvalidReason.labelRes(): Int = when (this) {
    InvalidReason.EMPTY_QUESTION -> R.string.transfer_invalid_empty_question
    InvalidReason.EMPTY_ANSWER -> R.string.transfer_invalid_empty_answer
    InvalidReason.EMPTY_CATEGORY -> R.string.transfer_invalid_empty_category
    InvalidReason.DIFFICULTY_OUT_OF_RANGE -> R.string.transfer_invalid_difficulty
    InvalidReason.BAD_TIMESTAMP -> R.string.transfer_invalid_timestamp
}

@Composable
private fun errorMessage(error: AppError): String = when (error) {
    is AppError.Format -> error.detail?.let {
        stringResource(R.string.transfer_error_format, it)
    } ?: stringResource(R.string.transfer_error_format_unknown)

    is AppError.UnsupportedSchema ->
        stringResource(R.string.transfer_error_schema, error.found, error.supported)

    is AppError.Io -> stringResource(R.string.transfer_error_io)
    else -> stringResource(R.string.transfer_error_storage)
}

private const val MAX_REPORT_LINES = 20
