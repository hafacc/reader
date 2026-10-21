package cc.hafa.reader.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.roundToInt

// How long a screen takes to slide over or off the list, and the list to come back under it.
private const val SCREEN_MS = 300
// A switch between two detail screens fades the old one out, then the new one in.
private const val FADE_OUT_MS = 90
private const val FADE_IN_MS = 210
// How far the list shrinks and fades while a screen covers it.
private const val UNDER_SCALE = 0.95f
private const val UNDER_ALPHA = 0.4f
// How faded an article is a whole page away from the middle of the pager.
private const val FAR_PAGE_ALPHA = 0.5f

/** What the phone shows: the list, or a detail screen. */
data class PhonePlace(
    /** The screen on show. */
    val screen: Screen,
    /** What the detail screen holds; null on the list. */
    val detail: Detail?,
)

/**
 * The article being left: the last snapshot that showed it, so it stays drawn
 * while it slides off, and which way it was sent.
 */
class LeavingArticle {
    /** The last snapshot with the open article, while it is open and as it leaves; null after another detail screen. */
    var snapshot: Snapshot? = null

    /** Which way a drag sent the article off, as a unit step along one axis; zero for Back, which sends it toward the end. */
    var towards: Offset = Offset.Zero
}

/**
 * Where content of [size] ends up when sent [towards] (a unit step, or zero
 * for toward the end of the reading direction): a whole width or height away.
 */
fun offscreen(towards: Offset, size: IntSize, layoutDirection: LayoutDirection): IntOffset =
    if (towards == Offset.Zero) {
        IntOffset(if (layoutDirection == LayoutDirection.Ltr) size.width else -size.width, 0)
    } else {
        IntOffset((towards.x * size.width).roundToInt(), (towards.y * size.height).roundToInt())
    }

/** How opaque a pager's page is [distance] pages from the middle: whole there, fading evenly to half opacity a whole page away. */
fun pageAlpha(distance: Float): Float = 1f - (1f - FAR_PAGE_ALPHA) * minOf(1f, abs(distance))

/**
 * The list index to scroll to so the row at [index] is in full view, given the
 * first and last rows wholly shown; null when it already is or is not in the list.
 */
fun revealIndex(index: Int, firstWhole: Int, lastWhole: Int): Int? =
    if (index < 0 || index in firstWhole..lastWhole) {
        null
    } else {
        index
    }

/**
 * How the phone moves between places. A detail screen slides in from the end
 * over the list, which shrinks and dims under it; leaving one slides it off,
 * toward the end for Back or the way a drag sent the article, while the list
 * comes back up under it. One detail screen replacing another fades through.
 * Nothing moves when the system turns animations off.
 */
fun AnimatedContentTransitionScope<PhonePlace>.placeTransform(
    animate: Boolean,
    leaving: LeavingArticle,
    layoutDirection: LayoutDirection,
): ContentTransform {
    val opening = initialState.screen == Screen.List && targetState.screen == Screen.Detail
    val closing = initialState.screen == Screen.Detail && targetState.screen == Screen.List
    return if (!animate) {
        EnterTransition.None togetherWith ExitTransition.None
    } else if (opening) {
        val enter = slideIn(tween(SCREEN_MS, easing = FastOutSlowInEasing)) { size -> offscreen(Offset.Zero, size, layoutDirection) }
        val exit = scaleOut(tween(SCREEN_MS, easing = FastOutSlowInEasing), targetScale = UNDER_SCALE) +
            fadeOut(tween(SCREEN_MS, easing = FastOutSlowInEasing), targetAlpha = UNDER_ALPHA)
        (enter togetherWith exit).apply { targetContentZIndex = 1f }
    } else if (closing) {
        // Only an article is dragged off; Back sends every detail screen toward the end.
        val towards = if (initialState.detail == Detail.Article) leaving.towards else Offset.Zero
        // A drag hands the article on already moving, so it slows to a stop rather than starting slowly.
        val easing = if (towards == Offset.Zero) FastOutSlowInEasing else LinearOutSlowInEasing
        val enter = scaleIn(tween(SCREEN_MS, easing = FastOutSlowInEasing), initialScale = UNDER_SCALE) +
            fadeIn(tween(SCREEN_MS, easing = FastOutSlowInEasing), initialAlpha = UNDER_ALPHA)
        val exit = slideOut(tween(SCREEN_MS, easing = easing)) { size -> offscreen(towards, size, layoutDirection) }
        (enter togetherWith exit).apply { targetContentZIndex = -1f }
    } else {
        fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS)) togetherWith fadeOut(tween(FADE_OUT_MS))
    }
}

/** Takes every touch when [blocking], so a screen sliding away cannot be used on its way out. */
fun Modifier.blockingTouches(blocking: Boolean): Modifier =
    if (blocking) {
        pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { change -> change.consume() }
                }
            }
        }
    } else {
        this
    }
