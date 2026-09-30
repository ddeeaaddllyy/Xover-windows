package com.xover.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.dp
import com.xover.music.domain.PlaylistTrack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.awt.EventQueue
import java.awt.Robot
import java.awt.event.InputEvent
import java.io.File
import javax.imageio.ImageIO

@EnabledIfEnvironmentVariable(named = "XOVER_WINDOW_SMOKE", matches = "1")
class PlaylistReorderSmokeTest {
    @Test
    fun downArrowMovesTrackWithoutSelectingIt() {
        val tracks = mutableStateListOf(
            PlaylistTrack.create("First", "https://example.com/first.mp3"),
            PlaylistTrack.create("Second", "https://example.com/second.mp3"),
            PlaylistTrack.create("Third", "https://example.com/third.mp3"),
        )
        var selections = 0
        lateinit var window: ComposeWindow
        EventQueue.invokeAndWait {
            window = ComposeWindow().apply {
                isUndecorated = true
                isAlwaysOnTop = true
                setSize(430, 260)
                setLocation(40, 40)
                setContent {
                    XoverTheme {
                        Column(Modifier.fillMaxSize().background(SurfaceColor)
                            .padding(horizontal = 42.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            tracks.forEachIndexed { index, track ->
                                key(track.id()) {
                                    PlaylistTrackRow(
                                        track = track,
                                        liked = false,
                                        likesEnabled = false,
                                        onToggleLike = {},
                                        index = index,
                                        totalTracks = tracks.size,
                                        selected = false,
                                        canEdit = true,
                                        onSelect = { selections++ },
                                        onDuplicate = {},
                                        onRemove = {},
                                        onMove = { target ->
                                            val source = tracks.indexOfFirst { it.id() == track.id() }
                                            tracks.add(target, tracks.removeAt(source))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                isVisible = true
                toFront()
            }
        }
        try {
            Thread.sleep(500)
            val robot = Robot().apply { autoDelay = 80 }
            val screenshot = File("build/reports/window-smoke/reorder-controls.png")
            screenshot.parentFile.mkdirs()
            ImageIO.write(robot.createScreenCapture(window.bounds), "png", screenshot)
            robot.mouseMove(window.x + 316, window.y + 62)
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            Thread.sleep(250)
            assertEquals("Second", tracks[0].title())
            assertEquals("First", tracks[1].title())
            assertEquals(0, selections)

            robot.mouseMove(window.x + 316, window.y + 100)
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            Thread.sleep(250)
            assertEquals("First", tracks[0].title())
            assertEquals("Second", tracks[1].title())
            assertEquals(0, selections)
        } finally {
            EventQueue.invokeAndWait { window.dispose() }
        }
    }
}
