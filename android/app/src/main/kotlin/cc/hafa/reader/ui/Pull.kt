package cc.hafa.reader.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

/** How far an article must be pulled past its top or end to close it, in dp, as on the web (96 CSS pixels); the list's end refreshes at the same distance. */
const val ARTICLE_PULL_DISTANCE = 96f

/** How far a pulled article can move however far the finger goes, in dp, as on the web. */
const val ARTICLE_PULL_STRETCH = 160f

/**
 * How far a pulled article moves when the finger has gone [travel] dp past its
 * edge: at first with the finger, then less and less, never quite reaching
 * [ARTICLE_PULL_STRETCH]. Keeps the sign of [travel]; the web's `pullStretch`.
 */
fun pullStretch(travel: Float): Float = sign(travel) * ARTICLE_PULL_STRETCH * (1f - exp(-abs(travel) / ARTICLE_PULL_STRETCH))

/** How far along a pull of [travel] dp is toward closing the article, from 0 to 1. */
fun pullProgress(travel: Float): Float = minOf(1f, abs(travel) / ARTICLE_PULL_DISTANCE)

// Material's pull to refresh indicator: its container and the spinner in it.
private val INDICATOR_SIZE = 40.dp
private val SPINNER_SIZE = 16.dp
private val SPINNER_STROKE = 2.5.dp
private val ARROW_SIZE = 18.dp

/**
 * A drag along [orientation] that what it wraps leaves unused, past its first or last page or its top or end: the
 * content follows it, moved by [stretch] of the drag's length, and on release one of at least [threshold] pixels
 * calls [onRelease] with which way it went, as a unit step, leaving the content where it is for the screen change to
 * carry off when [staysOnRelease], else springing back as a shorter one does. With [fromEdgeOnly], only a drag that
 * is unused from its very start counts, so a scroll that merely reaches the edge does not; with [pastEndOnly], only
 * one past the end or last page.
 */
class EdgeDrag(
    val orientation: Orientation,
    private val threshold: Float,
    private val fromEdgeOnly: Boolean,
    private val pastEndOnly: Boolean = false,
    private val staysOnRelease: Boolean = true,
    private val stretch: (Float) -> Float = { travel -> travel },
    private val onRelease: (Offset) -> Unit,
) : NestedScrollConnection {
    /** How far the finger has gone past the edge, in pixels. */
    var travel by mutableFloatStateOf(0f)
        private set

    /** How far the content is moved past the edge, in pixels. */
    val offset: Float
        get() = stretch(travel)

    /** How far the content is moved when letting go starts to act, in pixels. */
    val thresholdOffset: Float
        get() = stretch(threshold)

    /** Whether letting go now acts. */
    val armed: Boolean
        get() = abs(travel) >= threshold

    /** Whether a drag may start; one under way goes on unless [cancel]ed. */
    var enabled: Boolean = true

    /** Whether a finger is moving the content, rather than it springing back. */
    val dragging: Boolean
        get() = counts == true

    // Whether the drag under way may move the content; null until its first movement.
    private var counts: Boolean? = null

    private fun along(offset: Offset): Float = if (orientation == Orientation.Horizontal) offset.x else offset.y

    private fun offsetOf(amount: Float): Offset =
        if (orientation == Orientation.Horizontal) Offset(amount, 0f) else Offset(0f, amount)

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val amount = along(available)
        // Dragging back toward the content first undoes the distance past the edge.
        return if (source == NestedScrollSource.UserInput && counts == true && travel != 0f && sign(amount) != sign(travel)) {
            val taken = if (abs(amount) >= abs(travel)) -travel else amount
            travel += taken
            offsetOf(taken)
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val amount = along(available)
        if (source == NestedScrollSource.UserInput && counts == null) {
            counts = enabled && (!fromEdgeOnly || (along(consumed) == 0f && amount != 0f)) && !(pastEndOnly && amount > 0f)
        }
        val allowed = !pastEndOnly || travel + amount <= 0f
        return if (source == NestedScrollSource.UserInput && counts == true && amount != 0f && allowed) {
            travel += amount
            offsetOf(amount)
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val counted = counts == true
        counts = null
        return if (travel == 0f || !counted) {
            Velocity.Zero
        } else {
            if (armed) {
                onRelease(offsetOf(sign(travel)))
            }
            if (!armed || !staysOnRelease) {
                animate(travel, 0f, animationSpec = spring()) { value, _ -> travel = value }
            }
            available
        }
    }

    /** Stop the drag under way, if any: the content springs back and the rest of the drag scrolls it as usual. */
    suspend fun cancel() {
        if (counts == true) {
            counts = false
        }
        if (travel != 0f) {
            animate(travel, 0f, animationSpec = spring()) { value, _ -> travel = value }
        }
    }
}

/**
 * A drag along [orientation] past the content's edge, as [EdgeDrag] describes, at the article's distance and
 * following the finger by [pullStretch], or not at all with animations off, where the indicator alone shows the pull;
 * it gives a haptic tick each time the finger crosses the point where letting go acts.
 */
@Composable
fun rememberEdgeDrag(
    orientation: Orientation,
    fromEdgeOnly: Boolean,
    pastEndOnly: Boolean = false,
    staysOnRelease: Boolean = true,
    onRelease: (Offset) -> Unit,
): EdgeDrag {
    val release by rememberUpdatedState(onRelease)
    val density = LocalDensity.current
    val animate = animationsOn()
    val drag = remember(orientation, fromEdgeOnly, pastEndOnly, staysOnRelease, density, animate) {
        EdgeDrag(
            orientation,
            with(density) { ARTICLE_PULL_DISTANCE.dp.toPx() },
            fromEdgeOnly,
            pastEndOnly,
            staysOnRelease,
            stretch = { travel -> if (animate) density.density * pullStretch(travel / density.density) else 0f },
        ) { towards -> release(towards) }
    }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(drag, haptics) {
        snapshotFlow { drag.armed }.drop(1).collect {
            if (drag.dragging) {
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            }
        }
    }
    return drag
}

/**
 * Material's pull to refresh indicator, for a pull past the content's edge: in the gap [pull] opens, inside
 * [padding], with an arrow pointing the way of the pull that grows in as the pull goes on and turns over, in the
 * accent color, once letting go acts, when screen readers hear [armedLabel]. While [spinning], it turns past the
 * end, where letting go began to act, until that ends, as Material's does while refreshing.
 */
@Composable
fun PullIndicator(pull: EdgeDrag, padding: PaddingValues, armedLabel: String, spinning: Boolean = false) {
    val resting by animateFloatAsState(if (spinning) 1f else 0f, label = "pull indicator rest")
    val pulling by remember(pull) { derivedStateOf { pull.travel != 0f } }
    val forward by remember(pull) { derivedStateOf { pull.travel > 0f } }
    val armed by remember(pull) { derivedStateOf { pull.armed } }
    if (pulling || resting > 0f) {
        val colors = ReaderTheme.colors
        val sideways = pull.orientation == Orientation.Horizontal
        val turn by animateFloatAsState(if (armed == forward) 180f else 0f, label = "pull arrow")
        val containerColor = PullToRefreshDefaults.indicatorContainerColor
        val color = PullToRefreshDefaults.indicatorColor
        val shape = PullToRefreshDefaults.indicatorShape
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            contentAlignment = when {
                sideways && forward -> AbsoluteAlignment.CenterLeft
                sideways -> AbsoluteAlignment.CenterRight
                forward -> Alignment.TopCenter
                else -> Alignment.BottomCenter
            },
        ) {
            Box(
                Modifier
                    .graphicsLayer {
                        val progress = if (spinning) 1f else maxOf(pullProgress(pull.travel / density), resting)
                        val moved = if (spinning || !pulling) -pull.thresholdOffset / 2 else pull.offset / 2
                        if (sideways) {
                            translationX = moved
                        } else {
                            translationY = moved
                        }
                        alpha = progress
                        scaleX = 0.6f + 0.4f * progress
                        scaleY = scaleX
                        shadowElevation = PullToRefreshDefaults.Elevation.toPx()
                        this.shape = shape
                        clip = true
                    }
                    .size(INDICATOR_SIZE)
                    .background(containerColor, shape)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        if (armed) {
                            contentDescription = armedLabel
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (pulling && !spinning) {
                    Icon(
                        Icons.Outlined.ArrowDownward,
                        contentDescription = null,
                        // Turned a quarter, the downward arrow points right, the way of a pull past the first article.
                        modifier = Modifier.size(ARROW_SIZE).graphicsLayer { rotationZ = turn + if (sideways) -90f else 0f },
                        tint = if (armed) colors.accent else color,
                    )
                } else {
                    CircularProgressIndicator(Modifier.size(SPINNER_SIZE), color = color, strokeWidth = SPINNER_STROKE)
                }
            }
        }
    }
}
