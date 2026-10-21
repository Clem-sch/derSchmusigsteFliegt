package de.schmusig.game.swipe.round

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.schmusig.core.designsystem.component.DifficultyDots
import de.schmusig.core.designsystem.theme.LocalVerdictColors
import de.schmusig.core.designsystem.theme.QuestionCardTextStyle
import de.schmusig.core.model.Question
import de.schmusig.core.model.Verdict
import de.schmusig.game.swipe.R
import kotlinx.coroutines.launch

/**
 * Der Karten-Stack, handgebaut mit `pointerInput` und `Animatable` (Abschnitt 3).
 *
 * Rechts = richtig, links = falsch, hoch = ueberspringen. Unter einer Mindestdistanz
 * federt die Karte zurueck; darueber fliegt sie in Wischrichtung aus dem Bild und erst
 * danach meldet sie die Wertung - so sieht man, was man gewertet hat.
 */
@Composable
internal fun SwipeCardStack(
    question: Question,
    answerVisible: Boolean,
    onReveal: () -> Unit,
    onVerdict: (Verdict) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()

    // Neue Frage: die Karte beginnt wieder in der Mitte.
    LaunchedEffect(question.id) { offset.snapTo(Offset.Zero) }

    val dragThreshold = screenWidthPx * HORIZONTAL_THRESHOLD_FRACTION
    val verticalThreshold = with(density) { SKIP_THRESHOLD.toPx() }

    val pendingVerdict = when {
        offset.value.x > dragThreshold -> Verdict.CORRECT
        offset.value.x < -dragThreshold -> Verdict.WRONG
        offset.value.y < -verticalThreshold -> Verdict.SKIPPED
        else -> null
    }

    fun flyOut(verdict: Verdict) {
        scope.launch {
            val target = when (verdict) {
                Verdict.CORRECT -> Offset(screenWidthPx * 1.5f, offset.value.y)
                Verdict.WRONG -> Offset(-screenWidthPx * 1.5f, offset.value.y)
                Verdict.SKIPPED -> Offset(offset.value.x, -screenWidthPx * 1.5f)
            }
            offset.animateTo(target, tween(FLY_OUT_MS))
            onVerdict(verdict)
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Angedeutete zweite Karte, damit der Stack als Stapel lesbar ist.
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(CARD_HEIGHT)
                .graphicsLayer {
                    scaleX = 0.94f
                    scaleY = 0.94f
                    translationY = with(density) { 12.dp.toPx() }
                },
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {}

        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(CARD_HEIGHT)
                .graphicsLayer {
                    translationX = offset.value.x
                    translationY = offset.value.y
                    rotationZ = (offset.value.x / ROTATION_DIVISOR).coerceIn(-12f, 12f)
                }
                .pointerInput(question.id) {
                    detectDragGestures(
                        onDragEnd = {
                            val verdict = when {
                                offset.value.x > dragThreshold -> Verdict.CORRECT
                                offset.value.x < -dragThreshold -> Verdict.WRONG
                                offset.value.y < -verticalThreshold -> Verdict.SKIPPED
                                else -> null
                            }
                            if (verdict != null) {
                                flyOut(verdict)
                            } else {
                                scope.launch { offset.animateTo(Offset.Zero, spring()) }
                            }
                        },
                        onDragCancel = {
                            scope.launch { offset.animateTo(Offset.Zero, spring()) }
                        },
                    ) { _, dragAmount ->
                        scope.launch {
                            offset.snapTo(offset.value + dragAmount)
                        }
                    }
                },
            shape = RoundedCornerShape(28.dp),
        ) {
            CardContent(
                question = question,
                answerVisible = answerVisible,
                onReveal = onReveal,
                pendingVerdict = pendingVerdict,
            )
        }
    }
}

@Composable
private fun CardContent(
    question: Question,
    answerVisible: Boolean,
    onReveal: () -> Unit,
    pendingVerdict: Verdict?,
) {
    val verdictColors = LocalVerdictColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (answerVisible) Modifier else Modifier.clickable(onClick = onReveal),
            )
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = question.category,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            DifficultyDots(
                difficulty = question.difficulty,
                contentDescription = stringResource(
                    R.string.swipe_card_difficulty,
                    question.difficulty,
                ),
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = question.question,
            style = QuestionCardTextStyle,
            modifier = Modifier.semantics {
                contentDescription =
                    "${question.question}. ${if (answerVisible) question.answer else ""}"
            },
        )

        Spacer(Modifier.weight(1f))

        if (answerVisible) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = question.answer,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            Text(
                text = stringResource(R.string.swipe_cards_reveal),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Richtungsindikator waehrend des Ziehens.
        AnimatedVisibility(visible = pendingVerdict != null) {
            val (label, color) = when (pendingVerdict) {
                Verdict.CORRECT -> stringResource(R.string.swipe_cards_correct) to
                    verdictColors.correct

                Verdict.WRONG -> stringResource(R.string.swipe_cards_wrong) to
                    verdictColors.wrong

                else -> stringResource(R.string.swipe_cards_skip) to verdictColors.skipped
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                )
            }
        }
    }
}

/** Wie weit die Karte mindestens gezogen werden muss, bevor sie wertet. */
private const val HORIZONTAL_THRESHOLD_FRACTION = 0.28f
private val SKIP_THRESHOLD = 110.dp
private val CARD_HEIGHT = 420.dp
private const val ROTATION_DIVISOR = 28f
private const val FLY_OUT_MS = 180
