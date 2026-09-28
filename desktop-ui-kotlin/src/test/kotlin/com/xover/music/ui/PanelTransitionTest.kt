package com.xover.music.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.awt.ComposeWindow
import com.xover.music.domain.SessionViewState
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
    @Test
    fun restoresPanelWithoutRemeasuringItsContentOrLosingState() {
        val progress = mutableStateOf(0f)
        val measured = AtomicReference(IntSize.Zero)
        val compositions = AtomicInteger()
        lateinit var window: ComposeWindow
        EventQueue.invokeAndWait {
            window = ComposeWindow()
            window.apply {
                isUndecorated = true
                setSize(430, 720)
                setLocation(40, 40)
                setContent {
                    MaterialTheme(colors = xoverColors(), typography = xoverTypography()) {
                        PanelTransition(progress.value, compact = {
                            CollapsedRail(SessionViewState.idle(), 1f, window, {}, {}, {}, {}, {}, {})
                        }, expanded = {
                            remember { compositions.incrementAndGet() }
                            Box(Modifier.fillMaxSize().onSizeChanged { measured.set(it) }) {
                                XoverPanel(
                                    state = SessionViewState.idle(), awtWindow = window,
                                    settings = UiSettings(), onSettingsChange = {},
                                    selectedTab = XoverTab.PLAYER, onTabSelected = {},
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
