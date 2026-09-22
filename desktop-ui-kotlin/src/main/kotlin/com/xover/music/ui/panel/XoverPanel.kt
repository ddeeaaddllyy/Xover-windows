package com.xover.music.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xover.music.domain.SessionViewState
import java.awt.Window as AwtWindow

@Composable
internal fun XoverPanel(
    state: SessionViewState,
    awtWindow: AwtWindow,
    pinned: Boolean,
    opacity: Float,
    onPinnedChange: (Boolean) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onCollapse: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onHost: (String, Int) -> Unit,
    onConnect: (String, Int) -> Unit,
    onAddTrackUrl: (String) -> Unit,
    onSelectTrack: (Int) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onLocalVolumeChange: (Int) -> Unit,
    onDisconnect: () -> Unit,
    onDisconnectPeer: (String) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(XoverTab.SETUP) }
    var selectedRole by remember { mutableStateOf(SetupRole.HOST) }
    var advertisedHost by remember { mutableStateOf("127.0.0.1") }
    var connectHost by remember { mutableStateOf("127.0.0.1") }
    var port by remember { mutableStateOf(DefaultPort.toString()) }
    val animatedOpacity by animateFloatAsState(opacity.coerceIn(0.78f, 0.98f), label = "panelOpacity")

    FloatingSurface(opacity = animatedOpacity) {
        TopBar(
            state = state,
            awtWindow = awtWindow,
            onCollapse = onCollapse,
            onMinimize = onMinimize,
            onClose = onClose,
        )

        TabStrip(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
        )

        Divider(color = BorderColor.copy(alpha = 0.7f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (selectedTab) {
                XoverTab.SETUP -> SetupTab(
                    state = state,
                    selectedRole = selectedRole,
                    advertisedHost = advertisedHost,
                    connectHost = connectHost,
                    port = port,
                    onRoleSelected = { selectedRole = it },
                    onAdvertisedHostChange = { advertisedHost = it },
                    onConnectHostChange = { connectHost = it },
                    onPortChange = { port = it.filter(Char::isDigit).take(5) },
                    onHost = {
                        onHost(advertisedHost, port.toIntOrNull() ?: DefaultPort)
                    },
                    onConnect = {
                        onConnect(connectHost, port.toIntOrNull() ?: DefaultPort)
                    },
                    onDisconnect = onDisconnect,
                )

                XoverTab.PLAYER -> PlayerTab(
                    state = state,
                    onAddTrackUrl = onAddTrackUrl,
                    onSelectTrack = onSelectTrack,
                    onRemoveTrack = onRemoveTrack,
                    onMoveTrack = onMoveTrack,
                    onPlay = onPlay,
                    onPause = onPause,
                    onSeek = onSeek,
                    onLocalVolumeChange = onLocalVolumeChange,
                )

                XoverTab.STATUS -> StatusTab(
                    state = state,
                    onDisconnectPeer = onDisconnectPeer,
                )

                XoverTab.MORE -> MoreTab(
                    state = state,
                    pinned = pinned,
                    opacity = opacity,
                    onPinnedChange = onPinnedChange,
                    onOpacityChange = onOpacityChange,
                    onDisconnect = onDisconnect,
                    onDisconnectPeer = onDisconnectPeer,
                    onCollapse = onCollapse,
                    onMinimize = onMinimize,
                )
            }
        }
    }
}

@Composable
internal fun CollapsedRail(
    state: SessionViewState,
    awtWindow: AwtWindow,
    onExpand: () -> Unit,
    onMinimize: () -> Unit,
) {
    val railWidth by animateDpAsState(64.dp, label = "railWidth")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(railWidth)
                .windowDrag(awtWindow)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceColor.copy(alpha = 0.96f))
                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(24.dp))
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusDot(state.connectionStatus())
            Text("XO", fontWeight = FontWeight.Bold, color = PrimaryText)
            WindowControlButton(">", ControlGreen, onExpand)
            WindowControlButton("-", ControlYellow, onMinimize)
        }
    }
}

