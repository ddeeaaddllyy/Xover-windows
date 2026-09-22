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
import java.awt.Frame

class DesktopApplication(
    private val sessionService: ListeningSessionService,
    private val errorEvents: ErrorEventSource,
    private val errorReporter: ErrorReporter,
) {
    fun start() = application {
        var collapsed by remember { mutableStateOf(false) }
        var pinned by remember { mutableStateOf(false) }
        var opacity by remember { mutableFloatStateOf(0.94f) }
        val latestError = rememberLatestError(errorEvents)
        val appIcon = appIconPainter()
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
                            onHost = { advertisedHost, port ->
                                reportUiFailure(errorReporter, "Start host") {
                                    sessionService.startHost(advertisedHost, port)
                                }
                            },
                            onConnect = { host, port ->
                                reportUiFailure(errorReporter, "Connect to host") {
                                    sessionService.connectToHost(host, port)
                                }
                            },
                            onAddTrackUrl = { sourceUrl ->
                                reportUiFailure(errorReporter, "Add track URL") {
                                    sessionService.addTrackUrl(sourceUrl)
                                }
                            },
                            onSelectTrack = { trackIndex ->
                                reportUiFailure(errorReporter, "Select track") {
                                    sessionService.selectTrack(trackIndex)
                                }
                            },
                            onRemoveTrack = { trackIndex ->
                                reportUiFailure(errorReporter, "Remove track") {
                                    sessionService.removeTrackAt(trackIndex)
                                }
                            },
                            onMoveTrack = { fromIndex, toIndex ->
                                reportUiFailure(errorReporter, "Move track") {
                                    sessionService.moveTrack(fromIndex, toIndex)
                                }
                            },
                            onPlay = {
                                reportUiFailure(errorReporter, "Play") {
                                    sessionService.play()
                                }
                            },
                            onPause = {
                                reportUiFailure(errorReporter, "Pause") {
                                    sessionService.pause()
                                }
                            },
                            onSeek = { positionMillis ->
                                reportUiFailure(errorReporter, "Seek") {
                                    sessionService.seek(positionMillis)
                                }
                            },
                            onLocalVolumeChange = { volumePercent ->
                                reportUiFailure(errorReporter, "Change local volume") {
                                    sessionService.setLocalVolumePercent(volumePercent)
                                }
                            },
                            onDisconnect = {
                                reportUiFailure(errorReporter, "Disconnect") {
                                    sessionService.disconnect()
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
