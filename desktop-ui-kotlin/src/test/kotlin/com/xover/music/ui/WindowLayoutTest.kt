package com.xover.music.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Rectangle

class WindowLayoutTest {
    private val workArea = Rectangle(0, 0, 1920, 1040)

    @Test
    fun compactWindowSnapsToEachScreenEdgeAndCorners() {
        assertEquals(Rectangle(0, 200, 344, 132), snapToScreenEdges(Rectangle(18, 200, 344, 132), workArea))
        assertEquals(Rectangle(1576, 200, 344, 132), snapToScreenEdges(Rectangle(1560, 200, 344, 132), workArea))
        assertEquals(Rectangle(300, 0, 344, 132), snapToScreenEdges(Rectangle(300, 15, 344, 132), workArea))
        assertEquals(Rectangle(300, 908, 344, 132), snapToScreenEdges(Rectangle(300, 900, 344, 132), workArea))
        val corner = snapToScreenEdges(Rectangle(1560, 900, 344, 132), workArea)
        assertEquals(DockedEdges(right = true, bottom = true), dockedEdges(corner, workArea))
    }

    @Test
    fun movingAwayFromEdgesReleasesDocking() {
        val floating = Rectangle(300, 200, 344, 132)
        assertEquals(floating, snapToScreenEdges(floating, workArea))
        assertFalse(dockedEdges(floating, workArea).attached)
    }

    @Test
    fun expandingAndCollapsingKeepTheBottomRightAnchor() {
        var previous = Rectangle(1576, 908, 344, 132)
        for (size in listOf(370 to 310, 430 to 720, 370 to 310, 344 to 132)) {
            previous = resizedWindowBounds(previous, Rectangle(previous.x, previous.y, size.first, size.second), workArea)
            assertEquals(1920, previous.x + previous.width)
            assertEquals(1040, previous.y + previous.height)
        }
    }

    @Test
    fun dockingSupportsAMonitorLeftOfThePrimaryDisplayAndTaskbarInsets() {
        val secondary = Rectangle(-1920, 40, 1920, 1040)
        val snapped = snapToScreenEdges(Rectangle(-1910, 52, 344, 132), secondary)
        assertEquals(Rectangle(-1920, 40, 344, 132), snapped)
        assertEquals(DockedEdges(left = true, top = true), dockedEdges(snapped, secondary))
    }

    @Test
    fun expansionKeepsTheTopControlsVisibleOnSmallScreens() {
        val smallScreen = Rectangle(0, 0, 800, 600)
        assertEquals(Rectangle(370, 0, 430, 720), resizedWindowBounds(
            Rectangle(456, 468, 344, 132), Rectangle(456, 468, 430, 720), smallScreen,
        ))
    }

    @Test
    fun trackMenuStaysFullyVisibleAtEveryWindowCorner() {
        val window = IntSize(430, 720)
        val menu = IntSize(218, 260)
        for (click in listOf(IntOffset(12, 12), IntOffset(418, 12), IntOffset(12, 708), IntOffset(418, 708))) {
            val position = TrackMenuPositionProvider(click, 8).calculatePosition(
                IntRect(0, 0, 430, 720), window, LayoutDirection.Ltr, menu,
            )
            assertTrue(position.x >= 8 && position.y >= 8)
            assertTrue(position.x + menu.width <= window.width - 8)
            assertTrue(position.y + menu.height <= window.height - 8)
        }
    }

    @Test
    fun trackMenuUsesTheTrackRowPositionAndFlipsUpward() {
        val position = TrackMenuPositionProvider(IntOffset(180, 30), 8).calculatePosition(
            IntRect(50, 620, 380, 678), IntSize(430, 720), LayoutDirection.Ltr, IntSize(218, 260),
        )
        assertEquals(IntOffset(12, 390), position)
    }
}
