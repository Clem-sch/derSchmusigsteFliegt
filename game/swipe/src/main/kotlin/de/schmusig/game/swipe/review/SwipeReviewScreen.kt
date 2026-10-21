@file:OptIn(ExperimentalMaterial3Api::class)

package de.schmusig.game.swipe.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schmusig.core.designsystem.component.DifficultyDots
import de.schmusig.core.designsystem.component.EmptyState
import de.schmusig.core.designsystem.component.MinTouchTarget
import de.schmusig.core.designsystem.theme.LocalVerdictColors
import de.schmusig.core.model.Verdict
import de.schmusig.game.swipe.R

@Composable
internal fun SwipeReviewRoute(
    onEditQuestion: (String) -> Unit,
    onFinished: () -> Unit,
    onNewRoundStarted: (String) -> Unit,
    viewModel: SwipeReviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SwipeReviewScreen(
        uiState = uiState,
        onVerdictToggle = viewModel::onVerdictToggle,
        onEditQuestion = onEditQuestion,
        onFinish = { viewModel.onFinish(onFinished) },
        onNewRound = { viewModel.onNewRound(onNewRoundStarted) },
    )
}

@Composable
internal fun SwipeReviewScreen(
    uiState: SwipeReviewUiState,
    onVerdictToggle: (String) -> Unit,
    onEditQuestion: (String) -> Unit,
    onFinish: () -> Unit,
    onNewRound: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.swipe_review_title)) }) },
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

        if (uiState.rows.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                EmptyState(
                    icon = Icons.Filled.Inbox,
                    title = stringResource(R.string.swipe_review_empty_title),
                    text = stringResource(R.string.swipe_review_empty_text),
                )
                Spacer(Modifier.weight(1f))
                ReviewActions(
                    finishing = uiState.finishing,
                    onFinish = onFinish,
                    onNewRound = onNewRound,
                )
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                text = stringResource(
                    R.string.swipe_review_summary,
                    uiState.correct,
                    uiState.wrong,
                    uiState.skipped,
                    uiState.rows.size,
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
            HorizontalDivider()

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.rows, key = { it.attempt.id }) { row ->
                    ReviewRowItem(
                        row = row,
                        onVerdictToggle = { onVerdictToggle(row.attempt.id) },
                        onEdit = { row.question?.let { onEditQuestion(it.id) } },
                    )
                    HorizontalDivider()
                }
            }

            ReviewActions(
                finishing = uiState.finishing,
                onFinish = onFinish,
                onNewRound = onNewRound,
            )
        }
    }
}

@Composable
private fun ReviewActions(
    finishing: Boolean,
    onFinish: () -> Unit,
    onNewRound: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onFinish,
            enabled = !finishing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.swipe_review_finish))
        }
        OutlinedButton(onClick = onNewRound, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.swipe_review_again))
        }
    }
}

@Composable
private fun ReviewRowItem(
    row: ReviewRow,
    onVerdictToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.question?.question
                    ?: stringResource(R.string.swipe_review_question_gone),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (row.question != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.swipe_review_answer, row.question.answer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = row.question.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    DifficultyDots(
                        difficulty = row.question.difficulty,
                        contentDescription = stringResource(
                            R.string.swipe_card_difficulty,
                            row.question.difficulty,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            VerdictChip(verdict = row.attempt.verdict, onClick = onVerdictToggle)
        }

        if (row.question != null) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(MinTouchTarget),
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.swipe_review_edit),
                )
            }
        }
    }
}

/** Die Wertung ist antippbar und umschaltbar: richtig -> falsch -> uebersprungen (8.4). */
@Composable
private fun VerdictChip(verdict: Verdict, onClick: () -> Unit) {
    val colors = LocalVerdictColors.current
    val (label, container, content) = when (verdict) {
        Verdict.CORRECT -> Triple(
            stringResource(R.string.swipe_review_verdict_correct),
            colors.correctContainer,
            colors.correct,
        )

        Verdict.WRONG -> Triple(
            stringResource(R.string.swipe_review_verdict_wrong),
            colors.wrongContainer,
            colors.wrong,
        )

        Verdict.SKIPPED -> Triple(
            stringResource(R.string.swipe_review_verdict_skipped),
            colors.skippedContainer,
            colors.skipped,
        )
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = container,
        modifier = Modifier
            .heightIn(min = MinTouchTarget)
            .clickable(
                onClick = onClick,
                onClickLabel = stringResource(R.string.swipe_review_verdict_toggle, label),
                role = Role.Button,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
        }
    }
}
