package com.xover.music.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xover.music.domain.ConnectionStatus
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Window as AwtWindow

@Composable
internal fun FloatingSurface(
    opacity: Float,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .background(SurfaceColor.copy(alpha = opacity))
                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(28.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}


@Composable
internal fun Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceColor.copy(alpha = 0.72f))
            .border(BorderStroke(1.dp, BorderColor.copy(alpha = 0.78f)), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            color = SecondaryText,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.caption,
        )
        content()
    }
}

@Composable
internal fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = SecondaryText)
        Text(value, color = PrimaryText, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun StatusDot(status: ConnectionStatus) {
    val color by animateColorAsState(statusColor(status), label = "statusDot")
    Box(
        modifier = Modifier
            .size(11.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
internal fun CompactUrlField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    val background by animateColorAsState(
        targetValue = when {
            !enabled -> SoftLayerColor.copy(alpha = 0.46f)
            focused -> SurfaceColor
            hovered -> SurfaceColor.copy(alpha = 0.94f)
            else -> SurfaceColor.copy(alpha = 0.82f)
        },
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "compactFieldBackground",
    )
    val border by animateColorAsState(
        targetValue = when {
            !enabled -> BorderColor.copy(alpha = 0.48f)
            focused -> AccentColor.copy(alpha = 0.68f)
            hovered -> BorderColor
            else -> BorderColor.copy(alpha = 0.78f)
        },
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "compactFieldBorder",
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(background)
            .border(BorderStroke(1.dp, border), shape)
            .hoverable(interactionSource = interactionSource, enabled = enabled),
        enabled = enabled,
        singleLine = true,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(AccentColor),
        textStyle = MaterialTheme.typography.body2.copy(
            color = if (enabled) PrimaryText else SecondaryText,
            fontWeight = FontWeight.Medium,
        ),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isBlank()) {
                    Text(
                        text = placeholder,
                        color = SecondaryText.copy(alpha = if (enabled) 0.82f else 0.5f),
                        style = MaterialTheme.typography.body2,
                        maxLines = 1,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    MotionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        backgroundColor = AccentColor,
        hoverBackgroundColor = AccentColor.copy(alpha = 0.92f),
        pressedBackgroundColor = AccentColor.copy(alpha = 0.86f),
        disabledBackgroundColor = AccentColor.copy(alpha = 0.32f),
        contentColor = Color.White,
        disabledContentColor = Color.White.copy(alpha = 0.72f),
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
internal fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    MotionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        backgroundColor = Color.White.copy(alpha = 0.72f),
        hoverBackgroundColor = Color.White.copy(alpha = 0.92f),
        pressedBackgroundColor = Color.White.copy(alpha = 0.84f),
        disabledBackgroundColor = Color.White.copy(alpha = 0.42f),
        contentColor = PrimaryText,
        disabledContentColor = SecondaryText,
        borderColor = BorderColor,
    )
}

@Composable
internal fun MotionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color,
    hoverBackgroundColor: Color,
    pressedBackgroundColor: Color,
    disabledBackgroundColor: Color,
    contentColor: Color,
    disabledContentColor: Color,
    fontWeight: FontWeight = FontWeight.Normal,
    borderColor: Color? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val buttonShape = RoundedCornerShape(14.dp)
    val offsetY by animateDpAsState(
        targetValue = when {
            !enabled -> 0.dp
            pressed -> 1.dp
            hovered -> (-2).dp
            else -> 0.dp
        },
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "motionButtonOffset",
    )
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "motionButtonScale",
    )
    val background by animateColorAsState(
        targetValue = when {
            !enabled -> disabledBackgroundColor
            pressed -> pressedBackgroundColor
            hovered -> hoverBackgroundColor
            else -> backgroundColor
        },
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "motionButtonBackground",
    )
    val foreground by animateColorAsState(
        targetValue = if (enabled) contentColor else disabledContentColor,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "motionButtonForeground",
    )
    val animatedBorder by animateColorAsState(
        targetValue = borderColor?.copy(
            alpha = when {
                !enabled -> 0.36f
                pressed -> 0.92f
                hovered -> 1f
                else -> 0.78f
            },
        ) ?: Color.Transparent,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "motionButtonBorder",
    )

    Box(
        modifier = modifier
            .height(44.dp)
            .offset(y = offsetY)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(buttonShape)
            .background(background)
            .then(
                if (borderColor != null) {
                    Modifier.border(BorderStroke(1.dp, animatedBorder), buttonShape)
                } else {
                    Modifier
                },
            )
            .hoverable(interactionSource = interactionSource, enabled = enabled)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = foreground, fontWeight = fontWeight)
    }
}

@Composable
internal fun WindowControlButton(
    symbol: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shakeOnHover: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background by animateColorAsState(
        targetValue = if (hovered) color else color.copy(alpha = 0.82f),
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "windowControlBackground",
    )
    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.08f else 1f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "windowControlScale",
    )
    val shakeTransition = rememberInfiniteTransition(label = "windowControlShake")
    val shakeOffset by shakeTransition.animateFloat(
        initialValue = -1.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 70),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "windowControlShakeOffset",
    )
    val offsetX = if (hovered && shakeOnHover) shakeOffset else 0f

    Box(
        modifier = modifier
            .size(22.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
            }
            .clip(CircleShape)
            .background(background)
            .border(BorderStroke(1.dp, color.copy(alpha = 0.35f)), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        WindowControlGlyph(symbol)
    }
}

@Composable
private fun WindowControlGlyph(symbol: String) {
    Canvas(modifier = Modifier.size(7.dp)) {
        val strokeWidth = 1.3.dp.toPx()
        val strokeColor = Color.White
        val left = size.width * 0.2f
        val right = size.width * 0.8f
        val top = size.height * 0.2f
        val bottom = size.height * 0.8f
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        when (symbol) {
            "-" -> drawLine(
                color = strokeColor,
                start = Offset(left, centerY),
                end = Offset(right, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )

            "x" -> {
                drawLine(
                    color = strokeColor,
                    start = Offset(left, top),
                    end = Offset(right, bottom),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(right, top),
                    end = Offset(left, bottom),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            "<" -> {
                drawLine(
                    color = strokeColor,
                    start = Offset(right, top),
                    end = Offset(left, centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(left, centerY),
                    end = Offset(right, bottom),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            ">" -> {
                drawLine(
                    color = strokeColor,
                    start = Offset(left, top),
                    end = Offset(right, centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(right, centerY),
                    end = Offset(left, bottom),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

internal fun Modifier.windowDrag(window: AwtWindow): Modifier = pointerInput(window) {
    var startMouse = Point()
    var startWindow = Point()

    detectDragGestures(
        onDragStart = {
            startMouse = MouseInfo.getPointerInfo().location
            startWindow = window.location
        },
        onDrag = { _, _ ->
            val currentMouse = MouseInfo.getPointerInfo().location
            window.setLocation(
                startWindow.x + currentMouse.x - startMouse.x,
                startWindow.y + currentMouse.y - startMouse.y,
            )
        },
    )
}
