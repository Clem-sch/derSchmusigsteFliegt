@file:OptIn(ExperimentalMaterial3Api::class)

package de.schmusig.quiz.menu

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schmusig.core.designsystem.component.MinTouchTarget
import de.schmusig.quiz.R

@Composable
fun MainMenuRoute(
    onPlayMode: (String) -> Unit,
    onOpenCatalog: () -> Unit,
    onAddQuestion: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenSettings: () -> Unit,
    onResumeSession: (String) -> Unit,
    viewModel: MainMenuViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MainMenuScreen(
        uiState = uiState,
        onPlayMode = onPlayMode,
        onOpenCatalog = onOpenCatalog,
        onAddQuestion = onAddQuestion,
        onOpenTransfer = onOpenTransfer,
        onOpenSettings = onOpenSettings,
        onResumeSession = onResumeSession,
        onDiscardResumable = viewModel::onDiscardResumable,
    )
}

@Composable
internal fun MainMenuScreen(
    uiState: MainMenuUiState,
    onPlayMode: (String) -> Unit,
    onOpenCatalog: () -> Unit,
    onAddQuestion: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenSettings: () -> Unit,
    onResumeSession: (String) -> Unit,
    onDiscardResumable: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.menu_settings),
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.menu_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = if (uiState.inventory.questionCount == 0) {
                    stringResource(R.string.menu_inventory_empty)
                } else {
                    stringResource(
                        R.string.menu_inventory,
                        uiState.inventory.questionCount,
                        uiState.inventory.categoryCount,
                    )
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            uiState.resumableSession?.let { session ->
                ResumeCard(
                    onResume = { onResumeSession(session.id) },
                    onDiscard = { onDiscardResumable(session.id) },
                )
            }

            Spacer(Modifier.height(4.dp))

            // Ein Eintrag pro Spielmodus, mit dem Anzeigenamen des Modus - kein
            // generisches "Spielen" (7.1).
            uiState.gameModes.forEach { mode ->
                MenuCard(
                    icon = Icons.Filled.PlayArrow,
                    title = mode.displayName,
                    subtitle = stringResource(R.string.menu_play_hint),
                    highlighted = true,
                    onClick = { onPlayMode(mode.id) },
                )
            }

            MenuCard(
                icon = Icons.AutoMirrored.Filled.List,
                title = stringResource(R.string.menu_catalog),
                subtitle = stringResource(R.string.menu_catalog_hint),
                onClick = onOpenCatalog,
            )

            MenuCard(
                icon = Icons.Filled.Add,
                title = stringResource(R.string.menu_add_question),
                subtitle = stringResource(R.string.menu_add_question_hint),
                onClick = onAddQuestion,
            )

            MenuCard(
                icon = Icons.Filled.SwapVert,
                title = stringResource(R.string.menu_transfer),
                subtitle = stringResource(R.string.menu_transfer_hint),
                onClick = onOpenTransfer,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ResumeCard(onResume: () -> Unit, onDiscard: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.menu_resume_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onResume) {
                    Text(stringResource(R.string.menu_resume_confirm))
                }
                TextButton(onClick = onDiscard) {
                    Text(stringResource(R.string.menu_resume_discard))
                }
            }
        }
    }
}

@Composable
private fun MenuCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget * 1.5f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (highlighted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.size(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (highlighted) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (highlighted) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
