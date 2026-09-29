package com.xover.music.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xover.music.domain.ConnectionStatus
import com.xover.music.domain.DeviceRole
import com.xover.music.domain.SessionViewState

@Composable
internal fun SetupTab(
    state: SessionViewState,
    selectedRole: SetupRole,
    advertisedHost: String,
    connectHost: String,
    port: String,
    onRoleSelected: (SetupRole) -> Unit,
    onAdvertisedHostChange: (String) -> Unit,
    onConnectHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onHost: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val hostActive = state.role() == DeviceRole.HOST &&
        (state.connectionStatus() == ConnectionStatus.HOSTING || state.connectionStatus() == ConnectionStatus.CONNECTED)
    val clientActive = state.role() == DeviceRole.CLIENT &&
        (state.connectionStatus() == ConnectionStatus.CONNECTING || state.connectionStatus() == ConnectionStatus.CONNECTED)
    val blockedByClient = state.role() == DeviceRole.CLIENT && !hostActive
    val blockedByHost = state.role() == DeviceRole.HOST && !clientActive

    ScreenIntro("Good music. Same moment.", "A private room for you and your people.")

    Section("01 / Choose your side") {
        SegmentedRole(selectedRole = selectedRole, onRoleSelected = onRoleSelected)
    }

    Crossfade(
        targetState = selectedRole,
        animationSpec = tween(180),
        label = "setupRole",
    ) { role ->
        if (role == SetupRole.HOST) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Section("02 / Open a room") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = advertisedHost,
                            onValueChange = onAdvertisedHostChange,
                            label = { Text("Your VPN IP") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !hostActive && !blockedByClient,
                            colors = readableTextFieldColors(),
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = onPortChange,
                            label = { Text("Port") },
                            modifier = Modifier.width(104.dp),
                            singleLine = true,
                            enabled = !hostActive && !blockedByClient,
                            colors = readableTextFieldColors(),
                        )
                    }
                    AddressPreview("Friend connects to", advertisedHost, port)
                    StatusRow(
                        "Connected listeners",
                        state.connectedPeerIds().size.toString()
                    )
                    PrimaryButton(
                        text = if (hostActive) "Stop hosting" else "Start hosting",
                        onClick = if (hostActive) onDisconnect else onHost,
                        enabled = hostActive || !blockedByClient,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            Section("02 / Find your room") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = connectHost,
                        onValueChange = onConnectHostChange,
                        label = { Text("Host VPN IP") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !clientActive && !blockedByHost,
                        colors = readableTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = onPortChange,
                        label = { Text("Port") },
                        modifier = Modifier.width(104.dp),
                        singleLine = true,
                        enabled = !clientActive && !blockedByHost,
                        colors = readableTextFieldColors(),
                    )
                }
                AddressPreview("Opening", connectHost, port)
                PrimaryButton(
                    text = if (clientActive) "Disconnect" else "Connect",
                    onClick = if (clientActive) onDisconnect else onConnect,
                    enabled = clientActive || !blockedByHost,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
@Composable
internal fun SegmentedRole(
    selectedRole: SetupRole,
    onRoleSelected: (SetupRole) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SoftLayerColor)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        SetupRole.entries.forEach { role ->
            val selected = role == selectedRole
            val background by animateColorAsState(if (selected) AccentColor else Color.Transparent, label = "roleBackground")
            val foreground by animateColorAsState(if (selected) InkColor else SecondaryText, label = "roleForeground")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(background)
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null) { onRoleSelected(role) },
                contentAlignment = Alignment.Center,
            ) {
                Text(role.title, color = foreground, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun AddressPreview(label: String, host: String, port: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SoftLayerColor)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = SecondaryText, style = MaterialTheme.typography.caption)
        Text("http://${host.ifBlank { "VPN_IP" }}:${port.ifBlank { DefaultPort }}/health", color = PrimaryText)
    }
}

