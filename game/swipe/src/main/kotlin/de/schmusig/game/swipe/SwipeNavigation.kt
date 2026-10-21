package de.schmusig.game.swipe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import de.schmusig.game.swipe.review.SwipeReviewRoute
import de.schmusig.game.swipe.review.SwipeReviewViewModel
import de.schmusig.game.swipe.round.SwipeRoundRoute
import de.schmusig.game.swipe.round.SwipeRoundViewModel
import de.schmusig.game.swipe.setup.SwipeSelectionScreen
import de.schmusig.game.swipe.setup.SwipeSetupViewModel
import de.schmusig.game.swipe.setup.SwipeTimerScreen

/**
 * Die Routen dieses Spielmodus. Sie liegen im Modul, damit `:app` nur den Einstieg kennen
 * muss - ein weiterer Modus bringt seinen eigenen Graphen mit.
 */
object SwipeRoutes {
    const val GRAPH = "swipe"
    const val SETUP_GRAPH = "swipe/setup"
    const val SELECTION = "swipe/setup/selection"
    const val TIMER = "swipe/setup/timer"
    const val ROUND = "swipe/round"
    const val REVIEW = "swipe/review"

    fun round(sessionId: String) = "$ROUND/$sessionId"

    fun review(sessionId: String) = "$REVIEW/$sessionId"
}

/**
 * Haengt Setup, Kartenphase und Auswertung in den Navigationsgraphen der App.
 *
 * Setup ist ein eigener Untergraph: Schritt 1 und Schritt 2 teilen sich dadurch dasselbe
 * [SwipeSetupViewModel], und das Vorladen laeuft weiter, waehrend der Nutzer das Drehrad
 * dreht (8.1).
 */
fun NavGraphBuilder.swipeGameGraph(
    navController: NavHostController,
    onLeaveGame: () -> Unit,
    onEditQuestion: (String) -> Unit,
) {
    navigation(startDestination = SwipeRoutes.SELECTION, route = SwipeRoutes.SETUP_GRAPH) {
        composable(SwipeRoutes.SELECTION) { entry ->
            val viewModel = entry.setupViewModel(navController)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            SwipeSelectionScreen(
                uiState = uiState,
                onNavigateBack = onLeaveGame,
                onCategoryToggle = viewModel::onCategoryToggle,
                onAllCategories = viewModel::onAllCategories,
                onNoCategories = viewModel::onNoCategories,
                onDifficultyToggle = viewModel::onDifficultyToggle,
                onExcludeRecentlyPlayedChange = viewModel::onExcludeRecentlyPlayedChange,
                onContinue = {
                    viewModel.onContinue()
                    navController.navigate(SwipeRoutes.TIMER)
                },
            )
        }

        composable(SwipeRoutes.TIMER) { entry ->
            val viewModel = entry.setupViewModel(navController)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            SwipeTimerScreen(
                uiState = uiState,
                // Zurueck fuehrt zum vorherigen Schritt, nicht aus dem Modus heraus (8.1).
                onNavigateBack = {
                    viewModel.onBackToSelection()
                    navController.popBackStack()
                },
                onTimerSecondsChange = viewModel::onTimerSecondsChange,
                onStart = {
                    viewModel.onStart { sessionId ->
                        navController.navigate(SwipeRoutes.round(sessionId)) {
                            // Das Setup ist erledigt; von der Karte aus zurueck soll
                            // nicht wieder ins Drehrad fuehren.
                            popUpTo(SwipeRoutes.SETUP_GRAPH) { inclusive = true }
                        }
                    }
                },
            )
        }
    }

    composable(
        route = "${SwipeRoutes.ROUND}/{${SwipeRoundViewModel.ARG_SESSION_ID}}",
        arguments = listOf(
            navArgument(SwipeRoundViewModel.ARG_SESSION_ID) { type = NavType.StringType },
        ),
    ) {
        SwipeRoundRoute(
            onNavigateToReview = { sessionId ->
                navController.navigate(SwipeRoutes.review(sessionId)) {
                    popUpTo("${SwipeRoutes.ROUND}/{${SwipeRoundViewModel.ARG_SESSION_ID}}") {
                        inclusive = true
                    }
                }
            },
            onNavigateHome = onLeaveGame,
        )
    }

    composable(
        route = "${SwipeRoutes.REVIEW}/{${SwipeReviewViewModel.ARG_SESSION_ID}}",
        arguments = listOf(
            navArgument(SwipeReviewViewModel.ARG_SESSION_ID) { type = NavType.StringType },
        ),
    ) {
        SwipeReviewRoute(
            onEditQuestion = onEditQuestion,
            onFinished = onLeaveGame,
            onNewRoundStarted = { sessionId ->
                navController.navigate(SwipeRoutes.round(sessionId)) {
                    popUpTo("${SwipeRoutes.REVIEW}/{${SwipeReviewViewModel.ARG_SESSION_ID}}") {
                        inclusive = true
                    }
                }
            },
        )
    }
}

/**
 * Das Setup-ViewModel haengt am Untergraphen, nicht am einzelnen Screen - sonst wuerde
 * die Navigation zu Schritt 2 das Vorladen abbrechen.
 */
@androidx.compose.runtime.Composable
private fun androidx.navigation.NavBackStackEntry.setupViewModel(
    navController: NavHostController,
): SwipeSetupViewModel {
    val parentEntry = remember(this) {
        navController.getBackStackEntry(SwipeRoutes.SETUP_GRAPH)
    }
    return hiltViewModel(parentEntry)
}
