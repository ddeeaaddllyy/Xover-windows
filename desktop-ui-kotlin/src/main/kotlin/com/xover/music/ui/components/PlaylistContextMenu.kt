package com.xover.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondary
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.playlistContextMenuTrigger(
    enabled: Boolean,
    onOpen: (Offset) -> Unit,
): Modifier = composed {
    val currentOnOpen by rememberUpdatedState(onOpen)

    if (!enabled) {
        Modifier
    } else {
        Modifier.pointerInput(enabled) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Press && event.button.isSecondary) {
                        event.changes.firstOrNull()?.position?.let(currentOnOpen)
                        event.changes.forEach { change -> change.consume() }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlaylistTrackContextMenu(
    offset: IntOffset,
    onDismiss: () -> Unit,
    onSelect: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    Popup(
        alignment = Alignment.TopStart,
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(
                animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
            ) + scaleIn(
                initialScale = 0.96f,
                transformOrigin = TransformOrigin(0f, 0f),
                animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
            ),
        ) {
            Column(
                modifier = Modifier
                    .width(218.dp)
                    .shadow(18.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceColor.copy(alpha = 0.98f))
                    .border(BorderStroke(1.dp, BorderColor.copy(alpha = 0.86f)), RoundedCornerShape(18.dp))
                    .padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TrackContextMenuHeader()
                TrackContextMenuItem(
                    symbol = ">",
                    title = "Play now",
                    caption = "Select this track",
                    tone = AccentColor,
                    onClick = {
                        onDismiss()
                        onSelect()
                    },
                )
                TrackContextMenuItem(
                    symbol = "+",
                    title = "Add again",
                    caption = "Duplicate the URL",
                    tone = WarmColor,
                    onClick = {
                        onDismiss()
                        onDuplicate()
                    },
                )
                TrackContextMenuItem(
                    symbol = "x",
                    title = "Delete",
                    caption = "Remove from playlist",
                    tone = DangerColor,
                    onClick = {
                        onDismiss()
                        onRemove()
                    },
                )
            }
        }
    }
}

@Composable
private fun TrackContextMenuHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(AccentColor),
        )
        Text(
            text = "Track actions",
            color = SecondaryText,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.caption,
        )
    }
}

@Composable
private fun TrackContextMenuItem(
    symbol: String,
    title: String,
    caption: String,
    tone: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background by animateColorAsState(
        targetValue = if (hovered) tone.copy(alpha = 0.10f) else Color.Transparent,
        animationSpec = tween(durationMillis = 130, easing = FastOutSlowInEasing),
        label = "trackMenuItemBackground",
    )
    val iconBackground by animateColorAsState(
        targetValue = tone.copy(alpha = if (hovered) 0.18f else 0.12f),
        animationSpec = tween(durationMillis = 130, easing = FastOutSlowInEasing),
        label = "trackMenuIconBackground",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(background)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(29.dp)
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = symbol,
                color = tone,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.body2,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = PrimaryText,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = caption,
                color = SecondaryText,
                style = MaterialTheme.typography.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
