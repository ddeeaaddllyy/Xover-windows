package com.xover.music.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/** A drawn record rather than placeholder album art; motion follows actual playback. */
@Composable
internal fun RecordArtwork(playing: Boolean, modifier: Modifier = Modifier) {
    val rotation = remember { Animatable(0f) }
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        while (playing) {
            rotation.animateTo(rotation.value + 360f, tween(6_000, easing = LinearEasing))
            rotation.snapTo(rotation.value % 360f)
        }
    }
    LaunchedEffect(playing) {
        if (playing) {
            while (true) {
                pulse.snapTo(0f)
                pulse.animateTo(1f, tween(1_500, easing = LinearEasing))
            }
        } else {
            pulse.animateTo(0f, tween(260))
        }
    }
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        val recordRadius = radius * 0.85f
        if (playing) {
            drawCircle(AccentColor.copy(alpha = (1f - pulse.value) * 0.42f),
                radius * (0.88f + 0.11f * pulse.value), style = Stroke(1.7.dp.toPx()))
        }
        drawCircle(BorderColor, recordRadius + 1.dp.toPx())
        rotate(rotation.value) {
            drawCircle(InkColor, recordRadius)
            for (i in 0..8) {
                drawCircle(BorderColor.copy(alpha = 0.68f), recordRadius * (0.44f + i * 0.06f),
                    style = Stroke(0.7.dp.toPx()))
            }
            drawArc(PrimaryText.copy(alpha = 0.46f), 210f, 53f, false,
                topLeft = Offset(center.x - recordRadius * 0.8f, center.y - recordRadius * 0.8f),
                size = Size(recordRadius * 1.6f, recordRadius * 1.6f),
                style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
            drawArc(PrimaryText.copy(alpha = 0.22f), 38f, 31f, false,
                topLeft = Offset(center.x - recordRadius * 0.63f, center.y - recordRadius * 0.63f),
                size = Size(recordRadius * 1.26f, recordRadius * 1.26f),
                style = Stroke(0.8.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(AccentColor, recordRadius * 0.34f)
            drawCircle(InkColor.copy(alpha = 0.3f), recordRadius * 0.24f, style = Stroke(0.8.dp.toPx()))
            drawLine(InkColor, Offset(center.x - recordRadius * 0.18f, center.y - recordRadius * 0.10f),
                Offset(center.x + recordRadius * 0.18f, center.y - recordRadius * 0.10f), 2.dp.toPx())
            drawCircle(InkColor, recordRadius * 0.065f)
        }
    }
}

@Composable
internal fun PulseEqualizer(playing: Boolean, modifier: Modifier = Modifier) {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (!playing) {
            phase.animateTo(0f, tween(220))
        } else {
            while (true) {
                phase.snapTo(0f)
                phase.animateTo(1f, tween(1_050, easing = LinearEasing))
            }
        }
    }
    Canvas(modifier) {
        val barWidth = size.width / 7f
        repeat(4) { index ->
            val wave = if (playing) (sin(phase.value * Math.PI * 2 + index * 1.4) + 1f).toFloat() / 2f else 0.14f
            val height = size.height * (0.25f + 0.7f * wave)
            val x = barWidth * (index * 2 + 0.5f)
            drawLine(AccentColor.copy(alpha = if (playing) 0.95f else 0.45f),
                Offset(x, (size.height - height) / 2f), Offset(x, (size.height + height) / 2f),
                strokeWidth = barWidth, cap = StrokeCap.Round)
        }
    }
}
