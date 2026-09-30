package com.xover.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.awt.EventQueue
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO

@EnabledIfEnvironmentVariable(named = "XOVER_WINDOW_SMOKE", matches = "1")
class PlaybackMotionSmokeTest {
    @Test
    fun playingArtworkAndEqualizerChangeAcrossFrames() {
        lateinit var window: ComposeWindow
        EventQueue.invokeAndWait {
            window = ComposeWindow().apply {
                isUndecorated = true
                setSize(230, 230)
                setLocation(40, 40)
                setContent {
                    XoverTheme {
                        Column(Modifier.fillMaxSize().background(SurfaceColor),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            RecordArtwork(true, Modifier.size(160.dp))
                            Spacer(Modifier.height(10.dp))
                            PulseEqualizer(true, Modifier.size(width = 26.dp, height = 16.dp))
                        }
                    }
                }
                isVisible = true
            }
        }
        try {
            val robot = Robot()
            Thread.sleep(750)
            val first = robot.createScreenCapture(window.bounds)
            Thread.sleep(650)
            val second = robot.createScreenCapture(window.bounds)
            val directory = File("build/reports/window-smoke").apply { mkdirs() }
            ImageIO.write(first, "png", File(directory, "playback-motion-first.png"))
            ImageIO.write(second, "png", File(directory, "playback-motion-second.png"))
            var changed = 0
            for (y in 0 until first.height) {
                for (x in 0 until first.width) {
                    if (first.getRGB(x, y) != second.getRGB(x, y)) changed++
                }
            }
            assertTrue(changed > 100, "Playback art must have visible motion")
        } finally {
            EventQueue.invokeAndWait { window.dispose() }
        }
    }
}
