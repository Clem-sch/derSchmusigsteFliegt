@file:OptIn(ExperimentalMaterial3Api::class)

package de.schmusig.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schmusig.core.designsystem.component.SectionCard
import de.schmusig.core.designsystem.format.formatDurationLabel
import de.schmusig.core.model.AnswerVisibility
import de.schmusig.core.model.ThemeChoice
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SettingsRoute(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var backupToExport by remember { mutableStateOf<String?>(null) }
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val name = backupToExport
        backupToExport = null
        if (uri != null && name != null) viewModel.onExportBackup(name, uri)
    }

    // Die Meldungen entstehen in einem LaunchedEffect, also ausserhalb der Composition;
    // die Ressourcen werden darum hier geholt und dort nur noch formatiert.
    val resources = LocalContext.current.resources
    val seedTemplate = stringResource(R.string.settings_seed_result)
    val seedFailed = stringResource(R.string.settings_seed_failed)
    val backupExported = stringResource(R.string.settings_backup_exported)
    val backupFailed = stringResource(R.string.settings_backup_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is SettingsEvent.TombstonesPurged -> resources.getQuantityString(
                    R.plurals.settings_purge_done,
                    event.count,
                    event.count,
                )
                is SettingsEvent.SeedImported ->
                    seedTemplate.format(event.new, event.unchanged, event.updated)

                SettingsEvent.SeedFailed -> seedFailed
                SettingsEvent.BackupExported -> backupExported
                SettingsEvent.BackupExportFailed -> backupFailed
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    SettingsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onThemeChange = viewModel::onThemeChange,
        onAnswerVisibilityChange = viewModel::onAnswerVisibilityChange,
        onDefaultTimerChange = viewModel::onDefaultTimerChange,
        onPurgeTombstones = viewModel::onPurgeTombstones,
        onSeedAgain = viewModel::onSeedAgain,
        onExportBackup = { name ->
            backupToExport = name
            createDocument.launch(name)
        },
    )
}

@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onThemeChange: (ThemeChoice) -> Unit,
    onAnswerVisibilityChange: (AnswerVisibility) -> Unit,
    onDefaultTimerChange: (Int) -> Unit,
    onPurgeTombstones: () -> Unit,
    onSeedAgain: () -> Unit,
    onExportBackup: (String) -> Unit,
) {
    var showPurgeDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
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
            SectionLabel(stringResource(R.string.settings_section_game))
            SectionCard {
                Text(
                    text = stringResource(R.string.settings_answer_visibility),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                Column(Modifier.selectableGroup()) {
                    RadioRow(
                        selected = uiState.settings.answerVisibility ==
                            AnswerVisibility.IMMEDIATE,
                        title = stringResource(R.string.settings_answer_immediate),
                        description = stringResource(R.string.settings_answer_immediate_hint),
                        onClick = { onAnswerVisibilityChange(AnswerVisibility.IMMEDIATE) },
                    )
                    RadioRow(
                        selected = uiState.settings.answerVisibility == AnswerVisibility.ON_TAP,
                        title = stringResource(R.string.settings_answer_on_tap),
                        description = stringResource(R.string.settings_answer_on_tap_hint),
                        onClick = { onAnswerVisibilityChange(AnswerVisibility.ON_TAP) },
                    )
                }
            }

            SectionCard {
                Text(
                    text = stringResource(R.string.settings_default_timer),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            onDefaultTimerChange(uiState.settings.defaultTimerSeconds - 60)
                        },
                    ) {
                        Icon(
                            Icons.Filled.Remove,
                            contentDescription = stringResource(R.string.settings_timer_less),
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.settings_default_timer_value,
                            formatDurationLabel(uiState.settings.defaultTimerSeconds),
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    IconButton(
                        onClick = {
                            onDefaultTimerChange(uiState.settings.defaultTimerSeconds + 60)
                        },
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.settings_timer_more),
                        )
                    }
                }
            }

            SectionLabel(stringResource(R.string.settings_section_appearance))
            SectionCard {
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChoice.entries.forEach { choice ->
                        FilterChip(
                            selected = uiState.settings.theme == choice,
                            onClick = { onThemeChange(choice) },
                            label = { Text(stringResource(choice.labelRes())) },
                        )
                    }
                }
            }

            SectionLabel(stringResource(R.string.settings_section_data))
            SectionCard {
                Text(
                    text = stringResource(R.string.settings_purge_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (uiState.tombstoneCount == 0) {
                        stringResource(R.string.settings_purge_none)
                    } else {
                        pluralStringResource(
                            R.plurals.settings_purge_count,
                            uiState.tombstoneCount,
                            uiState.tombstoneCount,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { showPurgeDialog = true },
                    enabled = uiState.tombstoneCount > 0 && !uiState.busy,
                ) {
                    Text(stringResource(R.string.settings_purge_action))
                }
            }

            SectionCard {
                Text(
                    text = stringResource(R.string.settings_backups_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_backups_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (uiState.backups.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_backups_none),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    uiState.backups.forEach { backup ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.settings_backup_entry,
                                    dateFormatter.format(
                                        backup.createdAt.atZone(ZoneId.systemDefault()),
                                    ),
                                    formatBytes(backup.sizeBytes),
                                ),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = { onExportBackup(backup.name) }) {
                                Text(stringResource(R.string.settings_backup_export))
                            }
                        }
                    }
                }
            }

            SectionLabel(stringResource(R.string.settings_section_debug))
            SectionCard {
                Text(
                    text = stringResource(R.string.settings_seed_again),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_seed_again_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onSeedAgain, enabled = !uiState.busy) {
                    Text(stringResource(R.string.settings_seed_action))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPurgeDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeDialog = false },
            title = { Text(stringResource(R.string.settings_purge_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_purge_dialog_text,
                        SettingsViewModel.TOMBSTONE_RETENTION_DAYS,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPurgeDialog = false
                        onPurgeTombstones()
                    },
                ) {
                    Text(stringResource(R.string.settings_purge_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeDialog = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun RadioRow(
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

private fun ThemeChoice.labelRes(): Int = when (this) {
    ThemeChoice.SYSTEM -> R.string.settings_theme_system
    ThemeChoice.LIGHT -> R.string.settings_theme_light
    ThemeChoice.DARK -> R.string.settings_theme_dark
}

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} kB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
