@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.schmusig.game.swipe.setup

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.schmusig.core.designsystem.component.EmptyState
import de.schmusig.core.designsystem.component.SectionCard
import de.schmusig.core.designsystem.component.SettingSwitchRow
import de.schmusig.core.designsystem.format.formatClock
import de.schmusig.core.model.Question
import de.schmusig.game.swipe.R

/**
 * Schritt 1 des Setups: Auswahl (8.1). Der Live-Zaehler unten reagiert auf jeden Tap und
 * sperrt "Weiter" bei 0 Treffern - mit dem Grund im Text statt eines stummen Buttons.
 */
@Composable
internal fun SwipeSelectionScreen(
    uiState: SwipeSetupUiState,
    onNavigateBack: () -> Unit,
    onCategoryToggle: (String) -> Unit,
    onAllCategories: () -> Unit,
    onNoCategories: () -> Unit,
    onDifficultyToggle: (Int) -> Unit,
    onExcludeRecentlyPlayedChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.swipe_setup_title))
                        Text(
                            text = stringResource(R.string.swipe_setup_step, 1, 2),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.swipe_setup_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.catalogEmpty) {
            EmptyState(
                modifier = Modifier.padding(innerPadding),
                icon = Icons.Filled.Inbox,
                title = stringResource(R.string.swipe_setup_empty_catalog_title),
                text = stringResource(R.string.swipe_setup_empty_catalog_text),
            )
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.swipe_setup_categories),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onAllCategories) {
                            Text(stringResource(R.string.swipe_setup_all))
                        }
                        TextButton(onClick = onNoCategories) {
                            Text(stringResource(R.string.swipe_setup_none))
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.categories.forEach { entry ->
                            FilterChip(
                                selected = entry.category in uiState.selectedCategories,
                                onClick = { onCategoryToggle(entry.category) },
                                label = { Text("${entry.category} ${entry.count}") },
                            )
                        }
                    }
                }

                SectionCard {
                    Text(
                        text = stringResource(R.string.swipe_setup_difficulties),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Fuenf einzelne Schalter, kein Bereichs-Slider: "1, 2 und 5, aber
                    // nicht 3 und 4" muss ausdrueckbar sein (8.1).
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Question.DIFFICULTIES.forEach { difficulty ->
                            FilterChip(
                                selected = difficulty in uiState.selectedDifficulties,
                                onClick = { onDifficultyToggle(difficulty) },
                                label = {
                                    Text(
                                        stringResource(
                                            R.string.swipe_setup_difficulty_chip,
                                            difficulty,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }

                SectionCard {
                    SettingSwitchRow(
                        title = stringResource(R.string.swipe_setup_exclude_recent),
                        description = stringResource(R.string.swipe_setup_exclude_recent_hint),
                        checked = uiState.excludeRecentlyPlayed,
                        onCheckedChange = onExcludeRecentlyPlayedChange,
                    )
                }

                Spacer(Modifier.height(8.dp))
            }

            MatchCountBar(uiState = uiState, onContinue = onContinue)
        }
    }
}

@Composable
private fun MatchCountBar(uiState: SwipeSetupUiState, onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = when (uiState.emptyReason) {
                EmptyReason.NO_CATEGORY -> stringResource(R.string.swipe_setup_no_category)
                EmptyReason.NO_DIFFICULTY -> stringResource(R.string.swipe_setup_no_difficulty)
                EmptyReason.NO_MATCH -> stringResource(R.string.swipe_setup_no_match)
                null -> pluralStringResource(
                    R.plurals.swipe_setup_match_count,
                    uiState.matchCount,
                    uiState.matchCount,
                )
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (uiState.canContinue) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Button(
            onClick = onContinue,
            enabled = uiState.canContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.swipe_setup_next))
        }
    }
}

/** Schritt 2 des Setups: Timer-Drehrad (8.1). */
@Composable
internal fun SwipeTimerScreen(
    uiState: SwipeSetupUiState,
    onNavigateBack: () -> Unit,
    onTimerSecondsChange: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val minutes = uiState.timerSeconds / 60
    val seconds = uiState.timerSeconds % 60

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.swipe_timer_title))
                        Text(
                            text = stringResource(R.string.swipe_setup_step, 2, 2),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.swipe_setup_back),
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
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = formatClock(uiState.timerSeconds),
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(8.dp))

            TimeWheelRow(
                minutes = minutes,
                seconds = seconds,
                minuteValues = MINUTE_VALUES,
                secondValues = SECOND_VALUES,
                onMinutesChange = { onTimerSecondsChange(it * 60 + seconds) },
                onSecondsChange = { onTimerSecondsChange(minutes * 60 + it) },
                minutesLabel = stringResource(R.string.swipe_timer_minutes),
                secondsLabel = stringResource(R.string.swipe_timer_seconds),
                minutesValueDescription = stringResource(R.string.swipe_timer_minutes_value, minutes),
                secondsValueDescription = stringResource(R.string.swipe_timer_seconds_value, seconds),
                minutesIncrement = stringResource(
                    R.string.swipe_timer_more,
                    stringResource(R.string.swipe_timer_minutes),
                ),
                minutesDecrement = stringResource(
                    R.string.swipe_timer_less,
                    stringResource(R.string.swipe_timer_minutes),
                ),
                secondsIncrement = stringResource(
                    R.string.swipe_timer_more,
                    stringResource(R.string.swipe_timer_seconds),
                ),
                secondsDecrement = stringResource(
                    R.string.swipe_timer_less,
                    stringResource(R.string.swipe_timer_seconds),
                ),
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.swipe_timer_presets),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESET_MINUTES.forEach { preset ->
                    AssistChip(
                        onClick = { onTimerSecondsChange(preset * 60) },
                        label = { Text(stringResource(R.string.swipe_timer_preset, preset)) },
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (!uiState.canStart && uiState.timerSeconds == 0) {
                Text(
                    text = stringResource(R.string.swipe_timer_zero_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = onStart,
                enabled = uiState.canStart && !uiState.starting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.starting) {
                    // Wird "Start" gedrueckt, bevor das Vorladen fertig ist, zeigt der
                    // Button einen Ladezustand statt den Bildschirm zu blockieren (8.1).
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.swipe_timer_start))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val MINUTE_VALUES = (0..60).toList()
private val SECOND_VALUES = (0..55 step 5).toList()
private val PRESET_MINUTES = listOf(3, 5, 10)
