package com.xover.music.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font as DesktopFont
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.xover.music.application.session.ListeningSessionService
import com.xover.music.application.session.SessionObserver
import com.xover.music.domain.ConnectionStatus
import com.xover.music.domain.DeviceRole
import com.xover.music.domain.PlaybackStatus
import com.xover.music.domain.SessionViewState
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.MouseInfo
import java.awt.Point
import java.io.File
import java.nio.file.Paths
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import java.awt.Window as AwtWindow

class DesktopApplication(
    private val sessionService: ListeningSessionService,
) {
    fun start() = application {
        var collapsed by remember { mutableStateOf(false) }
        var pinned by remember { mutableStateOf(false) }
        var opacity by remember { mutableFloatStateOf(0.94f) }
        val windowState = rememberWindowState(
            position = WindowPosition(24.dp, 92.dp),
            size = ExpandedSize,
        )
        val targetSize = if (collapsed) CollapsedSize else ExpandedSize
        val animatedWidth by animateDpAsState(
            targetValue = targetSize.width,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "windowWidth",
        )
        val animatedHeight by animateDpAsState(
            targetValue = targetSize.height,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "windowHeight",
        )

        LaunchedEffect(animatedWidth, animatedHeight) {
            windowState.size = DpSize(animatedWidth, animatedHeight)
        }

        Window(
            onCloseRequest = {
                sessionService.close()
                exitApplication()
            },
            title = "Xover",
            state = windowState,
            undecorated = true,
            transparent = true,
            resizable = false,
            alwaysOnTop = pinned,
        ) {
            val state = rememberSessionState(sessionService)
            val minimizeWindow = {
                window.extendedState = Frame.ICONIFIED
            }

            MaterialTheme(
                colors = xoverColors(),
                typography = xoverTypography(),
            ) {
                Crossfade(
                    targetState = collapsed,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    label = "panelCrossfade",
                ) { isCollapsed ->
                    if (isCollapsed) {
                        CollapsedRail(
                            state = state.value,
                            awtWindow = window,
                            onExpand = { collapsed = false },
                            onMinimize = minimizeWindow,
                        )
                    } else {
                        XoverPanel(
                            state = state.value,
                            awtWindow = window,
                            pinned = pinned,
                            opacity = opacity,
                            onPinnedChange = { pinned = it },
                            onOpacityChange = { opacity = it },
                            onCollapse = { collapsed = true },
                            onMinimize = minimizeWindow,
                            onClose = {
                                sessionService.close()
                                exitApplication()
                            },
                            onHost = { trackPath, advertisedHost, port ->
                                sessionService.startHost(Paths.get(trackPath), advertisedHost, port)
                            },
                            onConnect = { host, port ->
                                sessionService.connectToHost(host, port)
                            },
                            onChooseFile = {
                                chooseFile()?.absolutePath.orEmpty()
                            },
                            onPlay = sessionService::play,
                            onPause = sessionService::pause,
                            onSeek = sessionService::seek,
                            onLocalVolumeChange = sessionService::setLocalVolumePercent,
                            onDisconnect = sessionService::disconnect,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberSessionState(sessionService: ListeningSessionService): MutableState<SessionViewState> {
    val state = remember { mutableStateOf(sessionService.currentState()) }
    DisposableEffect(sessionService) {
        val observer = SessionObserver { next ->
            EventQueue.invokeLater {
                state.value = next
            }
        }
        sessionService.addObserver(observer)
        onDispose {
            sessionService.removeObserver(observer)
        }
    }
    return state
}

@Composable
private fun XoverPanel(
    state: SessionViewState,
    awtWindow: AwtWindow,
    pinned: Boolean,
    opacity: Float,
    onPinnedChange: (Boolean) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onCollapse: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onHost: (String, String, Int) -> Unit,
    onConnect: (String, Int) -> Unit,
    onChooseFile: () -> String,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onLocalVolumeChange: (Int) -> Unit,
    onDisconnect: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(XoverTab.SETUP) }
    var selectedRole by remember { mutableStateOf(SetupRole.HOST) }
    var trackPath by remember { mutableStateOf("") }
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
                    selectedRole = selectedRole,
                    trackPath = trackPath,
                    advertisedHost = advertisedHost,
                    connectHost = connectHost,
                    port = port,
                    onRoleSelected = { selectedRole = it },
                    onTrackPathChange = { trackPath = it },
                    onAdvertisedHostChange = { advertisedHost = it },
                    onConnectHostChange = { connectHost = it },
                    onPortChange = { port = it.filter(Char::isDigit).take(5) },
                    onChooseFile = {
                        val selected = onChooseFile()
                        if (selected.isNotBlank()) {
                            trackPath = selected
                        }
                    },
                    onHost = {
                        onHost(trackPath, advertisedHost, port.toIntOrNull() ?: DefaultPort)
                    },
                    onConnect = {
                        onConnect(connectHost, port.toIntOrNull() ?: DefaultPort)
                    },
                )

                XoverTab.PLAYER -> PlayerTab(
                    state = state,
                    onPlay = onPlay,
                    onPause = onPause,
                    onSeek = onSeek,
                    onLocalVolumeChange = onLocalVolumeChange,
                )

                XoverTab.STATUS -> StatusTab(state)

                XoverTab.MORE -> MoreTab(
                    state = state,
                    pinned = pinned,
                    opacity = opacity,
                    onPinnedChange = onPinnedChange,
                    onOpacityChange = onOpacityChange,
                    onDisconnect = onDisconnect,
                    onCollapse = onCollapse,
                    onMinimize = onMinimize,
                )
            }
        }
    }
}

@Composable
private fun CollapsedRail(
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
            WindowControlButton("›", ControlGreen, onExpand)
            WindowControlButton("−", ControlYellow, onMinimize)
        }
    }
}

@Composable
private fun FloatingSurface(
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
private fun TopBar(
    state: SessionViewState,
    awtWindow: AwtWindow,
    onCollapse: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowDrag(awtWindow),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(AccentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                StatusDot(state.connectionStatus())
            }
            Column {
                Text("Xover", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                Text(statusTitle(state), color = SecondaryText, style = MaterialTheme.typography.caption)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WindowControlButton("‹", ControlGreen, onCollapse, shakeOnHover = true)
            WindowControlButton("−", ControlYellow, onMinimize, shakeOnHover = true)
            WindowControlButton("×", ControlRed, onClose, shakeOnHover = true)
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
@Composable
private fun TabStrip(
    selectedTab: XoverTab,
    onTabSelected: (XoverTab) -> Unit,
) {
    val tabs = XoverTab.entries
    val tabSpacing = 5.dp
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SoftLayerColor)
            .padding(5.dp),
    ) {
        val indicatorWidth = (maxWidth - tabSpacing * (tabs.size - 1)) / tabs.size
        val indicatorOffset by animateDpAsState(
            targetValue = (indicatorWidth + tabSpacing) * selectedIndex,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "tabIndicatorOffset",
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(indicatorWidth)
                .height(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceColor),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tabSpacing),
        ) {
            tabs.forEach { tab ->
                TabButton(
                    tab = tab,
                    selected = tab == selectedTab,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabButton(
    tab: XoverTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val foreground by animateColorAsState(if (selected) PrimaryText else SecondaryText, label = "tabForeground")
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val lift by animateDpAsState(
        targetValue = if (hovered && !selected) (-1).dp else 0.dp,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "tabButtonLift",
    )

    Box(
        modifier = modifier
            .height(38.dp)
            .offset(y = lift)
            .clip(RoundedCornerShape(12.dp))
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(tab.title, color = foreground, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun SetupTab(
    selectedRole: SetupRole,
    trackPath: String,
    advertisedHost: String,
    connectHost: String,
    port: String,
    onRoleSelected: (SetupRole) -> Unit,
    onTrackPathChange: (String) -> Unit,
    onAdvertisedHostChange: (String) -> Unit,
    onConnectHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onChooseFile: () -> Unit,
    onHost: () -> Unit,
    onConnect: () -> Unit,
) {
    Section("Mode") {
        SegmentedRole(selectedRole = selectedRole, onRoleSelected = onRoleSelected)
    }

    AnimatedVisibility(
        visible = selectedRole == SetupRole.HOST,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Section("Track") {
                OutlinedTextField(
                    value = trackPath,
                    onValueChange = onTrackPathChange,
                    label = { Text("Selected file") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    readOnly = false,
                )
                SoftButton("Browse audio file", onChooseFile, modifier = Modifier.fillMaxWidth())
            }

            Section("Host network") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = advertisedHost,
                        onValueChange = onAdvertisedHostChange,
                        label = { Text("Your VPN IP") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = onPortChange,
                        label = { Text("Port") },
                        modifier = Modifier.width(104.dp),
                        singleLine = true,
                    )
                }
                AddressPreview("Friend connects to", advertisedHost, port)
                PrimaryButton(
                    text = "Start hosting",
                    onClick = onHost,
                    enabled = trackPath.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    AnimatedVisibility(
        visible = selectedRole == SetupRole.CLIENT,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Section("Join host") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = connectHost,
                    onValueChange = onConnectHostChange,
                    label = { Text("Host VPN IP") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = onPortChange,
                    label = { Text("Port") },
                    modifier = Modifier.width(104.dp),
                    singleLine = true,
                )
            }
            AddressPreview("Opening", connectHost, port)
            PrimaryButton("Connect", onConnect, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
@Composable
private fun SegmentedRole(
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
            val foreground by animateColorAsState(if (selected) Color.White else SecondaryText, label = "roleForeground")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(background)
                    .clickable { onRoleSelected(role) },
                contentAlignment = Alignment.Center,
            ) {
                Text(role.title, color = foreground, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AddressPreview(label: String, host: String, port: String) {
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

@Composable
private fun PlayerTab(
    state: SessionViewState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onLocalVolumeChange: (Int) -> Unit,
) {
    val duration = max(1L, state.durationMillis())
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var volumePosition by remember { mutableFloatStateOf(state.localVolumePercent().toFloat()) }

    LaunchedEffect(state.positionMillis()) {
        sliderPosition = min(state.positionMillis(), duration).toFloat()
    }

    LaunchedEffect(state.localVolumePercent()) {
        volumePosition = state.localVolumePercent().toFloat()
    }

    Section("Now playing") {
        Text(
            text = state.trackName().ifBlank { "No track loaded" },
            fontWeight = FontWeight.SemiBold,
            color = PrimaryText,
        )
        Text(
            text = "${formatMillis(state.positionMillis())} / ${formatMillis(state.durationMillis())}",
            color = SecondaryText,
            style = MaterialTheme.typography.caption,
        )
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = { onSeek(sliderPosition.toLong()) },
            valueRange = 0f..duration.toFloat(),
            enabled = state.role() == DeviceRole.HOST && state.durationMillis() > 0L,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton(
                text = "Play",
                onClick = onPlay,
                enabled = state.role() == DeviceRole.HOST && state.playbackStatus() != PlaybackStatus.LOADING,
                modifier = Modifier.weight(1f),
            )
            SoftButton(
                text = "Pause",
                onClick = onPause,
                enabled = state.playbackStatus() == PlaybackStatus.PLAYING,
                modifier = Modifier.weight(1f),
            )
        }
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
private fun StatusTab(state: SessionViewState) {
    Section("Session state") {
        StatusRow("Role", state.role().name)
        StatusRow("Network", state.connectionStatus().name)
        StatusRow("Playback", state.playbackStatus().name)
        StatusRow("Clock offset", "${state.clockOffsetMillis()} ms")
    }

    Section("Latest event") {
        Text(state.message(), color = PrimaryText)
    }
}

@Composable
private fun MoreTab(
    state: SessionViewState,
    pinned: Boolean,
    opacity: Float,
    onPinnedChange: (Boolean) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onDisconnect: () -> Unit,
    onCollapse: () -> Unit,
    onMinimize: () -> Unit,
) {
    Section("Session") {
        StatusRow("Connection", state.connectionStatus().name)
        SoftButton(
            text = "Disconnect",
            onClick = onDisconnect,
            enabled = state.connectionStatus() != ConnectionStatus.DISCONNECTED,
            modifier = Modifier.fillMaxWidth(),
        )
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
            SoftButton("‹ Collapse", onCollapse, modifier = Modifier.weight(1f))
            SoftButton("− Minimize", onMinimize, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Section(
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
private fun StatusRow(label: String, value: String) {
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
private fun StatusDot(status: ConnectionStatus) {
    val color by animateColorAsState(statusColor(status), label = "statusDot")
    Box(
        modifier = Modifier
            .size(11.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun PrimaryButton(
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
private fun SoftButton(
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
private fun MotionButton(
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
private fun WindowControlButton(
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
    val shakePhase by animateFloatAsState(
        targetValue = if (hovered && shakeOnHover) 1f else 0f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "windowControlShakePhase",
    )
    val offsetX = if (shakeOnHover) (sin(shakePhase * Math.PI * 8.0) * 1.4).toFloat() else 0f

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
        Text(
            text = symbol,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}

private fun Modifier.windowDrag(window: AwtWindow): Modifier = pointerInput(window) {
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

private val XoverFontFamily: FontFamily = FontFamily(
    DesktopFont("fonts/Xover-text.ttf", FontWeight.Normal),
    DesktopFont("fonts/Xover-text.ttf", FontWeight.Medium),
    DesktopFont("fonts/Xover-text.ttf", FontWeight.SemiBold),
    DesktopFont("fonts/Xover-text.ttf", FontWeight.Bold),
)

private fun chooseFile(): File? {
    val dialog = FileDialog(null as java.awt.Frame?, "Choose audio file", FileDialog.LOAD)
    dialog.isVisible = true
    val file = dialog.file ?: return null
    return File(dialog.directory, file)
}

private fun statusTitle(state: SessionViewState): String = when (state.connectionStatus()) {
    ConnectionStatus.DISCONNECTED -> "ready to connect"
    ConnectionStatus.HOSTING -> "hosting on VPN"
    ConnectionStatus.CONNECTING -> "connecting"
    ConnectionStatus.CONNECTED -> "synced session"
    ConnectionStatus.ERROR -> "needs attention"
}

private fun statusColor(status: ConnectionStatus): Color = when (status) {
    ConnectionStatus.CONNECTED -> AccentColor
    ConnectionStatus.ERROR -> DangerColor
    ConnectionStatus.HOSTING,
    ConnectionStatus.CONNECTING -> WarmColor
    ConnectionStatus.DISCONNECTED -> SecondaryText
}

private fun formatMillis(value: Long): String {
    val safeValue = max(0L, value)
    val totalSeconds = safeValue / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

private fun xoverColors() = lightColors(
    primary = AccentColor,
    secondary = WarmColor,
    background = Color.Transparent,
    surface = SurfaceColor,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = PrimaryText,
    onSurface = PrimaryText,
)

private fun xoverTypography() = Typography(
    defaultFontFamily = XoverFontFamily,
    h1 = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Bold, fontSize = 96.sp),
    h2 = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Bold, fontSize = 60.sp),
    h3 = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Bold, fontSize = 48.sp),
    h4 = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp),
    h5 = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    h6 = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    subtitle1 = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    subtitle2 = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    body1 = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    body2 = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    button = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    caption = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    overline = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp),
)

private enum class XoverTab(val title: String) {
    SETUP("Setup"),
    PLAYER("Player"),
    STATUS("Status"),
    MORE("More"),
}

private enum class SetupRole(val title: String) {
    HOST("Host"),
    CLIENT("Client"),
}

private const val DefaultPort = 47321

private val ExpandedSize = DpSize(430.dp, 720.dp)
private val CollapsedSize = DpSize(84.dp, 212.dp)

private val SurfaceColor = Color(0xFFFFFFFF)
private val SoftLayerColor = Color(0xFFF2F5F4)
private val BorderColor = Color(0xFFDDE4E1)
private val PrimaryText = Color(0xFF18201D)
private val SecondaryText = Color(0xFF6E7A75)
private val AccentColor = Color(0xFF45A996)
private val WarmColor = Color(0xFFE1AA45)
private val DangerColor = Color(0xFFD55D63)
private val ControlGreen = Color(0xFF42C66B)
private val ControlYellow = Color(0xFFE8B84F)
private val ControlRed = Color(0xFFE85C5C)
