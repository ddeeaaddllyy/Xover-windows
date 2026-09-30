package com.xover.music.ui

import androidx.compose.runtime.*
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import kotlin.math.abs
import java.awt.Window as AwtWindow

internal const val WindowSnapDistance = 24

internal data class DockedEdges(
    val left: Boolean = false,
    val top: Boolean = false,
    val right: Boolean = false,
    val bottom: Boolean = false,
) {
    val attached: Boolean get() = left || top || right || bottom
}

internal fun dockedEdges(bounds: Rectangle, workArea: Rectangle) = DockedEdges(
    left = abs(bounds.x - workArea.x) <= 1,
    top = abs(bounds.y - workArea.y) <= 1,
    right = abs(bounds.x + bounds.width - workArea.x - workArea.width) <= 1,
    bottom = abs(bounds.y + bounds.height - workArea.y - workArea.height) <= 1,
)

internal fun snapToScreenEdges(bounds: Rectangle, workArea: Rectangle, threshold: Int = WindowSnapDistance): Rectangle {
    val result = Rectangle(bounds)
    val right = workArea.x + workArea.width - bounds.width
    val bottom = workArea.y + workArea.height - bounds.height
    result.x = when {
        abs(bounds.x - workArea.x) <= threshold -> workArea.x
        abs(bounds.x - right) <= threshold -> right
        else -> bounds.x
    }
    result.y = when {
        abs(bounds.y - workArea.y) <= threshold -> workArea.y
        abs(bounds.y - bottom) <= threshold -> bottom
        else -> bounds.y
    }
    return result
}

/** Preserve the attached side while folding/unfolding and keep controls in the work area. */
internal fun resizedWindowBounds(previous: Rectangle, resized: Rectangle, workArea: Rectangle): Rectangle {
    val edges = dockedEdges(previous, workArea)
    val result = Rectangle(resized)
    val right = workArea.x + workArea.width - resized.width
    val bottom = workArea.y + workArea.height - resized.height
    if (edges.right) result.x = right
    if (edges.bottom) result.y = bottom
    result.x = result.x.coerceIn(workArea.x, maxOf(workArea.x, right))
    result.y = result.y.coerceIn(workArea.y, maxOf(workArea.y, bottom))
    return result
}

internal fun windowWorkArea(window: AwtWindow): Rectangle {
    val configuration = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        .map { it.defaultConfiguration }
        .maxByOrNull {
            val intersection = it.bounds.intersection(window.bounds)
            maxOf(0, intersection.width).toLong() * maxOf(0, intersection.height)
        } ?: window.graphicsConfiguration
    val bounds = Rectangle(configuration.bounds)
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
    return Rectangle(
        bounds.x + insets.left, bounds.y + insets.top,
        bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom,
    )
}

@Composable
internal fun rememberWindowDocking(window: AwtWindow, compact: Boolean): DockedEdges {
    var edges by remember(window) { mutableStateOf(DockedEdges()) }
    DisposableEffect(window, compact) {
        if (compact) {
            val snapped = snapToScreenEdges(window.bounds, windowWorkArea(window))
            window.setLocation(snapped.x, snapped.y)
        }
        var previous = Rectangle(window.bounds)
        fun updateBounds() {
            val current = Rectangle(window.bounds)
            val workArea = windowWorkArea(window)
            val resized = current.size != previous.size
            val adjusted = if (resized) resizedWindowBounds(previous, current, workArea) else current
            previous = adjusted
            if (adjusted.location != current.location) window.setLocation(adjusted.x, adjusted.y)
            edges = if (compact) dockedEdges(adjusted, workArea) else DockedEdges()
        }
        val listener = object : ComponentAdapter() {
            override fun componentMoved(event: ComponentEvent) = updateBounds()
            override fun componentResized(event: ComponentEvent) = updateBounds()
        }
        window.addComponentListener(listener)
        updateBounds()
        onDispose { window.removeComponentListener(listener) }
    }
    return edges
}
