package com.xover.music.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.xover.music.domain.DeviceRole
import com.xover.music.domain.PlaybackStatus
import com.xover.music.domain.PlaylistTrack
import com.xover.music.domain.SessionViewState
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun PlayerTab(
    state: SessionViewState,
    onAddTrackUrl: (String) -> Unit,
    onSelectTrack: (Int) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onLocalVolumeChange: (Int) -> Unit,
) {
    val duration = max(1L, state.durationMillis())
    val canEditPlaylist = state.role() != DeviceRole.CLIENT
    var trackUrl by remember { mutableStateOf("") }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var seeking by remember { mutableStateOf(false) }
    var volumePosition by remember { mutableFloatStateOf(state.localVolumePercent().toFloat()) }

    LaunchedEffect(state.positionMillis()) {
        if (!seeking) sliderPosition = min(state.positionMillis(), duration).toFloat()
    }

    LaunchedEffect(state.localVolumePercent()) {
        volumePosition = state.localVolumePercent().toFloat()
    }

    Section("Tracks") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CompactUrlField(
                value = trackUrl,
                onValueChange = { trackUrl = it },
                placeholder = "Audio URL..",
                modifier = Modifier.weight(1f),
                enabled = canEditPlaylist,
            )
            PrimaryButton(
                text = "Add",
                onClick = {
                    val nextUrl = trackUrl.trim()
                    if (nextUrl.isNotBlank()) {
                        onAddTrackUrl(nextUrl)
                        trackUrl = ""
                    }
                },
                enabled = canEditPlaylist && trackUrl.isNotBlank(),
                modifier = Modifier.width(86.dp),
            )
        }
        PlaylistEditor(
            tracks = state.playlist(),
            currentTrackIndex = state.currentTrackIndex(),
            canEdit = canEditPlaylist,
            onSelectTrack = onSelectTrack,
            onRemoveTrack = onRemoveTrack,
            onMoveTrack = onMoveTrack,
            onAddTrackUrl = onAddTrackUrl,
        )
    }

    Section("Now playing") {
        Text(
            text = state.trackName().ifBlank { "No track loaded" },
            fontWeight = FontWeight.SemiBold,
            color = PrimaryText,
        )
        Text(
            text = "${formatMillis(state.positionMillis())} / ${
                formatMillis(
                    state.durationMillis()
                )
            }",
            color = SecondaryText,
            style = MaterialTheme.typography.caption,
        )
        Slider(
            value = sliderPosition,
            onValueChange = { seeking = true; sliderPosition = it },
            onValueChangeFinished = { onSeek(sliderPosition.toLong()); seeking = false },
            valueRange = 0f..duration.toFloat(),
            enabled = state.role() == DeviceRole.HOST && state.durationMillis() > 0L,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton(
                text = if (state.playbackStatus() == PlaybackStatus.WAITING) "Waiting..." else "Play",
                onClick = onPlay,
                enabled = state.role() == DeviceRole.HOST && state.currentTrack() != null
                    && state.playbackStatus() !in setOf(PlaybackStatus.ERROR, PlaybackStatus.PLAYING, PlaybackStatus.WAITING),
                modifier = Modifier.weight(1f),
            )
            SoftButton(
                text = "Pause",
                onClick = onPause,
                enabled = state.role() == DeviceRole.HOST
                    && state.playbackStatus() in setOf(PlaybackStatus.PLAYING, PlaybackStatus.WAITING),
                modifier = Modifier.weight(1f),
            )
        }
        Text(state.message(), color = SecondaryText, style = MaterialTheme.typography.caption)
    }

    Section("Local volume") {
        StatusRow("Only this device", "${state.localVolumePercent()}%")
        Slider(
            value = volumePosition,
            onValueChange = {
                volumePosition = it
                onLocalVolumeChange(it.toInt())
            },
            valueRange = 0f..100f,
        )
    }
}

@Composable
internal fun PlaylistEditor(
    tracks: List<PlaylistTrack>,
    currentTrackIndex: Int,
    canEdit: Boolean,
    onSelectTrack: (Int) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onAddTrackUrl: (String) -> Unit,
) {
    if (tracks.isEmpty()) {
        Text("No tracks yet", color = SecondaryText, style = MaterialTheme.typography.caption)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tracks.forEachIndexed { index, track ->
            key(track.id()) {
                PlaylistTrackRow(
                    track = track,
                    index = index,
                    totalTracks = tracks.size,
                    selected = index == currentTrackIndex,
                    canEdit = canEdit,
                    onSelect = { onSelectTrack(index) },
                    onDuplicate = { onAddTrackUrl(track.sourceUrl()) },
                    onRemove = { onRemoveTrack(index) },
                    onMove = { targetIndex -> onMoveTrack(index, targetIndex) },
                )
            }
        }
    }
}

@Composable
internal fun PlaylistTrackRow(
    track: PlaylistTrack,
    index: Int,
    totalTracks: Int,
    selected: Boolean,
    canEdit: Boolean,
    onSelect: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val rowHeight = 58.dp
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeight.toPx() }
    var swipeOffsetX by remember(track.id()) { mutableFloatStateOf(0f) }
    var dragOffsetY by remember(track.id()) { mutableFloatStateOf(0f) }
    var dragging by remember(track.id()) { mutableStateOf(false) }
    var swiping by remember(track.id()) { mutableStateOf(false) }
    var clickPulse by remember(track.id()) { mutableStateOf(false) }
    var contextMenuOffset by remember(track.id()) { mutableStateOf<IntOffset?>(null) }
    val rowInteractionSource = remember { MutableInteractionSource() }
    val rowHovered by rowInteractionSource.collectIsHoveredAsState()
    val rowPressed by rowInteractionSource.collectIsPressedAsState()

    LaunchedEffect(clickPulse) {
        if (clickPulse) {
            delay(130)
            clickPulse = false
        }
    }

    val animatedSwipeX by animateFloatAsState(
        targetValue = swipeOffsetX,
        animationSpec = gentleMotion(),
        label = "playlistSwipeX",
    )
    val animatedDragY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = gentleMotion(),
        label = "playlistDragY",
    )
    val rowBackground by animateColorAsState(
        targetValue = when {
            clickPulse -> AccentColor.copy(alpha = 0.13f)
            selected -> AccentColor.copy(alpha = 0.08f)
            rowHovered && canEdit -> SoftLayerColor.copy(alpha = 0.88f)
            else -> Color.White.copy(alpha = 0.86f)
        },
        animationSpec = gentleMotion(),
        label = "playlistRowBackground",
    )
    val border by animateColorAsState(
        targetValue = if (selected) AccentColor.copy(alpha = 0.5f) else BorderColor.copy(alpha = 0.72f),
        animationSpec = gentleMotion(),
        label = "playlistRowBorder",
    )
    val rowScale by animateFloatAsState(
        targetValue = when {
            dragging -> 1.012f
            clickPulse -> 1.018f
            rowPressed -> 0.992f
            else -> 1f
        },
        animationSpec = gentleMotion(),
        label = "playlistRowScale",
    )
    val deleteRevealAlpha by animateFloatAsState(
        targetValue = if (swipeOffsetX < -8f) 1f else 0f,
        animationSpec = gentleMotion(),
        label = "playlistDeleteReveal",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .zIndex(if (dragging || contextMenuOffset != null) 1f else 0f),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = deleteRevealAlpha
                }
                .clip(RoundedCornerShape(12.dp))
                .background(DangerColor.copy(alpha = 0.14f))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text("Delete", color = DangerColor, fontWeight = FontWeight.SemiBold)
        }

        Row(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationX = if (swiping) swipeOffsetX else animatedSwipeX
                    translationY = if (dragging) dragOffsetY else animatedDragY
                    scaleX = rowScale
                    scaleY = rowScale
                }
                .clip(RoundedCornerShape(12.dp))
                .background(rowBackground)
                .border(BorderStroke(1.dp, border), RoundedCornerShape(12.dp))
                .playlistContextMenuTrigger(canEdit) { position ->
                    swipeOffsetX = 0f
                    contextMenuOffset = IntOffset(
                        x = position.x.roundToInt() + 8,
                        y = position.y.roundToInt() + 8,
                    )
                }
                .pointerInput(canEdit, track.id(), index) {
                    if (!canEdit) {
                        return@pointerInput
                    }
                    detectDragGestures(
                        onDragStart = {
                            swiping = true
                            swipeOffsetX = 0f
                            contextMenuOffset = null
                        },
                        onDragEnd = {
                            swiping = false
                            if (swipeOffsetX < -76f) {
                                onRemove()
                            }
                            swipeOffsetX = 0f
                        },
                        onDragCancel = {
                            swiping = false
                            swipeOffsetX = 0f
                        },
                    ) { change, dragAmount ->
                        if (!dragging && kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y)) {
                            change.consume()
                            swipeOffsetX = (swipeOffsetX + dragAmount.x).coerceIn(-112f, 20f)
                        }
                    }
                }
                .hoverable(rowInteractionSource, enabled = canEdit)
                .clickable(
                    enabled = canEdit,
                    indication = null,
                    interactionSource = rowInteractionSource,
                    onClick = {
                        contextMenuOffset = null
                        clickPulse = true
                        onSelect()
                    },
                )
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                color = if (selected) AccentColor else SecondaryText,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.caption,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = track.title(),
                    color = PrimaryText,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.sourceUrl(),
                    color = SecondaryText,
                    style = MaterialTheme.typography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (canEdit) {
                Text(
                    text = "::",
                    color = SecondaryText,
                    style = MaterialTheme.typography.overline,
                    modifier = Modifier.pointerInput(canEdit, track.id(), index, totalTracks) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragging = true
                                swipeOffsetX = 0f
                                contextMenuOffset = null
                            },
                            onDragEnd = {
                                val targetIndex = (index + (dragOffsetY / rowHeightPx).roundToInt())
                                    .coerceIn(0, totalTracks - 1)
                                if (targetIndex != index) {
                                    onMove(targetIndex)
                                }
                                dragOffsetY = 0f
                                dragging = false
                            },
                            onDragCancel = {
                                dragOffsetY = 0f
                                dragging = false
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            dragOffsetY += dragAmount.y
                        }
                    },
                )
            }
        }

        contextMenuOffset?.let { menuOffset ->
            PlaylistTrackContextMenu(
                offset = menuOffset,
                onDismiss = { contextMenuOffset = null },
                onSelect = {
                    clickPulse = true
                    onSelect()
                },
                onDuplicate = onDuplicate,
                onRemove = onRemove,
            )
        }
    }
}
