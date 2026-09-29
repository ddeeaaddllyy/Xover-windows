package com.xover.music.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LikeButton(liked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.9f else 1f, tween(120), label = "heartPress")
    val fill by animateFloatAsState(if (liked) 1f else 0f, tween(220), label = "heartFill")
    val halo by animateColorAsState(
        if (hovered) DangerColor.copy(alpha = 0.10f) else Color.Transparent,
        tween(160), label = "heartHover",
    )
    val bloom = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(liked) }
    val color by animateColorAsState(if (liked) DangerColor else SecondaryText, tween(180), label = "likeColor")
    LaunchedEffect(liked) {
        if (previous != liked) {
            previous = liked
            if (liked) {
                bloom.snapTo(0f)
                bloom.animateTo(1f, tween(380))
            }
        }
    }
    val label = if (liked) "Remove from liked tracks" else "Like track"
    TooltipArea(tooltip = {
        Surface(color = SoftLayerColor, shape = CircleShape, elevation = 0.dp) {
            Text(label, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = PrimaryText)
        }
    }) {
        Box(
            Modifier.size(36.dp)
                .semantics { contentDescription = label; selected = liked }
                .clip(CircleShape)
                .background(halo)
                .hoverable(interaction)
                .clickable(enabled = enabled, role = Role.Button, interactionSource = interaction,
                    indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(20.dp).graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }) {
                val w = size.width
                val h = size.height
                val heart = Path().apply {
                    moveTo(w * 0.5f, h * 0.9f)
                    cubicTo(w * 0.1f, h * 0.64f, 0f, h * 0.42f, w * 0.1f, h * 0.23f)
                    cubicTo(w * 0.2f, h * 0.04f, w * 0.4f, h * 0.04f, w * 0.5f, h * 0.24f)
                    cubicTo(w * 0.6f, h * 0.04f, w * 0.8f, h * 0.04f, w * 0.9f, h * 0.23f)
                    cubicTo(w, h * 0.42f, w * 0.9f, h * 0.64f, w * 0.5f, h * 0.9f)
                    close()
                }
                val pulse = 1f + kotlin.math.sin(bloom.value * kotlin.math.PI.toFloat()) * 0.08f
                scale(pulse) {
                    drawPath(heart, DangerColor.copy(alpha = fill))
                    drawPath(heart, color, style = Stroke(1.5.dp.toPx()))
                }
            }
        }
    }
}
