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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    likedUrls: Set<String>,
    likesEnabled: Boolean,
    onToggleLike: (String, String) -> Unit,
    onAddTrackUrl: (String) -> Unit,
    onSelectTrack: (Int) -> Unit,
    onRenameTrack: (Int, String) -> Unit = { _, _ -> },
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

    val playing = state.playbackStatus() in setOf(PlaybackStatus.PLAYING, PlaybackStatus.WAITING)
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(SoftLayerColor.copy(alpha = 0.55f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("ON THE DECK", style = MaterialTheme.typography.overline, color = AccentColor)
            Text(state.playbackStatus().name, style = MaterialTheme.typography.overline, color = SecondaryText)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            RecordArtwork(playing = state.playbackStatus() == PlaybackStatus.PLAYING, modifier = Modifier.size(100.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.trackName().ifBlank { "Room for a\ngood record." },
                    style = MaterialTheme.typography.h6, color = PrimaryText, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(if (state.currentTrack() == null) "Add a link to begin listening." else
                    "TRACK ${(state.currentTrackIndex() + 1).toString().padStart(2, '0')} / ${state.playlist().size.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.caption, color = SecondaryText)
            }
            state.currentTrack()?.let { track ->
                LikeButton(track.sourceUrl() in likedUrls, likesEnabled) { onToggleLike(track.sourceUrl(), track.title()) }
            }
        }
        Column {
            Slider(
                value = sliderPosition,
                onValueChange = { seeking = true; sliderPosition = it },
                onValueChangeFinished = { onSeek(sliderPosition.toLong()); seeking = false },
                valueRange = 0f..duration.toFloat(),
                enabled = state.role() == DeviceRole.HOST && state.durationMillis() > 0L,
                modifier = Modifier.fillMaxWidth().height(26.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMillis(state.positionMillis()), color = PrimaryText, style = MaterialTheme.typography.overline)
                Text(formatMillis(state.durationMillis()), color = SecondaryText, style = MaterialTheme.typography.overline)
            }
        }
        PrimaryButton(
            text = when { state.playbackStatus() == PlaybackStatus.WAITING -> "Cancel scheduled start"
                playing -> "Pause playback"; else -> "Play record" },
            onClick = if (playing) onPause else onPlay,
            enabled = state.role() == DeviceRole.HOST && state.currentTrack() != null && state.playbackStatus() != PlaybackStatus.ERROR,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(state.message(), color = SecondaryText, style = MaterialTheme.typography.caption,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("OUTPUT", color = SecondaryText, style = MaterialTheme.typography.overline)
        Slider(value = volumePosition, onValueChange = {
            volumePosition = it
            onLocalVolumeChange(it.toInt())
        }, valueRange = 0f..100f, modifier = Modifier.weight(1f).height(24.dp))
        Text("${state.localVolumePercent()}%", color = PrimaryText, style = MaterialTheme.typography.overline,
            modifier = Modifier.width(38.dp))
    }
    Section("QUEUE / ${state.playlist().size.toString().padStart(2, '0')}") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CompactUrlField(trackUrl, { trackUrl = it }, "Paste a track link", Modifier.weight(1f), canEditPlaylist)
            SoftButton("Add", {
                val next = trackUrl.trim()
                if (next.isNotBlank()) { onAddTrackUrl(next); trackUrl = "" }
            }, Modifier.width(56.dp), enabled = canEditPlaylist && trackUrl.isNotBlank())
        }
        PlaylistEditor(
            tracks = state.playlist(), likedUrls = likedUrls, likesEnabled = likesEnabled,
            onToggleLike = onToggleLike, currentTrackIndex = state.currentTrackIndex(), canEdit = canEditPlaylist,
            onSelectTrack = onSelectTrack, onRemoveTrack = onRemoveTrack, onMoveTrack = onMoveTrack, onAddTrackUrl = onAddTrackUrl,
            onRenameTrack = onRenameTrack,
        )
    }

}

@Composable
internal fun PlaylistEditor(
    tracks: List<PlaylistTrack>,
    likedUrls: Set<String>,
    likesEnabled: Boolean,
    onToggleLike: (String, String) -> Unit,
    currentTrackIndex: Int,
    canEdit: Boolean,
    onSelectTrack: (Int) -> Unit,
    onRenameTrack: (Int, String) -> Unit = { _, _ -> },
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
                    liked = track.sourceUrl() in likedUrls,
                    likesEnabled = likesEnabled,
                    onToggleLike = { onToggleLike(track.sourceUrl(), track.title()) },
                    index = index,
                    totalTracks = tracks.size,
                    selected = index == currentTrackIndex,
                    canEdit = canEdit,
                    onSelect = { onSelectTrack(index) },
                    onRename = { title -> onRenameTrack(index, title) },
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
    liked: Boolean,
    likesEnabled: Boolean,
    onToggleLike: () -> Unit,
    index: Int,
    totalTracks: Int,
    selected: Boolean,
    canEdit: Boolean,
    onSelect: () -> Unit,
    onRename: (String) -> Unit = {},
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
    var editing by remember(track.id()) { mutableStateOf(false) }
    var draftTitle by remember(track.id()) { mutableStateOf(track.title()) }
    val renameFocus = remember(track.id()) { FocusRequester() }
    LaunchedEffect(editing) {
        if (editing) renameFocus.requestFocus()
    }
    fun beginRename() {
        draftTitle = track.title()
        contextMenuOffset = null
        editing = true
    }
    fun saveRename() {
        val title = draftTitle.trim()
        if (title.isNotEmpty() && title.length <= 160) {
            editing = false
            onRename(title)
        }
    }
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
            else -> SurfaceColor.copy(alpha = 0.6f)
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
            clickPulse -> 1f
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
                .clip(RoundedCornerShape(8.dp))
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
                .clip(RoundedCornerShape(8.dp))
                .background(rowBackground)
                .border(BorderStroke(1.dp, border), RoundedCornerShape(8.dp))
                .playlistContextMenuTrigger(true) { position ->
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
                    enabled = canEdit && !editing,
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
                if (editing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BasicTextField(
                            value = draftTitle,
                            onValueChange = { draftTitle = it.take(160) },
                            modifier = Modifier.weight(1f).focusRequester(renameFocus).onPreviewKeyEvent {
                                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (it.key) {
                                    Key.Enter -> { saveRename(); true }
                                    Key.Escape -> { editing = false; true }
                                    else -> false
                                }
                            },
                            singleLine = true,
                            cursorBrush = SolidColor(AccentColor),
                            textStyle = MaterialTheme.typography.body1.copy(color = PrimaryText),
                        )
                        Text("✓", color = AccentColor, modifier = Modifier.clickable { saveRename() })
                        Text("×", color = SecondaryText, modifier = Modifier.clickable { editing = false })
                    }
                } else {
                    Text(
                        text = track.title(),
                        color = PrimaryText,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = track.sourceUrl(),
                    color = SecondaryText,
                    style = MaterialTheme.typography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LikeButton(liked, likesEnabled, onToggleLike)
            if (canEdit) {
                Text("✎", color = AccentColor, modifier = Modifier.clickable { beginRename() })
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
                canEdit = canEdit,
                liked = liked,
                likesEnabled = likesEnabled,
                onToggleLike = onToggleLike,
                onDismiss = { contextMenuOffset = null },
                onSelect = {
                    clickPulse = true
                    onSelect()
                },
                onRename = { beginRename() },
                onDuplicate = onDuplicate,
                onRemove = onRemove,
            )
        }
    }
}
