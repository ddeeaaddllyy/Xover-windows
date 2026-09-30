package com.xover.music.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** Keep both panels composed and measure them at their own stable sizes while the window resizes. */
@Composable
internal fun PanelTransition(
    progress: Float,
    opacity: Float = 1f,
    compact: @Composable () -> Unit,
    expanded: @Composable () -> Unit,
) {
    Layout(
        modifier = Modifier
            .graphicsLayer(alpha = opacity, compositingStrategy = CompositingStrategy.Offscreen)
            .clipToBounds()
            .drawBehind {
                if (progress > 0f && progress < 1f) {
                    val inset = 10.dp.toPx()
                    drawRoundRect(SurfaceColor, Offset(inset, inset),
                        Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f)),
                        CornerRadius(18.dp.toPx()))
                }
            },
        content = {
            Box { expanded() }
            Box { compact() }
        },
    ) { measurables, constraints ->
        val full = measurables[0].measure(Constraints.fixed(ExpandedSize.width.roundToPx(), ExpandedSize.height.roundToPx()))
        val mini = measurables[1].measure(Constraints.fixed(CollapsedSize.width.roundToPx(), CollapsedSize.height.roundToPx()))
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Unplaced panels retain their state but cannot receive pointer input or focus.
            val fullAlpha = (1f - progress * 2.5f).coerceIn(0f, 1f)
            val miniAlpha = ((progress - 0.4f) / 0.6f).coerceIn(0f, 1f)
            if (fullAlpha > 0f) full.placeWithLayer(0, 0) { alpha = fullAlpha }
            if (miniAlpha > 0f) mini.placeWithLayer(0, 0) { alpha = miniAlpha }
        }
    }
}
