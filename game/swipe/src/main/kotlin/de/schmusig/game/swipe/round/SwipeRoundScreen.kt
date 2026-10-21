@file:OptIn(ExperimentalMaterial3Api::class)

package de.schmusig.game.swipe.round

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schmusig.core.designsystem.component.MinTouchTarget
import de.schmusig.core.designsystem.format.formatClock
import de.schmusig.core.designsystem.theme.LocalVerdictColors
import de.schmusig.core.designsystem.theme.TimerTextStyle
import de.schmusig.core.model.Verdict
import de.schmusig.game.swipe.R

@Composable
internal fun SwipeRoundRoute(
    onNavigateToReview: (String) -> Unit,
    onNavigateHome: () -> Unit,
    viewModel: SwipeRoundViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    // Eine Runde, deren Zeit schon um war, geht direkt in die Auswertung.
    LaunchedEffect(uiState.alreadyInReview) {
        if (uiState.alreadyInReview) onNavigateToReview(viewModel.sessionId)
    }

    LaunchedEffect(uiState.timeUp) {
        if (uiState.timeUp) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    SwipeRoundScreen(
        uiState = uiState,
        onReveal = viewModel::onReveal,
        onVerdict = viewModel::onVerdict,
        onAbort = { viewModel.onAbort(onNavigateHome) },
        onToReview = { onNavigateToReview(viewModel.sessionId) },
    )
}

@Composable
internal fun SwipeRoundScreen(
    uiState: SwipeRoundUiState,
    onReveal: () -> Unit,
    onVerdict: (Verdict) -> Unit,
    onAbort: () -> Unit,
    onToReview: () -> Unit,
) {
    var showAbortDialog by remember { mutableStateOf(false) }
    val verdictColors = LocalVerdictColors.current

    BackHandler(enabled = !uiState.roundOver) { showAbortDialog = true }

    Scaffold { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                TimerBar(uiState = uiState)

                if (uiState.loading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    return@Column
                }

                val question = uiState.current
                if (question == null) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(
                                    R.string.swipe_cards_out_of_questions_title,
                                ),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.swipe_cards_out_of_questions_text,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = onToReview) {
                                Text(stringResource(R.string.swipe_cards_to_review))
                            }
                        }
                    }
                    return@Column
                }

                SwipeCardStack(
                    question = question,
                    answerVisible = uiState.answerVisible,
                    onReveal = onReveal,
                    onVerdict = onVerdict,
                    modifier = Modifier.weight(1f),
                )

                // Zwei Buttons zusaetzlich zum Swipe, damit die App auch mit einer Hand
                // und ohne Wischen benutzbar ist (8.2).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(
                        onClick = { onVerdict(Verdict.WRONG) },
                        modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = verdictColors.wrong)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.swipe_cards_wrong))
                    }
                    FilledTonalButton(
                        onClick = { onVerdict(Verdict.SKIPPED) },
                        modifier = Modifier.heightIn(min = MinTouchTarget),
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.swipe_cards_skip),
                        )
                    }
                    FilledTonalButton(
                        onClick = { onVerdict(Verdict.CORRECT) },
                        modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = verdictColors.correct,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.swipe_cards_correct))
                    }
                }
            }

            // "Zeit um" als Vollbild-Overlay, nicht als Dialog, der sich wegklicken
            // laesst (8.3).
            if (uiState.timeUp) {
                TimeUpOverlay(
                    answeredCount = uiState.answeredCount,
                    onToReview = onToReview,
                )
            }
        }
    }

    if (showAbortDialog) {
        AlertDialog(
            onDismissRequest = { showAbortDialog = false },
            title = { Text(stringResource(R.string.swipe_abort_title)) },
            text = { Text(stringResource(R.string.swipe_abort_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAbortDialog = false
                        onAbort()
                    },
                ) {
                    Text(stringResource(R.string.swipe_abort_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAbortDialog = false }) {
                    Text(stringResource(R.string.swipe_abort_dismiss))
                }
            },
        )
    }
}

@Composable
private fun TimerBar(uiState: SwipeRoundUiState) {
    val color = if (uiState.inFinalStretch) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatClock(uiState.remainingSeconds),
                style = TimerTextStyle,
                color = color,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.swipe_cards_answered, uiState.answeredCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { uiState.progress },
            modifier = Modifier.fillMaxWidth(),
            color = color,
        )
    }
}

@Composable
private fun TimeUpOverlay(answeredCount: Int, onToReview: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.96f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = stringResource(R.string.swipe_time_up),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.swipe_time_up_hint,
                    answeredCount,
                    answeredCount,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onToReview) {
                Text(stringResource(R.string.swipe_cards_to_review))
            }
        }
    }
}
