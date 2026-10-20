package cc.hafa.reader.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransitionsTest {
    private val size = IntSize(400, 800)

    @Test
    fun `back sends a screen toward the end of the reading direction`() {
        assertEquals(IntOffset(400, 0), offscreen(Offset.Zero, size, LayoutDirection.Ltr))
        assertEquals(IntOffset(-400, 0), offscreen(Offset.Zero, size, LayoutDirection.Rtl))
    }

    @Test
    fun `a drag sends the article on the way it went, a whole width or height`() {
        assertEquals(IntOffset(-400, 0), offscreen(Offset(-1f, 0f), size, LayoutDirection.Ltr))
        assertEquals(IntOffset(-400, 0), offscreen(Offset(-1f, 0f), size, LayoutDirection.Rtl))
        assertEquals(IntOffset(0, 800), offscreen(Offset(0f, 1f), size, LayoutDirection.Ltr))
        assertEquals(IntOffset(0, -800), offscreen(Offset(0f, -1f), size, LayoutDirection.Ltr))
    }

    @Test
    fun `pages fade evenly to half opacity a page away either side`() {
        assertEquals(1f, pageAlpha(0f))
        assertEquals(0.75f, pageAlpha(0.5f))
        assertEquals(0.75f, pageAlpha(-0.5f))
        assertEquals(0.5f, pageAlpha(1f))
        assertEquals(0.5f, pageAlpha(2f))
    }

    @Test
    fun `the list scrolls only to a row not wholly in view`() {
        assertNull(revealIndex(5, 3, 8))
        assertNull(revealIndex(3, 3, 8))
        assertNull(revealIndex(8, 3, 8))
        assertEquals(2, revealIndex(2, 3, 8))
        assertEquals(9, revealIndex(9, 3, 8))
        assertEquals(0, revealIndex(0, 0, -1))
        assertNull(revealIndex(-1, 3, 8))
    }
}
