package com.xover.music.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.awt.ComposeWindow
import com.xover.music.domain.SessionViewState
import com.xover.music.domain.PlaylistTrack
import com.xover.music.domain.LikedTrack
import com.xover.music.domain.DeviceRole
import com.xover.music.domain.PlaybackStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.awt.EventQueue
import java.awt.Frame
import java.awt.Robot
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO

/** Opt-in native window smoke test; needs an interactive desktop. */
@EnabledIfEnvironmentVariable(named = "XOVER_WINDOW_SMOKE", matches = "1")
class PanelTransitionTest {
    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun restoresPanelWithoutRemeasuringItsContentOrLosingState() {
        val progress = mutableStateOf(0f)
        val selectedTab = mutableStateOf(XoverTab.PLAYER)
        val tracks = listOf(
            PlaylistTrack.create("Nights on the coast", "https://example.com/nights.mp3"),
            PlaylistTrack.create("A little further home", "https://example.com/home.mp3"),
        )
        val session = SessionViewState.idle().withRole(DeviceRole.HOST).withPlaylist(tracks, 0)
            .withPlaybackStatus(PlaybackStatus.READY).withTrack("Nights on the coast", 216_000)
        val measured = AtomicReference(IntSize.Zero)
        val compositions = AtomicInteger()
        lateinit var window: ComposeWindow
        EventQueue.invokeAndWait {
            window = ComposeWindow()
            window.apply {
                isUndecorated = true
                setSize(430, 720)
                setLocation(40, 40)
                background = java.awt.Color(23, 26, 28)
                setContent {
                    XoverTheme {
                        PanelTransition(progress.value, compact = {
                            CollapsedRail(session, 1f, window, {}, {}, {}, {}, {}, {})
                        }, expanded = {
                            remember { compositions.incrementAndGet() }
                            Box(Modifier.fillMaxSize().onSizeChanged { measured.set(it) }) {
                                XoverPanel(
                                    state = session, awtWindow = window,
                                    settings = UiSettings(), onSettingsChange = {},
                                    selectedTab = selectedTab.value, onTabSelected = { selectedTab.value = it },
                                    likedTracks = tracks.map { LikedTrack(it.sourceUrl(), it.title()) },
                                    likesEnabled = true,
                                    selectedRole = SetupRole.HOST, onRoleSelected = {},
                                    onCollapse = {}, onMinimize = {}, onClose = {},
                                    onHost = { _, _ -> }, onConnect = { _, _ -> },
                                    onAddTrackUrl = {}, onSelectTrack = {}, onRemoveTrack = {},
                                    onMoveTrack = { _, _ -> }, onPlay = {}, onPause = {},
                                    onSeek = {}, onLocalVolumeChange = {}, onDisconnect = {},
                                    onDisconnectPeer = {},
                                )
                            }
                        })
                    }
                }
                isVisible = true
            }
        }
        try {
            await { measured.get() != IntSize.Zero }
            val original = measured.get()
            for (tab in XoverTab.entries) {
                EventQueue.invokeAndWait { selectedTab.value = tab }
                Thread.sleep(300)
                capture(window, "screen-${tab.name.lowercase()}")
            }
            EventQueue.invokeAndWait { selectedTab.value = XoverTab.PLAYER }
            repeat(3) {
                for (fraction in listOf(0.25f, 0.6f, 1f, 0.6f, 0.25f, 0f)) {
                    EventQueue.invokeAndWait {
                        progress.value = fraction
                        window.setSize((430 - 86 * fraction).toInt(), (720 - 588 * fraction).toInt())
                    }
                    Thread.sleep(120)
                    assertEquals(original, measured.get())
                    assertEquals(1, compositions.get())
                    if (fraction == 1f) capture(window, "compact")
                }
            }
            EventQueue.invokeAndWait { window.extendedState = Frame.ICONIFIED }
            Thread.sleep(200)
            EventQueue.invokeAndWait { window.extendedState = Frame.NORMAL }
            Thread.sleep(300)
            assertEquals(original, measured.get())
            assertEquals(1, compositions.get())
            capture(window, "restored")
        } finally {
            EventQueue.invokeAndWait { window.dispose() }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(50)
        assertTrue(condition(), "Window did not render")
    }

    private fun capture(window: ComposeWindow, name: String) {
        val file = File("build/reports/window-smoke/$name.png")
        file.parentFile.mkdirs()
        EventQueue.invokeAndWait {
            ImageIO.write(Robot().createScreenCapture(window.bounds), "png", file)
        }
    }
}
