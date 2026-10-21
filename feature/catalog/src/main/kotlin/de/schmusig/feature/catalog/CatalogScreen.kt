@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.schmusig.feature.catalog

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import de.schmusig.core.designsystem.component.DifficultyDots
import de.schmusig.core.designsystem.component.DistractorBadge
import de.schmusig.core.designsystem.component.EmptyState
import de.schmusig.core.designsystem.component.MinTouchTarget
import de.schmusig.core.designsystem.component.rememberHighlighted
import de.schmusig.core.model.CatalogSort
import de.schmusig.core.model.Question

@Composable
fun CatalogRoute(
    onNavigateBack: () -> Unit,
    onOpenQuestion: (String) -> Unit,
    onAddQuestion: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val questions = viewModel.questions.collectAsLazyPagingItems()
    val pendingDeletion by viewModel.pendingDeletion.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val undoLabel = stringResource(R.string.catalog_undo)
    // Die Meldung wird hier aufgeloest: in einem LaunchedEffect gibt es keinen
    // Composable-Kontext mehr, und sie soll den Fragetext tragen.
    val deletedMessage = pendingDeletion?.let {
        stringResource(R.string.catalog_deleted_snackbar, it.question)
    }
    LaunchedEffect(pendingDeletion) {
        val deleted = pendingDeletion ?: return@LaunchedEffect
        viewModel.onDeletionShown()
        val result = snackbarHostState.showSnackbar(
            message = deletedMessage.orEmpty(),
            actionLabel = undoLabel,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.onUndoDelete(deleted)
        }
    }

    CatalogScreen(
        uiState = uiState,
        questions = questions,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onOpenQuestion = onOpenQuestion,
        onAddQuestion = onAddQuestion,
        onSearchTextChange = viewModel::onSearchTextChange,
        onFiltersExpandedChange = viewModel::onFiltersExpandedChange,
        onCategoryToggle = viewModel::onCategoryToggle,
        onDifficultyToggle = viewModel::onDifficultyToggle,
        onFlaggedToggle = viewModel::onFlaggedToggle,
        onWithDistractorsToggle = viewModel::onWithDistractorsToggle,
        onSortChange = viewModel::onSortChange,
        onFiltersReset = viewModel::onFiltersReset,
    )
}

@Composable
internal fun CatalogScreen(
    uiState: CatalogUiState,
    questions: LazyPagingItems<Question>,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onOpenQuestion: (String) -> Unit,
    onAddQuestion: () -> Unit,
    onSearchTextChange: (String) -> Unit,
    onFiltersExpandedChange: (Boolean) -> Unit,
    onCategoryToggle: (String) -> Unit,
    onDifficultyToggle: (Int) -> Unit,
    onFlaggedToggle: () -> Unit,
    onWithDistractorsToggle: () -> Unit,
    onSortChange: (CatalogSort) -> Unit,
    onFiltersReset: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.catalog_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.catalog_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onFiltersExpandedChange(!uiState.filtersExpanded) }) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = stringResource(
                                if (uiState.filtersExpanded) {
                                    R.string.catalog_filter_hide
                                } else {
                                    R.string.catalog_filter_show
                                },
                            ),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddQuestion) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.catalog_add),
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            OutlinedTextField(
                value = uiState.searchText,
                onValueChange = onSearchTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.catalog_search_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchText.isNotEmpty()) {
                        IconButton(onClick = { onSearchTextChange("") }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(
                                    R.string.catalog_search_clear,
                                ),
                            )
                        }
                    }
                },
                singleLine = true,
            )

            AnimatedVisibility(visible = uiState.filtersExpanded) {
                FilterPanel(
                    uiState = uiState,
                    onCategoryToggle = onCategoryToggle,
                    onDifficultyToggle = onDifficultyToggle,
                    onFlaggedToggle = onFlaggedToggle,
                    onWithDistractorsToggle = onWithDistractorsToggle,
                    onSortChange = onSortChange,
                    onFiltersReset = onFiltersReset,
                )
            }

            Text(
                text = stringResource(R.string.catalog_match_count, uiState.matchCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            val isLoading = questions.loadState.refresh is LoadState.Loading
            when {
                isLoading && questions.itemCount == 0 -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }

                questions.itemCount == 0 && uiState.isCatalogEmpty -> EmptyState(
                    icon = Icons.Filled.Add,
                    title = stringResource(R.string.catalog_empty_title),
                    text = stringResource(R.string.catalog_empty_text),
                )

                questions.itemCount == 0 -> EmptyState(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.catalog_no_results_title),
                    text = stringResource(R.string.catalog_no_results_text),
                    action = {
                        TextButton(onClick = onFiltersReset) {
                            Text(stringResource(R.string.catalog_filter_reset))
                        }
                    },
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        count = questions.itemCount,
                        key = { index -> questions.peek(index)?.id ?: index },
                    ) { index ->
                        val question = questions[index]
                        if (question != null) {
                            QuestionRow(
                                question = question,
                                searchText = uiState.searchText,
                                onClick = { onOpenQuestion(question.id) },
                            )
                            HorizontalDivider()
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FilterPanel(
    uiState: CatalogUiState,
    onCategoryToggle: (String) -> Unit,
    onDifficultyToggle: (Int) -> Unit,
    onFlaggedToggle: () -> Unit,
    onWithDistractorsToggle: () -> Unit,
    onSortChange: (CatalogSort) -> Unit,
    onFiltersReset: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterLabel(stringResource(R.string.catalog_filter_categories))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            uiState.categories.forEach { entry ->
                FilterChip(
                    selected = entry.category in uiState.query.categories,
                    onClick = { onCategoryToggle(entry.category) },
                    label = {
                        Text(
                            stringResource(
                                R.string.catalog_category_chip,
                                entry.category,
                                entry.count,
                            ),
                        )
                    },
                )
            }
        }

        FilterLabel(stringResource(R.string.catalog_filter_difficulties))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Question.DIFFICULTIES.forEach { difficulty ->
                FilterChip(
                    selected = difficulty in uiState.query.difficulties,
                    onClick = { onDifficultyToggle(difficulty) },
                    label = { Text(difficulty.toString()) },
                )
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.query.onlyFlagged,
                onClick = onFlaggedToggle,
                label = { Text(stringResource(R.string.catalog_filter_flagged)) },
            )
            FilterChip(
                selected = uiState.query.onlyWithDistractors,
                onClick = onWithDistractorsToggle,
                label = { Text(stringResource(R.string.catalog_filter_with_distractors)) },
            )
        }

        FilterLabel(stringResource(R.string.catalog_sort_label))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CatalogSort.entries.forEach { sort ->
                FilterChip(
                    selected = uiState.query.sort == sort,
                    onClick = { onSortChange(sort) },
                    label = { Text(stringResource(sort.labelRes())) },
                )
            }
        }

        if (uiState.query.hasActiveFilter) {
            TextButton(onClick = onFiltersReset) {
                Text(stringResource(R.string.catalog_filter_reset))
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun FilterLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun CatalogSort.labelRes(): Int = when (this) {
    CatalogSort.RECENTLY_UPDATED -> R.string.catalog_sort_recent
    CatalogSort.CATEGORY -> R.string.catalog_sort_category
    CatalogSort.DIFFICULTY -> R.string.catalog_sort_difficulty
}

@Composable
private fun QuestionRow(
    question: Question,
    searchText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rememberHighlighted(question.question, searchText),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = question.category,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                DifficultyDots(
                    difficulty = question.difficulty,
                    contentDescription = stringResource(
                        R.string.catalog_difficulty_description,
                        question.difficulty,
                    ),
                )
                if (question.flagged) {
                    Icon(
                        imageVector = Icons.Filled.Flag,
                        contentDescription = stringResource(
                            R.string.catalog_flagged_description,
                        ),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                if (question.distractors.isNotEmpty()) {
                    DistractorBadge(
                        count = question.distractors.size,
                        contentDescription = stringResource(
                            R.string.catalog_distractors_description,
                            question.distractors.size,
                        ),
                    )
                }
            }
        }
    }
}
