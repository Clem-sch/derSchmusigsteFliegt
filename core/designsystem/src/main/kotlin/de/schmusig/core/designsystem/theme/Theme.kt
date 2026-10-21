package de.schmusig.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Plum40,
    onPrimary = Color.White,
    primaryContainer = Plum90,
    onPrimaryContainer = Plum10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Teal20,
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Amber30,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral95,
    onSurfaceVariant = Neutral20,
    error = Error40,
    onError = Color.White,
    errorContainer = Error90,
    onErrorContainer = Error10,
)

private val DarkColors = darkColorScheme(
    primary = Plum80,
    onPrimary = Plum20,
    primaryContainer = Plum30,
    onPrimaryContainer = Plum90,
    secondary = Teal80,
    onSecondary = Teal20,
    secondaryContainer = Teal40,
    onSecondaryContainer = Teal90,
    tertiary = Amber80,
    onTertiary = Amber30,
    tertiaryContainer = Amber40,
    onTertiaryContainer = Amber90,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = Neutral20,
    onSurfaceVariant = Neutral90,
    error = Error80,
    onError = Error10,
    errorContainer = Error40,
    onErrorContainer = Error90,
)

/**
 * Semantische Wertungsfarben, aufgeloest fuer das aktive Theme. "Richtig" und "falsch"
 * haben eine eigene Bedeutung, die in Hell und Dunkel stabil bleiben muss - darum liegen
 * sie neben dem Material-Schema statt in ihm.
 */
@Immutable
data class VerdictColorScheme(
    val correct: Color,
    val correctContainer: Color,
    val wrong: Color,
    val wrongContainer: Color,
    val skipped: Color,
    val skippedContainer: Color,
)

private val LightVerdictColors = VerdictColorScheme(
    correct = VerdictColors.correctLight,
    correctContainer = VerdictColors.correctContainerLight,
    wrong = VerdictColors.wrongLight,
    wrongContainer = VerdictColors.wrongContainerLight,
    skipped = VerdictColors.skippedLight,
    skippedContainer = VerdictColors.skippedContainerLight,
)

private val DarkVerdictColors = VerdictColorScheme(
    correct = VerdictColors.correctDark,
    correctContainer = VerdictColors.correctContainerDark,
    wrong = VerdictColors.wrongDark,
    wrongContainer = VerdictColors.wrongContainerDark,
    skipped = VerdictColors.skippedDark,
    skippedContainer = VerdictColors.skippedContainerDark,
)

val LocalVerdictColors: ProvidableCompositionLocal<VerdictColorScheme> =
    staticCompositionLocalOf { LightVerdictColors }

/**
 * Das Theme kennt bewusst nur "dunkel: ja oder nein" und nicht die Einstellung selbst -
 * so braucht `:core:designsystem` keine Abhaengigkeit auf das Datenmodell.
 */
@Composable
fun SchmusigTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalVerdictColors provides if (darkTheme) DarkVerdictColors else LightVerdictColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = SchmusigTypography,
            content = content,
        )
    }
}
