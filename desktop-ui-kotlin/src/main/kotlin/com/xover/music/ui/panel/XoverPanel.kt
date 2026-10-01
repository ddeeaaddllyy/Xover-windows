package com.xover.music.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xover.music.domain.DeviceRole
import com.xover.music.domain.LikedTrack
import com.xover.music.domain.PlaybackStatus
import com.xover.music.domain.SessionViewState
import java.awt.Window as AwtWindow

@Composable
internal fun XoverPanel(
    state: SessionViewState,
    awtWindow: AwtWindow,
    settings: UiSettings,
    onSettingsChange: (UiSettings) -> Unit,
    selectedTab: XoverTab,
    onTabSelected: (XoverTab) -> Unit,
    selectedRole: SetupRole,
    onRoleSelected: (SetupRole) -> Unit,
    onCollapse: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onHost: (String, Int) -> Unit,
    onConnect: (String, Int) -> Unit,
    onAddTrackUrl: (String) -> Unit,
    onSelectTrack: (Int) -> Unit,
    onRenameTrack: (Int, String) -> Unit = { _, _ -> },
    onRemoveTrack: (Int) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onLocalVolumeChange: (Int) -> Unit,
    onDisconnect: () -> Unit,
    onDisconnectPeer: (String) -> Unit,
    onSetPeerPlaylistEditing: (String, Boolean) -> Unit = { _, _ -> },
    likedTracks: List<LikedTrack> = emptyList(),
    likesEnabled: Boolean = false,
    likesLoading: Boolean = false,
    likesFailed: Boolean = false,
    onRetryLikes: () -> Unit = {},
    onToggleLike: (String, String) -> Unit = { _, _ -> },
) {
    FloatingSurface(opacity = 1f) {
        TopBar(
            state = state,
            awtWindow = awtWindow,
            onCollapse = onCollapse,
            onMinimize = onMinimize,
            onClose = onClose,
        )

        TabStrip(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
        )

        Divider(color = BorderColor.copy(alpha = 0.7f))

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn(tween(260)) + slideInHorizontally(tween(320)) { direction * it / 4 }) togetherWith
                    fadeOut(tween(0))
            },
            label = "tabContent",
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { tab ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (tab) {
                    XoverTab.SETUP -> SetupTab(
                        state = state,
                        selectedRole = selectedRole,
                        advertisedHost = settings.advertisedHost,
                        connectHost = settings.connectHost,
                        port = settings.port,
                        onRoleSelected = onRoleSelected,
                        onAdvertisedHostChange = { onSettingsChange(settings.copy(advertisedHost = it.take(253))) },
                        onConnectHostChange = { onSettingsChange(settings.copy(connectHost = it.take(253))) },
                        onPortChange = { onSettingsChange(settings.copy(port = it.filter(Char::isDigit).take(5))) },
                        onHost = {
                            onHost(settings.advertisedHost, settings.port.toIntOrNull() ?: 0)
                        },
                        onConnect = {
                            onConnect(settings.connectHost, settings.port.toIntOrNull() ?: 0)
                        },
                        onDisconnect = onDisconnect,
                    )

                    XoverTab.PLAYER -> PlayerTab(
                        state = state,
                        likedUrls = likedTracks.map { it.sourceUrl() }.toSet(),
                        likesEnabled = likesEnabled,
                        onToggleLike = onToggleLike,
                        onAddTrackUrl = onAddTrackUrl,
                        onSelectTrack = onSelectTrack,
                        onRenameTrack = onRenameTrack,
                        onRemoveTrack = onRemoveTrack,
                        onMoveTrack = onMoveTrack,
                        onPlay = onPlay,
                        onPause = onPause,
                        onSeek = onSeek,
                        onLocalVolumeChange = onLocalVolumeChange,
                    )

                    XoverTab.LIKED -> LikedTracksTab(
                        tracks = likedTracks,
                        canAdd = state.canEditPlaylist(),
                        likesEnabled = likesEnabled,
                        loading = likesLoading,
                        failed = likesFailed,
                        onRetry = onRetryLikes,
                        onAdd = onAddTrackUrl,
                        onToggleLike = onToggleLike,
                    )

                    XoverTab.STATUS -> StatusTab(
                        state = state,
                        onDisconnectPeer = onDisconnectPeer,
                        onSetPeerPlaylistEditing = onSetPeerPlaylistEditing,
                    )

                    XoverTab.MORE -> MoreTab(
                        state = state,
                        pinned = settings.pinned,
                        opacity = settings.opacity,
                        onPinnedChange = { onSettingsChange(settings.copy(pinned = it)) },
                        onOpacityChange = { onSettingsChange(settings.copy(opacity = it)) },
                        onDisconnect = onDisconnect,
                        onDisconnectPeer = onDisconnectPeer,
                        onSetPeerPlaylistEditing = onSetPeerPlaylistEditing,
                        onCollapse = onCollapse,
                        onMinimize = onMinimize,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(state.connectionStatus())
                Text(statusTitle(state), color = SecondaryText, style = androidx.compose.material.MaterialTheme.typography.caption)
            }
            Text("${state.connectedPeerIds().size} listeners", color = SecondaryText,
                style = androidx.compose.material.MaterialTheme.typography.caption)
        }
    }
}

@Composable
internal fun CollapsedRail(
    state: SessionViewState,
    opacity: Float,
    awtWindow: AwtWindow,
    onExpand: () -> Unit,
    onMinimize: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onMute: () -> Unit,
    dockedEdges: DockedEdges = DockedEdges(),
) {
    val host = state.role() == DeviceRole.HOST
    val hasTrack = state.currentTrack() != null
    val playing = state.playbackStatus() in setOf(PlaybackStatus.PLAYING, PlaybackStatus.WAITING)
    val leftInset by animateDpAsState(if (dockedEdges.left) 0.dp else 8.dp, gentleMotion(), label = "dockLeftInset")
    val topInset by animateDpAsState(if (dockedEdges.top) 0.dp else 8.dp, gentleMotion(), label = "dockTopInset")
    val rightInset by animateDpAsState(if (dockedEdges.right) 0.dp else 8.dp, gentleMotion(), label = "dockRightInset")
    val bottomInset by animateDpAsState(if (dockedEdges.bottom) 0.dp else 8.dp, gentleMotion(), label = "dockBottomInset")
    val topLeft by animateDpAsState(if (dockedEdges.left || dockedEdges.top) 0.dp else 16.dp, gentleMotion(), label = "dockTopLeft")
    val topRight by animateDpAsState(if (dockedEdges.right || dockedEdges.top) 0.dp else 16.dp, gentleMotion(), label = "dockTopRight")
    val bottomLeft by animateDpAsState(if (dockedEdges.left || dockedEdges.bottom) 0.dp else 16.dp, gentleMotion(), label = "dockBottomLeft")
    val bottomRight by animateDpAsState(if (dockedEdges.right || dockedEdges.bottom) 0.dp else 16.dp, gentleMotion(), label = "dockBottomRight")
    val border by animateColorAsState(if (dockedEdges.attached) Color.Transparent else BorderColor, gentleMotion(), label = "dockBorder")
    val shape = RoundedCornerShape(topStart = topLeft, topEnd = topRight, bottomStart = bottomLeft, bottomEnd = bottomRight)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = leftInset, top = topInset, end = rightInset, bottom = bottomInset),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(SurfaceColor.copy(alpha = opacity))
                .border(BorderStroke(1.dp, border), shape)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RecordArtwork(state.playbackStatus() == PlaybackStatus.PLAYING, Modifier.size(26.dp))
                PulseEqualizer(playing, Modifier.size(width = 16.dp, height = 13.dp))
                Text(
                    state.trackName().ifBlank { "Xover" },
                    color = PrimaryText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).windowDrag(awtWindow, dockToEdges = true),
                )
                WindowControlButton(">", ControlGreen, onExpand)
                WindowControlButton("-", ControlYellow, onMinimize)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PrimaryButton(
                    if (playing) "Pause" else "Play", onPlayPause,
                    modifier = Modifier.weight(1f),
                    enabled = host && hasTrack && state.playbackStatus() != PlaybackStatus.ERROR,
                )
                SoftButton("Back", onBack, modifier = Modifier.weight(1f), enabled = host && hasTrack)
                SoftButton(
                    "Next", onNext, modifier = Modifier.weight(1f),
                    enabled = host && state.currentTrackIndex() < state.playlist().lastIndex,
                )
                SoftButton(
                    if (state.localVolumePercent() == 0) "Unmute" else "Mute", onMute,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

