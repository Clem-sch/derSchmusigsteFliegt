package de.schmusig.quiz.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.schmusig.feature.catalog.CatalogRoute
import de.schmusig.feature.editor.EditorRoute
import de.schmusig.feature.editor.EditorViewModel
import de.schmusig.feature.settings.SettingsRoute
import de.schmusig.feature.transfer.TransferRoute
import de.schmusig.game.swipe.SwipeGameMode
import de.schmusig.game.swipe.SwipeRoutes
import de.schmusig.game.swipe.swipeGameGraph
import de.schmusig.quiz.menu.MainMenuRoute

/**
 * Die Routen der App. Nur `:app` kennt alle Module (Abschnitt 4), darum liegt der
 * Navigationsgraph hier - die Feature-Module liefern Composables und Callbacks, nicht
 * Navigationslogik.
 */
private object Routes {
    const val MENU = "menu"
    const val CATALOG = "catalog"
    const val EDITOR = "editor"
    const val SETTINGS = "settings"
    const val TRANSFER = "transfer"

    fun editor(questionId: String) = "$EDITOR/$questionId"
}

@Composable
fun SchmusigNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.MENU) {
        composable(Routes.MENU) {
            MainMenuRoute(
                onPlayMode = { modeId ->
                    // Jeder Modus bringt seinen eigenen Graphen mit; hier wird nur der
                    // Einstieg zugeordnet.
                    when (modeId) {
                        SwipeGameMode.MODE_ID -> navController.navigate(SwipeRoutes.SETUP_GRAPH)
                        else -> Unit
                    }
                },
                onOpenCatalog = { navController.navigate(Routes.CATALOG) },
                onAddQuestion = {
                    navController.navigate(Routes.editor(EditorViewModel.NEW_QUESTION))
                },
                onOpenTransfer = { navController.navigate(Routes.TRANSFER) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onResumeSession = { sessionId ->
                    navController.navigate(SwipeRoutes.round(sessionId))
                },
            )
        }

        composable(Routes.CATALOG) {
            CatalogRoute(
                onNavigateBack = { navController.popBackStack() },
                onOpenQuestion = { questionId ->
                    navController.navigate(Routes.editor(questionId))
                },
                onAddQuestion = {
                    navController.navigate(Routes.editor(EditorViewModel.NEW_QUESTION))
                },
            )
        }

        composable(
            route = "${Routes.EDITOR}/{${EditorViewModel.ARG_QUESTION_ID}}",
            arguments = listOf(
                navArgument(EditorViewModel.ARG_QUESTION_ID) { type = NavType.StringType },
            ),
        ) {
            EditorRoute(onNavigateBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsRoute(onNavigateBack = { navController.popBackStack() })
        }

        composable(Routes.TRANSFER) {
            TransferRoute(onNavigateBack = { navController.popBackStack() })
        }

        swipeGameGraph(
            navController = navController,
            onLeaveGame = {
                navController.navigate(Routes.MENU) {
                    popUpTo(Routes.MENU) { inclusive = true }
                }
            },
            // Der Editor aus der Review-Liste ist derselbe wie im Katalog - einmal
            // implementiert, von beiden Stellen aufgerufen (7.3).
            onEditQuestion = { questionId ->
                navController.navigate(Routes.editor(questionId))
            },
        )
    }
}
