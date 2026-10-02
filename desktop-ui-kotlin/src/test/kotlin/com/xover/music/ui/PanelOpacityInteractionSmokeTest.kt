package com.xover.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.awt.EventQueue
import java.awt.Robot
import java.awt.event.InputEvent
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt

@EnabledIfEnvironmentVariable(named = "XOVER_WINDOW_SMOKE", matches = "1")
class PanelOpacityInteractionSmokeTest {
    @Test
    fun changingPanelOpacityKeepsExpandedAndCompactControlsClickable() {
        val opacity = mutableStateOf(0.98f)
        val progress = mutableStateOf(0f)
        val expandedClicks = AtomicInteger()
        val compactClicks = AtomicInteger()
        val robot = Robot().apply { autoDelay = 80 }
        val background = robot.getPixelColor(110, 140)
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
                        PanelTransition(progress.value, opacity.value,
                            compact = {
                                Box(Modifier.fillMaxSize().background(Color.Blue)
                                    .clickable { compactClicks.incrementAndGet() })
                            },
                            expanded = {
                                Column(Modifier.fillMaxSize().background(Color.Green)) {
                                    Slider(value = opacity.value, onValueChange = { opacity.value = it },
                                        valueRange = 0.78f..0.98f, modifier = Modifier.fillMaxWidth())
                                    Box(Modifier.fillMaxWidth().weight(1f)
                                        .clickable { expandedClicks.incrementAndGet() })
                                }
                            },
                        )
                    }
                }
                isVisible = true
                toFront()
            }
        }
        try {
            fun click() {
                robot.mouseMove(window.x + 70, window.y + 100)
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            }

            Thread.sleep(400)
            click()
            assertEquals(1, expandedClicks.get(), "Expanded panel must respond before opacity changes")

            robot.mouseMove(window.x + 385, window.y + 24)
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            robot.mouseMove(window.x + 70, window.y + 24)
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            Thread.sleep(300)
            val actualOpacity = opacity.value
            assertTrue(actualOpacity < 0.88f, "Dragging the slider must change opacity: $actualOpacity")
            val pixel = robot.getPixelColor(window.x + 70, window.y + 100)
            val expectedRed = (background.red * (1f - actualOpacity)).roundToInt()
            val expectedGreen = (255f * actualOpacity + background.green * (1f - actualOpacity)).roundToInt()
            val expectedBlue = (background.blue * (1f - actualOpacity)).roundToInt()
            assertTrue(abs(pixel.red - expectedRed) < 25 &&
                abs(pixel.green - expectedGreen) < 25 &&
                abs(pixel.blue - expectedBlue) < 25,
                "Panel transparency must blend with the desktop: background=$background, panel=$pixel")
            assertEquals(1f, window.opacity, "Changing the slider must not change native window opacity")
            click()
            assertEquals(2, expandedClicks.get(), "Expanded panel must respond after opacity changes")

            EventQueue.invokeAndWait {
                progress.value = 1f
                window.setSize(344, 132)
            }
            Thread.sleep(300)
            click()
            assertEquals(1, compactClicks.get(), "Compact panel must respond after opacity changes")
        } finally {
            EventQueue.invokeAndWait { window.dispose() }
        }
    }
}
