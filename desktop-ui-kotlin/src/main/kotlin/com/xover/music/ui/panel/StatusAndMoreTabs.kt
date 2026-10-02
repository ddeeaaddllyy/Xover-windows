package com.xover.music.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.xover.music.domain.ConnectionStatus
import com.xover.music.domain.DeviceRole
import com.xover.music.domain.SessionViewState
import com.xover.music.application.version.RemoteVersions

@Composable
internal fun StatusTab(
    state: SessionViewState,
    onDisconnectPeer: (String) -> Unit,
    onSetPeerRoomControl: (String, Boolean) -> Unit,
) {
    ScreenIntro("On the same wavelength.", "The connection behind the listening room.")
    Section("Session state") {
        StatusRow("Role", state.role().name)
        StatusRow("Network", state.connectionStatus().name)
        StatusRow("Playback", state.playbackStatus().name)
        StatusRow("Clock offset", "${state.clockOffsetMillis()} ms")
    }

    Section("Connected listeners") {
        StatusRow("Count", state.connectedPeerIds().size.toString())
        if (state.role() == DeviceRole.HOST && state.connectedPeerIds().isNotEmpty()) {
            Text("Right-click a listener to manage access.", color = SecondaryText, style = MaterialTheme.typography.caption)
        }
        if (state.connectedPeerIds().isEmpty()) {
            Text("No listeners connected", color = SecondaryText, style = MaterialTheme.typography.caption)
        } else {
            state.connectedPeerIds().forEach { peerId ->
                ConnectedPeerRow(
                    peerId = peerId,
                    canDisconnect = state.role() == DeviceRole.HOST,
                    canControlRoom = state.controllerPeerIds().contains(peerId),
                    onDisconnectPeer = onDisconnectPeer,
                    onSetPeerRoomControl = onSetPeerRoomControl,
                )
            }
        }
    }

    Section("Latest event") {
        Text(state.message(), color = PrimaryText)
    }
}

@Composable
internal fun MoreTab(
    state: SessionViewState,
    remoteVersions: RemoteVersions?,
    versionStatus: String,
    versionLoading: Boolean,
    onRefreshVersions: () -> Unit,
    pinned: Boolean,
    opacity: Float,
    onPinnedChange: (Boolean) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onDisconnect: () -> Unit,
    onDisconnectPeer: (String) -> Unit,
    onSetPeerRoomControl: (String, Boolean) -> Unit,
    onCollapse: () -> Unit,
    onMinimize: () -> Unit,
) {
    ScreenIntro("Make yourself at home.", "A few quiet adjustments to your space.")
    Section("Session") {
        StatusRow("Connection", state.connectionStatus().name)
        SoftButton(
            text = "Disconnect",
            onClick = onDisconnect,
            enabled = state.connectionStatus() != ConnectionStatus.DISCONNECTED,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Section("Versions from GitHub") {
        remoteVersions?.let {
            StatusRow("Current", it.current())
            StatusRow("Latest", it.latest())
        }
        if (versionStatus.isNotEmpty()) {
            Text(versionStatus, color = SecondaryText, style = MaterialTheme.typography.caption,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        SoftButton("Refresh versions", onRefreshVersions, modifier = Modifier.fillMaxWidth(), enabled = !versionLoading)
    }

    if (state.role() == DeviceRole.HOST) {
        Section("Listeners") {
            if (state.connectedPeerIds().isNotEmpty()) {
                Text("Right-click a listener to manage access.", color = SecondaryText, style = MaterialTheme.typography.caption)
            }
            if (state.connectedPeerIds().isEmpty()) {
                Text("No listeners connected", color = SecondaryText, style = MaterialTheme.typography.caption)
            } else {
                state.connectedPeerIds().forEach { peerId ->
                    ConnectedPeerRow(
                        peerId = peerId,
                        canDisconnect = true,
                        canControlRoom = state.controllerPeerIds().contains(peerId),
                        onDisconnectPeer = onDisconnectPeer,
                        onSetPeerRoomControl = onSetPeerRoomControl,
                    )
                }
            }
        }
    }

    Section("Panel") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Keep above", color = PrimaryText)
            Switch(checked = pinned, onCheckedChange = onPinnedChange)
        }
        Text("Opacity", color = SecondaryText, style = MaterialTheme.typography.caption)
        Slider(
            value = opacity,
            onValueChange = onOpacityChange,
            valueRange = 0.78f..0.98f,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SoftButton("Collapse", onCollapse, modifier = Modifier.weight(1f))
            SoftButton("Minimize", onMinimize, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun ConnectedPeerRow(
    peerId: String,
    canDisconnect: Boolean,
    canControlRoom: Boolean,
    onDisconnectPeer: (String) -> Unit,
    onSetPeerRoomControl: (String, Boolean) -> Unit,
) {
    var menuOffset by remember { mutableStateOf<IntOffset?>(null) }
    Row(
        modifier = Modifier.fillMaxWidth().playlistContextMenuTrigger(canDisconnect) {
            menuOffset = IntOffset(it.x.toInt(), it.y.toInt())
        },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = peerId,
            color = PrimaryText,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (canControlRoom) {
            Text("Room control", color = AccentColor, style = MaterialTheme.typography.caption)
        }
        menuOffset?.let { offset ->
            PeerContextMenu(
                peerId = peerId,
                offset = offset,
                canControlRoom = canControlRoom,
                onDismiss = { menuOffset = null },
                onToggleAccess = {
                    menuOffset = null
                    onSetPeerRoomControl(peerId, !canControlRoom)
                },
                onKick = {
                    menuOffset = null
                    onDisconnectPeer(peerId)
                },
            )
        }
    }
}

@Composable
private fun PeerContextMenu(
    peerId: String,
    offset: IntOffset,
    canControlRoom: Boolean,
    onDismiss: () -> Unit,
    onToggleAccess: () -> Unit,
    onKick: () -> Unit,
) {
    Popup(
        popupPositionProvider = TrackMenuPositionProvider(offset, 8),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnClickOutside = true, clippingEnabled = true),
    ) {
        Column(
            modifier = Modifier
                .width(238.dp)
                .shadow(8.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceColor)
                .border(BorderStroke(1.dp, BorderColor.copy(alpha = 0.86f)), RoundedCornerShape(10.dp))
                .verticalScroll(rememberScrollState())
                .padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                peerId,
                color = SecondaryText,
                style = MaterialTheme.typography.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            )
            TrackContextMenuItem(
                symbol = if (canControlRoom) "−" else "+",
                title = if (canControlRoom) "Remove room control" else "Allow room control",
                caption = "Playlist, play, pause and seek",
                tone = AccentColor,
                onClick = onToggleAccess,
            )
            TrackContextMenuItem(
                symbol = "×",
                title = "Kick listener",
                caption = "Disconnect this device",
                tone = DangerColor,
                onClick = onKick,
            )
        }
    }
}

