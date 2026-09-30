package com.xover.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.awt.EventQueue
import java.awt.Robot

@EnabledIfEnvironmentVariable(named = "XOVER_WINDOW_SMOKE", matches = "1")
class OpaquePanelSmokeTest {
    @Test
    fun dimmedPanelDoesNotShowBrightContentBehindIt() {
        lateinit var window: ComposeWindow
        EventQueue.invokeAndWait {
            window = ComposeWindow().apply {
                isUndecorated = true
                setSize(430, 720)
                setLocation(40, 40)
                setContent {
                    XoverTheme {
                        Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                            FloatingSurface(opacity = 0.78f) { }
                        }
                    }
                }
                isVisible = true
            }
        }
        try {
            val robot = Robot()
            val deadline = System.nanoTime() + 5_000_000_000L
            while (System.nanoTime() < deadline) {
                val background = robot.getPixelColor(window.x + 2, window.y + 2)
                if (background.red > 200 && background.blue > 200) break
                Thread.sleep(30)
            }
            val pixel = robot.getPixelColor(window.x + 30, window.y + 60)
            assertTrue(pixel.red < 40 && pixel.green < 40 && pixel.blue < 40,
                "Content behind the dimmed panel must not show through: $pixel")
        } finally {
            EventQueue.invokeAndWait { window.dispose() }
        }
    }
}
