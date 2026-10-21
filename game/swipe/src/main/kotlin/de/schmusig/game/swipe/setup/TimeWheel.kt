package de.schmusig.game.swipe.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.schmusig.core.designsystem.component.MinTouchTarget
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged

private val ItemHeight = 44.dp
private const val VISIBLE_ITEMS = 5
private const val PADDING_ITEMS = (VISIBLE_ITEMS - 1) / 2

/**
 * Ein Drehrad wie beim Weckerstellen (8.1, Schritt 2).
 *
 * Umsetzung mit `LazyColumn` plus `rememberSnapFlingBehavior`: die aktive Zeile steht
 * mittig und ist hervorgehoben, Nachbarzeilen sind verkleinert und abgeblendet, und jede
 * Rastung gibt einen leichten Haptik-Tick.
 *
 * Fuer TalkBack ist ein Drehrad schlecht bedienbar. Darum traegt die Spalte eine
 * zusammengefasste Semantik mit dem aktuellen Wert, und darueber/darunter liegt ein
 * Plus/Minus-Paar mit eigenen Content-Descriptions - das ist der barrierefreie Weg zum
 * selben Wert.
 */
@Composable
internal fun NumberWheel(
    values: List<Int>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    label: String,
    valueDescription: String,
    incrementDescription: String,
    decrementDescription: String,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val itemHeightPx = with(density) { ItemHeight.toPx() }

    /**
     * Die fortlaufende Position der Mitte, inklusive Bruchteil - daraus folgen
     * Verkleinerung und Abblendung der Nachbarzeilen waehrend des Ziehens.
     */
    val centerPosition by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex +
                listState.firstVisibleItemScrollOffset / itemHeightPx
        }
    }

    // Programmatische Aenderung (Presets, Plus/Minus) animiert das Rad auf den Wert.
    LaunchedEffect(selectedIndex) {
        if (listState.isScrollInProgress) return@LaunchedEffect
        if (listState.firstVisibleItemIndex != selectedIndex) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    // Rastung melden und den Haptik-Tick geben.
    LaunchedEffect(listState, values.size) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (listState.isScrollInProgress) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                if (index != selectedIndex && index in values.indices) {
                    onSelectedIndexChange(index)
                }
            }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        IconButton(
            onClick = {
                if (selectedIndex + 1 in values.indices) onSelectedIndexChange(selectedIndex + 1)
            },
            modifier = Modifier.height(MinTouchTarget),
        ) {
            Icon(Icons.Filled.Add, contentDescription = incrementDescription)
        }

        Box(
            modifier = Modifier
                .width(96.dp)
                .height(ItemHeight * VISIBLE_ITEMS),
            contentAlignment = Alignment.Center,
        ) {
            // Der Rahmen markiert die aktive Zeile, auch wenn gerade gescrollt wird.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ItemHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            )

            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = PaddingValues(vertical = ItemHeight * PADDING_ITEMS),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {
                        contentDescription = "$label: $valueDescription"
                    },
            ) {
                itemsIndexed(values) { index, value ->
                    val distance = abs(index - centerPosition)
                    Text(
                        text = "%02d".format(value),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .height(ItemHeight)
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .scale((1f - 0.14f * distance).coerceAtLeast(0.7f))
                            .alpha((1f - 0.3f * distance).coerceIn(0.25f, 1f)),
                    )
                }
            }
        }

        IconButton(
            onClick = {
                if (selectedIndex - 1 in values.indices) onSelectedIndexChange(selectedIndex - 1)
            },
            modifier = Modifier.height(MinTouchTarget),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = decrementDescription)
        }
    }
}

/** Minuten und Sekunden nebeneinander, mit dem Doppelpunkt dazwischen. */
@Composable
internal fun TimeWheelRow(
    minutes: Int,
    seconds: Int,
    minuteValues: List<Int>,
    secondValues: List<Int>,
    onMinutesChange: (Int) -> Unit,
    onSecondsChange: (Int) -> Unit,
    minutesLabel: String,
    secondsLabel: String,
    minutesValueDescription: String,
    secondsValueDescription: String,
    minutesIncrement: String,
    minutesDecrement: String,
    secondsIncrement: String,
    secondsDecrement: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberWheel(
            values = minuteValues,
            selectedIndex = minuteValues.indexOf(minutes).coerceAtLeast(0),
            onSelectedIndexChange = { index -> onMinutesChange(minuteValues[index]) },
            label = minutesLabel,
            valueDescription = minutesValueDescription,
            incrementDescription = minutesIncrement,
            decrementDescription = minutesDecrement,
        )
        Text(
            text = ":",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        NumberWheel(
            values = secondValues,
            selectedIndex = secondValues.indexOf(seconds).coerceAtLeast(0),
            onSelectedIndexChange = { index -> onSecondsChange(secondValues[index]) },
            label = secondsLabel,
            valueDescription = secondsValueDescription,
            incrementDescription = secondsIncrement,
            decrementDescription = secondsDecrement,
        )
    }
}
