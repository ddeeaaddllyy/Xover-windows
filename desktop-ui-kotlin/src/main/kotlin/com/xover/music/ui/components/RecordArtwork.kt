package com.xover.music.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** A drawn record rather than placeholder album art; motion follows actual playback. */
@Composable
internal fun RecordArtwork(playing: Boolean, modifier: Modifier = Modifier) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        while (playing) {
            rotation.animateTo(rotation.value + 360f, tween(12_000, easing = LinearEasing))
            rotation.snapTo(rotation.value % 360f)
        }
    }
    Canvas(modifier.graphicsLayer { rotationZ = rotation.value }) {
        val radius = size.minDimension / 2f
        drawCircle(InkColor, radius)
        for (i in 0..8) {
            drawCircle(BorderColor.copy(alpha = 0.65f), radius * (0.48f + i * 0.06f), style = Stroke(0.7.dp.toPx()))
        }
        drawCircle(AccentColor, radius * 0.34f)
        drawCircle(InkColor.copy(alpha = 0.3f), radius * 0.24f, style = Stroke(0.8.dp.toPx()))
        drawLine(InkColor, Offset(center.x - radius * 0.18f, center.y - radius * 0.10f),
            Offset(center.x + radius * 0.18f, center.y - radius * 0.10f), 2.dp.toPx())
        drawCircle(InkColor, radius * 0.065f)
        drawCircle(PrimaryText.copy(alpha = 0.12f), radius * 0.92f, style = Stroke(1.dp.toPx()))
    }
}
