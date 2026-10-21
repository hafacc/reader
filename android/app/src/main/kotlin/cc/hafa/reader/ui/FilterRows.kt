package cc.hafa.reader.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.hafa.reader.R

/** One chip of a filter row: what it shows, how many articles it covers when counted, and whether it is off. */
data class FilterChipItem(
    /** Stable key telling the chip apart. */
    val key: String,
    /** The words it shows. */
    val label: String,
    /** Articles it covers; not shown when null. */
    val count: Int?,
    /** Whether it is turned off. */
    val off: Boolean,
    /** Turns it off or back on. */
    val onToggle: () -> Unit,
)

// How far a row's ends fade where more chips lie past them.
private val FADE = 28.dp

/** Widen what this wraps by [edge] on each side, so it runs to the edges of a box padded that much. */
private fun Modifier.bleed(edge: Dp): Modifier =
    layout { measurable, constraints ->
        val extra = edge.roundToPx() * 2
        val placeable = measurable.measure(
            constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
        )
        layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
    }

/** Fade the ends of a sideways-scrolling row where more lies past them. */
private fun Modifier.edgeFades(scroll: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = FADE.toPx()
            if (scroll.value > 0) {
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fade),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (scroll.value < scroll.maxValue) {
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

@Composable
private fun FilterRow(label: String, chips: List<FilterChipItem>, section: Boolean, edge: Dp) {
    val scroll = rememberScrollState()
    Row(
        Modifier
            .bleed(edge)
            .fillMaxWidth()
            .semantics {
                contentDescription = label
                isTraversalGroup = true
            }
            .edgeFades(scroll)
            .horizontalScroll(scroll)
            .padding(horizontal = edge),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (chip in chips) {
            key(chip.key) {
                TopicChip(chip.label, off = chip.off, onToggle = chip.onToggle, count = chip.count, section = section)
            }
        }
    }
}

/**
 * The filter chips of an article or a view: a row of [sections], then one row per group of [topics], each a single
 * line that scrolls sideways and runs [edge] past the sides into its box's padding.
 */
@Composable
fun FilterRows(sections: List<FilterChipItem>, topics: List<List<FilterChipItem>>, edge: Dp = 20.dp) {
    Column {
        if (sections.isNotEmpty()) {
            FilterRow(stringResource(R.string.sections), sections, section = true, edge = edge)
        }
        val topicsLabel = stringResource(R.string.topics)
        for (row in topics) {
            FilterRow(topicsLabel, row, section = false, edge = edge)
        }
    }
}
