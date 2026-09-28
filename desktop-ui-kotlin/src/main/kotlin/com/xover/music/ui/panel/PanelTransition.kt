package com.xover.music.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

/** Keep both panels composed and measure them at their own stable sizes while the window resizes. */
@Composable
internal fun PanelTransition(
    progress: Float,
    compact: @Composable () -> Unit,
    expanded: @Composable () -> Unit,
) {
    Layout(
        modifier = Modifier.clipToBounds(),
        content = {
            Box { expanded() }
            Box { compact() }
        },
    ) { measurables, constraints ->
        val full = measurables[0].measure(Constraints.fixed(ExpandedSize.width.roundToPx(), ExpandedSize.height.roundToPx()))
        val mini = measurables[1].measure(Constraints.fixed(CollapsedSize.width.roundToPx(), CollapsedSize.height.roundToPx()))
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Unplaced panels retain their state but cannot receive pointer input or focus.
            if (progress < 1f) full.placeWithLayer(0, 0) { alpha = 1f - progress }
            if (progress > 0f) mini.placeWithLayer(0, 0) { alpha = progress }
        }
    }
}
