package de.schmusig.core.designsystem.theme

import androidx.compose.ui.graphics.Color

internal val Plum10 = Color(0xFF1B1020)
internal val Plum20 = Color(0xFF2E1C38)
internal val Plum30 = Color(0xFF452A52)
internal val Plum40 = Color(0xFF5E3B6E)
internal val Plum80 = Color(0xFFD9B9EB)
internal val Plum90 = Color(0xFFEDDCF6)

internal val Teal20 = Color(0xFF07373A)
internal val Teal40 = Color(0xFF156E73)
internal val Teal80 = Color(0xFF8CD4D9)
internal val Teal90 = Color(0xFFC9ECEF)

internal val Amber30 = Color(0xFF5B3D00)
internal val Amber40 = Color(0xFF7C5400)
internal val Amber80 = Color(0xFFF2C14E)
internal val Amber90 = Color(0xFFFFE6AE)

internal val Neutral10 = Color(0xFF14121A)
internal val Neutral20 = Color(0xFF231F2B)
internal val Neutral90 = Color(0xFFE8E2EC)
internal val Neutral95 = Color(0xFFF5F0F7)
internal val Neutral99 = Color(0xFFFFFBFF)

internal val Error40 = Color(0xFFB3261E)
internal val Error80 = Color(0xFFF2B8B5)
internal val Error90 = Color(0xFFF9DEDC)
internal val Error10 = Color(0xFF410E0B)

/**
 * Wertungsfarben fuer Spiel und Review. Sie liegen bewusst neben dem Material-Schema,
 * weil "richtig" und "falsch" eine eigene, in Hell und Dunkel stabile Bedeutung haben.
 */
object VerdictColors {
    val correctLight = Color(0xFF1B6B3A)
    val correctContainerLight = Color(0xFFBFEBCF)
    val correctDark = Color(0xFF8ED6A8)
    val correctContainerDark = Color(0xFF12482A)

    val wrongLight = Color(0xFFA62A28)
    val wrongContainerLight = Color(0xFFFBD6D4)
    val wrongDark = Color(0xFFF0A3A0)
    val wrongContainerDark = Color(0xFF5E1715)

    val skippedLight = Color(0xFF5A5A66)
    val skippedContainerLight = Color(0xFFE3E1E8)
    val skippedDark = Color(0xFFBFBDC7)
    val skippedContainerDark = Color(0xFF3A3842)
}
