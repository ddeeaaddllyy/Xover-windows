package com.xover.music.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.xover.music.application.common.diagnostics.ErrorEventSource
import com.xover.music.application.common.diagnostics.ErrorReporter
import com.xover.music.application.session.ListeningSessionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.asCoroutineDispatcher
import java.awt.Frame
import java.util.concurrent.Executors

class DesktopApplication(
    private val sessionService: ListeningSessionService,
    private val errorEvents: ErrorEventSource,
    private val errorReporter: ErrorReporter,
) {
    fun start() = application {
        val settingsStore = remember { UiSettingsStore() }
        val loadedSettings = remember { runCatching { settingsStore.load() } }
        var settings by remember { mutableStateOf(loadedSettings.getOrDefault(UiSettings())) }
        var collapsed by remember { mutableStateOf(false) }
        var selectedTab by remember { mutableStateOf(XoverTab.SETUP) }
        var selectedRole by remember { mutableStateOf(SetupRole.HOST) }
        var closing by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val sessionDispatcher = remember {
            Executors.newSingleThreadExecutor { task ->
                Thread(task, "xover-ui-actions").apply { isDaemon = true }
            }.asCoroutineDispatcher()
        }
        DisposableEffect(sessionDispatcher) {
            onDispose { sessionDispatcher.close() }
        }
        val perform: (String, () -> Unit) -> Unit = { operation, action ->
            if (!closing) scope.launch(sessionDispatcher) { reportUiFailure(errorReporter, operation, action) }
        }
        val latestError = rememberLatestError(errorEvents)
        LaunchedEffect(Unit) {
            loadedSettings.exceptionOrNull()?.let { errorReporter.report("Load preferences", it) }
        }
        LaunchedEffect(settings) {
            delay(350)
            withContext(Dispatchers.IO) {
                runCatching { settingsStore.save(settings) }
                    .onFailure { errorReporter.report("Save preferences", it) }
            }
        }
        val closeApplication: () -> Unit = {
            if (!closing) {
                closing = true
                val finalSettings = settings
                scope.launch {
                    withContext(sessionDispatcher) {
                        runCatching { settingsStore.save(finalSettings) }
                            .onFailure { errorReporter.report("Save preferences", it) }
                        runCatching { sessionService.close() }
                            .onFailure { errorReporter.report("Close session", it) }
                    }
                    exitApplication()
                }
            }
        }
        val appIcon = appIconPainter()
        val windowState = rememberWindowState(
            position = WindowPosition(24.dp, 92.dp),
            size = ExpandedSize,
        )
        val targetSize = if (collapsed) CollapsedSize else ExpandedSize
        val animatedWidth by animateDpAsState(
            targetValue = targetSize.width,
            animationSpec = gentleMotion(),
            label = "windowWidth",
        )
        val animatedHeight by animateDpAsState(
            targetValue = targetSize.height,
            animationSpec = gentleMotion(),
            label = "windowHeight",
        )

        SideEffect {
            windowState.size = DpSize(animatedWidth, animatedHeight)
        }

        Window(
            onCloseRequest = closeApplication,
            title = "Xover",
            state = windowState,
            undecorated = true,
            transparent = true,
            resizable = false,
            alwaysOnTop = settings.pinned,
            icon = appIcon,
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
                            opacity = settings.opacity,
                            awtWindow = window,
                            onExpand = { collapsed = false },
                            onMinimize = minimizeWindow,
                        )
                    } else {
                        XoverPanel(
                            state = state.value,
                            awtWindow = window,
                            settings = settings,
                            onSettingsChange = { settings = it },
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            selectedRole = selectedRole,
                            onRoleSelected = { selectedRole = it },
                            onCollapse = { collapsed = true },
                            onMinimize = minimizeWindow,
                            onClose = closeApplication,
                            onHost = { advertisedHost, port ->
                                perform("Start host") {
                                    sessionService.startHost(advertisedHost, port)
                                }
                            },
                            onConnect = { host, port ->
                                perform("Connect to host") {
                                    sessionService.connectToHost(host, port)
                                }
                            },
                            onAddTrackUrl = { sourceUrl ->
                                perform("Add track URL") {
                                    sessionService.addTrackUrl(sourceUrl)
                                }
                            },
                            onSelectTrack = { trackIndex ->
                                perform("Select track") {
                                    sessionService.selectTrack(trackIndex)
                                }
                            },
                            onRemoveTrack = { trackIndex ->
                                perform("Remove track") {
                                    sessionService.removeTrackAt(trackIndex)
                                }
                            },
                            onMoveTrack = { fromIndex, toIndex ->
                                perform("Move track") {
                                    sessionService.moveTrack(fromIndex, toIndex)
                                }
                            },
                            onPlay = {
                                perform("Play") {
                                    sessionService.play()
                                }
                            },
                            onPause = {
                                perform("Pause") {
                                    sessionService.pause()
                                }
                            },
                            onSeek = { positionMillis ->
                                perform("Seek") {
                                    sessionService.seek(positionMillis)
                                }
                            },
                            onLocalVolumeChange = { volumePercent ->
                                perform("Change local volume") {
                                    sessionService.setLocalVolumePercent(volumePercent)
                                }
                            },
                            onDisconnect = {
                                perform("Disconnect") {
                                    sessionService.disconnect()
                                }
                            },
                            onDisconnectPeer = { peerId ->
                                perform("Disconnect listener") {
                                    sessionService.disconnectPeer(peerId)
                                }
                            },
                        )
                    }
                }
            }
        }

        latestError.value?.let { event ->
            ErrorTraceWindow(
                event = event,
                onClose = { latestError.value = null },
            )
        }
    }
}
