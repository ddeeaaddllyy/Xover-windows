package com.xover.music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xover.music.domain.ConnectionStatus
import com.xover.music.domain.DeviceRole
import com.xover.music.domain.SessionViewState

@Composable
internal fun StatusTab(
    state: SessionViewState,
    onDisconnectPeer: (String) -> Unit,
    onSetPeerPlaylistEditing: (String, Boolean) -> Unit,
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
        if (state.connectedPeerIds().isEmpty()) {
            Text("No listeners connected", color = SecondaryText, style = MaterialTheme.typography.caption)
        } else {
            state.connectedPeerIds().forEach { peerId ->
                ConnectedPeerRow(
                    peerId = peerId,
                    canDisconnect = state.role() == DeviceRole.HOST,
                    canEditPlaylist = state.editablePeerIds().contains(peerId),
                    onDisconnectPeer = onDisconnectPeer,
                    onSetPeerPlaylistEditing = onSetPeerPlaylistEditing,
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
    pinned: Boolean,
    opacity: Float,
    onPinnedChange: (Boolean) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onDisconnect: () -> Unit,
    onDisconnectPeer: (String) -> Unit,
    onSetPeerPlaylistEditing: (String, Boolean) -> Unit,
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

    if (state.role() == DeviceRole.HOST) {
        Section("Listeners") {
            if (state.connectedPeerIds().isEmpty()) {
                Text("No listeners connected", color = SecondaryText, style = MaterialTheme.typography.caption)
            } else {
                state.connectedPeerIds().forEach { peerId ->
                    ConnectedPeerRow(
                        peerId = peerId,
                        canDisconnect = true,
                        canEditPlaylist = state.editablePeerIds().contains(peerId),
                        onDisconnectPeer = onDisconnectPeer,
                        onSetPeerPlaylistEditing = onSetPeerPlaylistEditing,
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
    canEditPlaylist: Boolean,
    onDisconnectPeer: (String) -> Unit,
    onSetPeerPlaylistEditing: (String, Boolean) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().playlistContextMenuTrigger(canDisconnect) { menuOpen = true },
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
        if (canEditPlaylist) {
            Text("Editor", color = AccentColor, style = MaterialTheme.typography.caption)
        }
        SoftButton(
            text = "Kick",
            onClick = { onDisconnectPeer(peerId) },
            enabled = canDisconnect,
            modifier = Modifier.width(82.dp),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(onClick = {
                menuOpen = false
                onSetPeerPlaylistEditing(peerId, !canEditPlaylist)
            }) {
                Text(if (canEditPlaylist) "Remove playlist editing" else "Allow playlist editing")
            }
        }
    }
}

