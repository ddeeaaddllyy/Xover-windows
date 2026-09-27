package com.xover.music.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    onRemoveTrack: (Int) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onLocalVolumeChange: (Int) -> Unit,
    onDisconnect: () -> Unit,
    onDisconnectPeer: (String) -> Unit,
) {
    val animatedOpacity by animateFloatAsState(
        settings.opacity, animationSpec = gentleMotion(), label = "panelOpacity",
    )

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
            onTabSelected = onTabSelected,
        )

        Divider(color = BorderColor.copy(alpha = 0.7f))

        Crossfade(
            targetState = selectedTab,
            animationSpec = tween(180),
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
                        pinned = settings.pinned,
                        opacity = settings.opacity,
                        onPinnedChange = { onSettingsChange(settings.copy(pinned = it)) },
                        onOpacityChange = { onSettingsChange(settings.copy(opacity = it)) },
                        onDisconnect = onDisconnect,
                        onDisconnectPeer = onDisconnectPeer,
                        onCollapse = onCollapse,
                        onMinimize = onMinimize,
                    )
                }
            }
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
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(64.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceColor.copy(alpha = opacity))
                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(24.dp))
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusDot(state.connectionStatus())
            Text("XO", fontWeight = FontWeight.Bold, color = PrimaryText, modifier = Modifier.windowDrag(awtWindow))
            WindowControlButton(">", ControlGreen, onExpand)
            WindowControlButton("-", ControlYellow, onMinimize)
        }
    }
}

