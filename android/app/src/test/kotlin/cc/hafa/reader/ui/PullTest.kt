package cc.hafa.reader.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The same numbers as the web's gestures.test.ts.
class PullTest {
    @Test
    fun `a pulled article moves less and less as the finger goes further`() {
        assertEquals(0f, pullStretch(0f))
        assertEquals(9.694f, pullStretch(10f), 0.001f)
        assertEquals(72.19f, pullStretch(96f), 0.001f)
        assertEquals(-72.19f, pullStretch(-96f), 0.001f)
        assertEquals(114.159f, pullStretch(200f), 0.001f)
        assertEquals(146.866f, pullStretch(400f), 0.001f)
        assertTrue(pullStretch(1000f) < ARTICLE_PULL_STRETCH)
    }

    @Test
    fun `a pull's progress reaches 1 at the closing distance and stays there`() {
        assertEquals(0f, pullProgress(0f))
        assertEquals(0.5f, pullProgress(48f))
        assertEquals(0.5f, pullProgress(-48f))
        assertEquals(1f, pullProgress(ARTICLE_PULL_DISTANCE))
        assertEquals(1f, pullProgress(400f))
    }

    // Frames 16 ms apart, as fast as the test can take them.
    private object TestClock : MonotonicFrameClock {
        private var frame = 0L

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            frame += 16_000_000L
            return onFrame(frame)
        }
    }

    private fun endPull(): EdgeDrag =
        EdgeDrag(Orientation.Vertical, ARTICLE_PULL_DISTANCE, fromEdgeOnly = true, pastEndOnly = true, staysOnRelease = false) {}

    private fun EdgeDrag.dragUp(amount: Float) {
        onPostScroll(Offset.Zero, Offset(0f, -amount), NestedScrollSource.UserInput)
    }

    @Test
    fun `a pull past the end does nothing while it is off, as while refreshing`() {
        val drag = endPull()
        drag.enabled = false
        drag.dragUp(50f)
        assertEquals(0f, drag.travel)
    }

    @Test
    fun `a pull cancelled midway springs back and stops following the finger`() = runBlocking {
        val drag = endPull()
        drag.dragUp(50f)
        assertEquals(-50f, drag.travel)
        withContext(TestClock) { drag.cancel() }
        assertEquals(0f, drag.travel)
        drag.dragUp(50f)
        assertEquals(0f, drag.travel)
    }
}
