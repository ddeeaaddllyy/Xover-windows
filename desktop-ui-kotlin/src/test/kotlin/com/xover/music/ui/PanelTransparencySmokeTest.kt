package com.xover.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.awt.EventQueue
import java.awt.Robot
import kotlin.math.abs
import kotlin.math.roundToInt

@EnabledIfEnvironmentVariable(named = "XOVER_WINDOW_SMOKE", matches = "1")
class PanelTransparencySmokeTest {
    @Test
    fun nativeWindowOpacityRevealsDesktopBehindOpaquePanel() {
        val robot = Robot()
        val background = robot.getPixelColor(70, 100)
        lateinit var window: ComposeWindow
        EventQueue.invokeAndWait {
            window = ComposeWindow().apply {
                isUndecorated = true
                isAlwaysOnTop = true
                isTransparent = true
                setSize(430, 720)
                setLocation(40, 40)
                setContent {
                    XoverTheme {
                        FloatingSurface(opacity = 1f) {
                            Box(Modifier.fillMaxSize().background(Color.Green))
                        }
                    }
                }
                opacity = 0.78f
                isVisible = true
                toFront()
            }
        }
        try {
            Thread.sleep(300)
            val pixel = robot.getPixelColor(70, 100)
            val expectedRed = (background.red * 0.22f).roundToInt()
            val expectedGreen = (255f * 0.78f + background.green * 0.22f).roundToInt()
            val expectedBlue = (background.blue * 0.22f).roundToInt()
            assertTrue(abs(pixel.red - expectedRed) < 25 &&
                abs(pixel.green - expectedGreen) < 25 &&
                abs(pixel.blue - expectedBlue) < 25,
                "Window transparency must blend with the desktop: background=$background, panel=$pixel")
        } finally {
            EventQueue.invokeAndWait { window.dispose() }
        }
    }

    @Test
    fun collapsedPanelDoesNotShowHiddenExpandedContent() {
        val progress = mutableStateOf(0f)
        lateinit var window: ComposeWindow
        EventQueue.invokeAndWait {
            window = ComposeWindow().apply {
                isUndecorated = true
                isAlwaysOnTop = true
                isTransparent = true
                setSize(430, 720)
                setLocation(40, 40)
                setContent {
                    XoverTheme {
                        Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                            PanelTransition(progress.value,
                                compact = { Box(Modifier.fillMaxSize()) },
                                expanded = { Box(Modifier.size(430.dp, 720.dp).background(Color.Green)) },
                            )
                        }
                    }
                }
                isVisible = true
                toFront()
            }
        }
        try {
            Thread.sleep(300)
            for (fraction in listOf(0.6f, 1f)) {
                EventQueue.invokeAndWait {
                    progress.value = fraction
                    window.setSize((430 - 86 * fraction).toInt(), (720 - 588 * fraction).toInt())
                }
                Thread.sleep(300)
                val pixel = Robot().getPixelColor(window.x + 30, window.y + 60)
                assertTrue(pixel.green < 40,
                    "Expanded content must not appear beneath the compact panel at $fraction: $pixel")
            }
        } finally {
            EventQueue.invokeAndWait { window.dispose() }
        }
    }
}
