package de.schmusig.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Hebt die Suchbegriffe im Text hervor (7.2).
 *
 * Die Zerlegung passiert hier und nicht in der Datenschicht: welche Zeichen als
 * Worttrenner gelten, ist eine Darstellungsfrage, und die Hervorhebung muss auch dann
 * funktionieren, wenn der Treffer ueber einen Tag kam und im angezeigten Text gar nicht
 * vorkommt - dann wird eben nichts markiert.
 */
private val tokenSplitter = Regex("[^\\p{L}\\p{N}]+")

fun searchTokens(text: String): List<String> =
    text.trim()
        .split(tokenSplitter)
        .filter { it.isNotBlank() }
        .map { it.lowercase() }

@Composable
fun rememberHighlighted(
    text: String,
    query: String,
): AnnotatedString {
    val highlightStyle = SpanStyle(
        fontWeight = FontWeight.Bold,
        background = MaterialTheme.colorScheme.tertiaryContainer,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
    )
    return remember(text, query, highlightStyle) {
        highlight(text, searchTokens(query), highlightStyle)
    }
}

internal fun highlight(
    text: String,
    tokens: List<String>,
    style: SpanStyle,
): AnnotatedString {
    if (tokens.isEmpty()) return AnnotatedString(text)

    val lowercase = text.lowercase()
    val marked = BooleanArray(text.length)
    tokens.forEach { token ->
        var from = 0
        while (from <= lowercase.length - token.length) {
            val hit = lowercase.indexOf(token, from)
            if (hit < 0) break
            for (index in hit until hit + token.length) marked[index] = true
            from = hit + token.length
        }
    }

    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val isMarked = marked[index]
            var end = index
            while (end < text.length && marked[end] == isMarked) end++
            if (isMarked) {
                withStyle(style) { append(text, index, end) }
            } else {
                append(text, index, end)
            }
            index = end
        }
    }
}
