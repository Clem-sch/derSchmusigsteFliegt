@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.schmusig.feature.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schmusig.core.designsystem.component.MinTouchTarget
import de.schmusig.core.designsystem.component.SettingSwitchRow
import de.schmusig.core.model.AppError
import de.schmusig.core.model.Question
import de.schmusig.core.sync.DistractorRowError

private typealias ValidationField = AppError.Validation.Field

/**
 * Der Editor (7.3). Ein Screen fuer Anlegen und Bearbeiten, aufrufbar aus dem Katalog,
 * aus dem Hauptmenue und aus der Review-Liste - einmal implementiert, nicht dupliziert.
 */
@Composable
fun EditorRoute(
    onNavigateBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMessage = stringResource(R.string.editor_save_failed)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                EditorEvent.Saved, EditorEvent.Deleted -> onNavigateBack()
                EditorEvent.SaveFailed -> snackbarHostState.showSnackbar(saveFailedMessage)
            }
        }
    }

    EditorScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onQuestionChange = viewModel::onQuestionChange,
        onAnswerChange = viewModel::onAnswerChange,
        onCategoryChange = viewModel::onCategoryChange,
        onDifficultyChange = viewModel::onDifficultyChange,
        onFlaggedChange = viewModel::onFlaggedChange,
        onTagInputChange = viewModel::onTagInputChange,
        onTagCommit = viewModel::onTagCommit,
        onTagRemove = viewModel::onTagRemove,
        onDistractorsExpandedChange = viewModel::onDistractorsExpandedChange,
        onDistractorAdd = viewModel::onDistractorAdd,
        onDistractorChange = viewModel::onDistractorChange,
        onDistractorRemove = viewModel::onDistractorRemove,
        onDuplicateDismiss = viewModel::onDuplicateWarningDismiss,
        onSaveAnyway = viewModel::onSaveAnyway,
        onSave = viewModel::onSave,
        onDelete = viewModel::onDelete,
    )
}

@Composable
internal fun EditorScreen(
    uiState: EditorUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onQuestionChange: (String) -> Unit,
    onAnswerChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onDifficultyChange: (Int) -> Unit,
    onFlaggedChange: (Boolean) -> Unit,
    onTagInputChange: (String) -> Unit,
    onTagCommit: () -> Unit,
    onTagRemove: (String) -> Unit,
    onDistractorsExpandedChange: (Boolean) -> Unit,
    onDistractorAdd: () -> Unit,
    onDistractorChange: (Int, String) -> Unit,
    onDistractorRemove: (Int) -> Unit,
    onDuplicateDismiss: () -> Unit,
    onSaveAnyway: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (uiState.isNew) {
                                R.string.editor_title_new
                            } else {
                                R.string.editor_title_edit
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                R.string.editor_back,
                            ),
                        )
                    }
                },
                actions = {
                    if (!uiState.isNew) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(
                                    R.string.editor_delete_action,
                                ),
                            )
                        }
                    }
                    IconButton(onClick = onSave, enabled = !uiState.saving) {
                        if (uiState.saving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = stringResource(R.string.editor_save),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (uiState.questionMissing) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.editor_not_found))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.question,
                onValueChange = onQuestionChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.editor_question_label)) },
                minLines = 2,
                isError = uiState.invalidField ==
                    ValidationField.QUESTION,
                supportingText = {
                    if (uiState.invalidField ==
                        ValidationField.QUESTION
                    ) {
                        Text(stringResource(R.string.editor_question_error))
                    }
                },
            )

            OutlinedTextField(
                value = uiState.answer,
                onValueChange = onAnswerChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.editor_answer_label)) },
                minLines = 2,
                isError = uiState.invalidField ==
                    ValidationField.ANSWER,
                supportingText = {
                    if (uiState.invalidField ==
                        ValidationField.ANSWER
                    ) {
                        Text(stringResource(R.string.editor_answer_error))
                    }
                },
            )

            CategoryField(
                value = uiState.category,
                suggestions = uiState.categorySuggestions,
                isError = uiState.invalidField ==
                    ValidationField.CATEGORY,
                onValueChange = onCategoryChange,
            )

            DifficultyField(
                difficulty = uiState.difficulty,
                onDifficultyChange = onDifficultyChange,
            )

            TagsField(
                tags = uiState.tags,
                input = uiState.tagInput,
                onInputChange = onTagInputChange,
                onCommit = onTagCommit,
                onRemove = onTagRemove,
            )

            SettingSwitchRow(
                title = stringResource(R.string.editor_flagged_title),
                description = stringResource(R.string.editor_flagged_description),
                checked = uiState.flagged,
                onCheckedChange = onFlaggedChange,
            )

            HorizontalDivider()

            DistractorSection(
                distractors = uiState.distractors,
                errors = uiState.distractorErrors,
                expanded = uiState.distractorsExpanded,
                canAdd = uiState.canAddDistractor,
                count = uiState.distractorCount,
                onExpandedChange = onDistractorsExpandedChange,
                onAdd = onDistractorAdd,
                onChange = onDistractorChange,
                onRemove = onDistractorRemove,
            )

            Spacer(Modifier.height(32.dp))
        }
    }

    uiState.duplicateWarning?.let { warning ->
        AlertDialog(
            onDismissRequest = onDuplicateDismiss,
            title = { Text(stringResource(R.string.editor_duplicate_title)) },
            text = {
                Text(
                    stringResource(R.string.editor_duplicate_text, warning.existingQuestion),
                )
            },
            confirmButton = {
                TextButton(onClick = onSaveAnyway) {
                    Text(stringResource(R.string.editor_duplicate_save_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = onDuplicateDismiss) {
                    Text(stringResource(R.string.editor_cancel))
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.editor_delete_title)) },
            text = { Text(stringResource(R.string.editor_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                ) {
                    Text(stringResource(R.string.editor_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.editor_cancel))
                }
            },
        )
    }
}

@Composable
private fun CategoryField(
    value: String,
    suggestions: List<String>,
    isError: Boolean,
    onValueChange: (String) -> Unit,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.editor_category_label)) },
            singleLine = true,
            isError = isError,
            supportingText = {
                if (isError) Text(stringResource(R.string.editor_category_error))
            },
        )
        // Autovervollstaendigung als Chips statt als Dropdown: der Bestand ist klein,
        // und Chips lassen sich mit einer Hand treffen.
        val matching = remember(value, suggestions) {
            if (value.isBlank()) {
                suggestions.take(MAX_SUGGESTIONS)
            } else {
                suggestions
                    .filter { it.contains(value, ignoreCase = true) && it != value }
                    .take(MAX_SUGGESTIONS)
            }
        }
        if (matching.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                matching.forEach { suggestion ->
                    AssistChip(
                        onClick = { onValueChange(suggestion) },
                        label = { Text(suggestion) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DifficultyField(
    difficulty: Int,
    onDifficultyChange: (Int) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.editor_difficulty_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            Question.DIFFICULTIES.forEachIndexed { index, value ->
                SegmentedButton(
                    selected = difficulty == value,
                    onClick = { onDifficultyChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = Question.DIFFICULTIES.size,
                    ),
                ) {
                    Text(value.toString())
                }
            }
        }
    }
}

@Composable
private fun TagsField(
    tags: List<String>,
    input: String,
    onInputChange: (String) -> Unit,
    onCommit: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Column {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.editor_tags_label)) },
            placeholder = { Text(stringResource(R.string.editor_tags_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit() }),
            trailingIcon = {
                IconButton(onClick = onCommit, enabled = input.isNotBlank()) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.editor_tag_add),
                    )
                }
            },
        )
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(
                                    R.string.editor_tag_remove,
                                    tag,
                                ),
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * Der eigene, eingeklappte Abschnitt fuer Falschantworten (7.3). Er traegt den Hinweis,
 * dass der aktuelle Spielmodus sie nicht benutzt - sonst wirkt das Feld wie ein Fehler.
 */
@Composable
private fun DistractorSection(
    distractors: List<String>,
    errors: List<DistractorRowError?>,
    expanded: Boolean,
    canAdd: Boolean,
    count: Int,
    onExpandedChange: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onChange: (Int, String) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MinTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (count > 0) {
                    stringResource(R.string.editor_distractors_section_count, count)
                } else {
                    stringResource(R.string.editor_distractors_section)
                },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onExpandedChange(!expanded) }) {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.ExpandLess
                    } else {
                        Icons.Filled.ExpandMore
                    },
                    contentDescription = stringResource(
                        if (expanded) {
                            R.string.editor_distractors_collapse
                        } else {
                            R.string.editor_distractors_expand
                        },
                    ),
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.editor_distractors_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                distractors.forEachIndexed { index, value ->
                    val error = errors.getOrNull(index)
                    OutlinedTextField(
                        value = value,
                        onValueChange = { onChange(index, it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(stringResource(R.string.editor_distractor_label, index + 1))
                        },
                        singleLine = true,
                        isError = error != null,
                        supportingText = {
                            when (error) {
                                DistractorRowError.EQUALS_ANSWER -> Text(
                                    stringResource(R.string.editor_distractor_equals_answer),
                                )

                                DistractorRowError.DUPLICATE -> Text(
                                    stringResource(R.string.editor_distractor_duplicate),
                                )

                                null -> Unit
                            }
                        },
                        trailingIcon = {
                            IconButton(onClick = { onRemove(index) }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(
                                        R.string.editor_distractor_remove,
                                        index + 1,
                                    ),
                                )
                            }
                        },
                    )
                }

                FilledTonalButton(onClick = onAdd, enabled = canAdd) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.editor_distractor_add))
                }
                if (!canAdd) {
                    Text(
                        text = stringResource(
                            R.string.editor_distractor_max,
                            Question.MAX_DISTRACTORS,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val MAX_SUGGESTIONS = 6
