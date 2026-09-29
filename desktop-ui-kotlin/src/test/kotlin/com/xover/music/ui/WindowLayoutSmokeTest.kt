package com.xover.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.dp
import com.xover.music.domain.PlaylistTrack
import com.xover.music.domain.SessionViewState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.awt.EventQueue
import java.awt.Robot
import java.awt.event.InputEvent
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

@EnabledIfEnvironmentVariable(named = "XOVER_WINDOW_SMOKE", matches = "1")
class WindowLayoutSmokeTest {
    @Test
    fun compactWindowAttachesToTheCornerAndKeepsItWhenExpanded() {
        val compact = mutableStateOf(true)
        lateinit var window: ComposeWindow
        EventQueue.invokeAndWait {
            window = ComposeWindow()
            window.apply {
                isUndecorated = true
                setSize(344, 132)
                val workArea = windowWorkArea(this)
                setLocation(workArea.x + workArea.width - width - 12, workArea.y + workArea.height - height - 12)
                setContent {
                    val edges = rememberWindowDocking(window, compact.value)
                    XoverTheme {
                        CollapsedRail(SessionViewState.idle(), 1f, window, {}, {}, {}, {}, {}, {}, edges)
                    }
                }
                isVisible = true
            }
        }
        fun isAtCorner(): Boolean {
            var attached = false
            EventQueue.invokeAndWait {
                val edges = dockedEdges(window.bounds, windowWorkArea(window))
                attached = edges.right && edges.bottom
            }
            return attached
        }
        fun awaitCorner() {
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!isAtCorner() && System.nanoTime() < deadline) Thread.sleep(30)
            assertTrue(isAtCorner(), "The window must keep its bottom-right anchor")
        }
        try {
            awaitCorner()
            Thread.sleep(400)
            val screenshot = File("build/reports/window-smoke/compact-docked.png")
            screenshot.parentFile.mkdirs()
            ImageIO.write(Robot().createScreenCapture(window.bounds), "png", screenshot)
            EventQueue.invokeAndWait { compact.value = false }
            Thread.sleep(200)
            EventQueue.invokeAndWait { window.setSize(430, 720) }
            awaitCorner()
        } finally {
            EventQueue.invokeAndWait { window.dispose() }
        }
    }

    @Test
    fun canDuplicateATrackFromTheMenuAtTheBottomRightOfTheWindow() {
        val duplicates = AtomicInteger()
        lateinit var window: ComposeWindow
        EventQueue.invokeAndWait {
            window = ComposeWindow().apply {
                isUndecorated = true
                setSize(430, 720)
                setLocation(40, 40)
                setContent {
                    XoverTheme {
                        Column(Modifier.fillMaxSize().background(SurfaceColor).padding(20.dp)) {
                            Spacer(Modifier.weight(1f))
                            PlaylistTrackRow(
                                track = PlaylistTrack.create("Menu at the screen edge", "https://example.com/edge.mp3"),
                                liked = false, likesEnabled = true, onToggleLike = {},
                                index = 0, totalTracks = 1, selected = false, canEdit = true,
                                onSelect = {}, onDuplicate = { duplicates.incrementAndGet() },
                                onRemove = {}, onMove = {},
                            )
                        }
                    }
                }
                isVisible = true
            }
        }
        val robot = Robot().apply { autoDelay = 80 }
        fun click(x: Int, y: Int, button: Int) {
            robot.mouseMove(window.x + x, window.y + y)
            robot.mousePress(button)
            robot.mouseRelease(button)
        }
        try {
            Thread.sleep(800)
            click(390, 680, InputEvent.BUTTON3_DOWN_MASK)
            Thread.sleep(350)
            val screenshot = File("build/reports/window-smoke/track-menu-bottom-right.png")
            screenshot.parentFile.mkdirs()
            ImageIO.write(robot.createScreenCapture(window.bounds), "png", screenshot)
            // The menu flips upward; Add again is the third of its four actions.
            click(280, 610, InputEvent.BUTTON1_DOWN_MASK)
            Thread.sleep(250)
            assertEquals(1, duplicates.get(), "The duplicate action must be fully visible and clickable")
        } finally {
            EventQueue.invokeAndWait { window.dispose() }
        }
    }
}
