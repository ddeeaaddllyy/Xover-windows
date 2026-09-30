package com.xover.music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.xover.music.domain.LikedTrack
import com.xover.music.domain.PlaylistTrack
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.awt.EventQueue
import java.awt.Robot
import java.awt.event.InputEvent
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO

@EnabledIfEnvironmentVariable(named = "XOVER_WINDOW_SMOKE", matches = "1")
class LikedTracksUiTest {
    @Test
    fun listenerCanLikeWithHeartAndContextMenuAndHostCanAddSavedUrl() {
        val track = PlaylistTrack.create("Midnight drive", "https://example.com/midnight.mp3")
        val liked = mutableStateOf(false)
        val added = AtomicInteger()
        val laidOut = AtomicBoolean()
        lateinit var window: ComposeWindow
        EventQueue.invokeAndWait {
            window = ComposeWindow()
            window.apply {
                isUndecorated = true
                isAlwaysOnTop = true
                setSize(430, 350)
                setLocation(40, 40)
                setContent {
                    XoverTheme {
                        Column(Modifier.fillMaxSize().background(SurfaceColor).padding(20.dp)
                            .onGloballyPositioned { laidOut.set(true) }, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            PlaylistTrackRow(
                                track = track, liked = liked.value, likesEnabled = true,
                                onToggleLike = { liked.value = !liked.value },
                                index = 0, totalTracks = 1, selected = true, canEdit = false,
                                onSelect = { fail<Unit>("A listener cannot select tracks") },
                                onDuplicate = {}, onRemove = {}, onMove = {},
                            )
                            LikedTracksTab(
                                tracks = if (liked.value) listOf(LikedTrack(track.sourceUrl(), track.title())) else emptyList(),
                                canAdd = true, likesEnabled = true, loading = false, failed = false,
                                onRetry = {}, onToggleLike = { _, _ -> liked.value = false },
                                onAdd = { url ->
                                    assertEquals(track.sourceUrl(), url)
                                    added.incrementAndGet()
                                },
                            )
                        }
                    }
                }
                isVisible = true
                toFront()
                requestFocus()
            }
        }
        val robot = Robot().apply { autoDelay = 80 }
        fun click(x: Int, y: Int, button: Int = InputEvent.BUTTON1_DOWN_MASK) {
            robot.mouseMove(window.x + x, window.y + y)
            robot.mousePress(button)
            robot.mouseRelease(button)
        }
        try {
            await { laidOut.get() }
            await {
                val background = robot.getPixelColor(window.x + 5, window.y + 5)
                background.red == 23 && background.green == 26 && background.blue == 28
            }
            robot.waitForIdle()
            Thread.sleep(300)
            repeat(3) {
                if (liked.value) return@repeat
                click(380, 49)
                val deadline = System.nanoTime() + 750_000_000L
                while (!liked.value && System.nanoTime() < deadline) Thread.sleep(30)
                if (!liked.value) EventQueue.invokeAndWait { window.toFront(); window.requestFocus() }
            }
            await { liked.value }
            Thread.sleep(400)
            capture(window, "liked-tracks")
            repeat(3) {
                if (added.get() > 0) return@repeat
                click(362, 290)
                val deadline = System.nanoTime() + 750_000_000L
                while (added.get() == 0 && System.nanoTime() < deadline) Thread.sleep(30)
            }
            await { added.get() == 1 }
            click(380, 49)
            await { !liked.value }
            click(150, 49, InputEvent.BUTTON3_DOWN_MASK)
            Thread.sleep(300)
            capture(window, "like-context-menu")
            click(220, 118)
            await { liked.value }
            assertEquals(1, added.get(), "Liking must not add the track to the playlist")
        } finally {
            EventQueue.invokeAndWait { window.dispose() }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(30)
        assertTrue(condition(), "Expected UI action did not complete")
    }

    private fun capture(window: ComposeWindow, name: String) {
        val file = File("build/reports/window-smoke/$name.png")
        file.parentFile.mkdirs()
        ImageIO.write(Robot().createScreenCapture(window.bounds), "png", file)
    }
}
