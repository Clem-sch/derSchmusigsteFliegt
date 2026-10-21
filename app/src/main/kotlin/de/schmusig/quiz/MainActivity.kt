package de.schmusig.quiz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.schmusig.core.designsystem.theme.SchmusigTheme
import de.schmusig.core.model.ThemeChoice
import de.schmusig.quiz.navigation.SchmusigNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val theme by appViewModel.theme.collectAsStateWithLifecycle()

            // Die Einstellung wird hier zu "dunkel: ja oder nein" aufgeloest, damit
            // :core:designsystem das Datenmodell nicht kennen muss.
            val darkTheme = when (theme) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
            }

            SchmusigTheme(darkTheme = darkTheme) {
                SchmusigNavHost()
            }
        }
    }
}
