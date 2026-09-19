package com.xover.music.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.xover.music.application.session.ListeningSessionService
import com.xover.music.application.session.SessionObserver
import com.xover.music.domain.ConnectionStatus
import com.xover.music.domain.DeviceRole
import com.xover.music.domain.PlaybackStatus
import com.xover.music.domain.SessionViewState
import java.awt.EventQueue
import java.awt.FileDialog
import java.io.File
import java.nio.file.Paths
import kotlin.math.max
import kotlin.math.min

class DesktopApplication(
    private val sessionService: ListeningSessionService,
) {
    fun start() = application {
        Window(
            onCloseRequest = {
                sessionService.close()
                exitApplication()
            },
            title = "Xover",
        ) {
            val state = rememberSessionState(sessionService)

            MaterialTheme(colors = xoverColors()) {
                XoverScreen(
                    state = state.value,
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
                )
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
private fun XoverScreen(
    state: SessionViewState,
    onHost: (String, String, Int) -> Unit,
    onConnect: (String, Int) -> Unit,
    onChooseFile: () -> String,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    var trackPath by remember { mutableStateOf("") }
    var advertisedHost by remember { mutableStateOf("127.0.0.1") }
    var connectHost by remember { mutableStateOf("127.0.0.1") }
    var port by remember { mutableStateOf("47321") }

    Surface(color = Color(0xFF171819), modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Header(state)

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                HostPanel(
                    modifier = Modifier.weight(1f),
                    trackPath = trackPath,
                    advertisedHost = advertisedHost,
                    port = port,
                    onTrackPathChange = { trackPath = it },
                    onAdvertisedHostChange = { advertisedHost = it },
                    onPortChange = { port = it },
                    onChooseFile = {
                        val selected = onChooseFile()
                        if (selected.isNotBlank()) {
                            trackPath = selected
                        }
                    },
                    onHost = {
                        onHost(trackPath, advertisedHost, port.toIntOrNull() ?: 47321)
                    },
                )

                ClientPanel(
                    modifier = Modifier.weight(1f),
                    connectHost = connectHost,
                    port = port,
                    onConnectHostChange = { connectHost = it },
                    onPortChange = { port = it },
                    onConnect = {
                        onConnect(connectHost, port.toIntOrNull() ?: 47321)
                    },
                )
            }

            PlaybackPanel(
                state = state,
                onPlay = onPlay,
                onPause = onPause,
                onSeek = onSeek,
            )

            StatusPanel(state)
        }
    }
}

@Composable
private fun Header(state: SessionViewState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Xover", style = MaterialTheme.typography.h4, fontWeight = FontWeight.Bold)
            Text("Peer-to-peer music sync for VPN friends", color = Color(0xFFB9C0BA))
        }
        StatusPill(state.connectionStatus().name.lowercase())
    }
}

@Composable
private fun HostPanel(
    modifier: Modifier,
    trackPath: String,
    advertisedHost: String,
    port: String,
    onTrackPathChange: (String) -> Unit,
    onAdvertisedHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onChooseFile: () -> Unit,
    onHost: () -> Unit,
) {
    Panel(modifier = modifier) {
        Text("Host", style = MaterialTheme.typography.h6, fontWeight = FontWeight.SemiBold)
        Text("Share a local track through your VPN address.", color = Color(0xFFAAB2AD))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = trackPath,
            onValueChange = onTrackPathChange,
            label = { Text("Track file") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
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
                modifier = Modifier.width(120.dp),
                singleLine = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onChooseFile) {
                Text("Choose")
            }
            Button(onClick = onHost, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3DB9A3))) {
                Text("Start Host", color = Color(0xFF0D1412))
            }
        }
    }
}

@Composable
private fun ClientPanel(
    modifier: Modifier,
    connectHost: String,
    port: String,
    onConnectHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    Panel(modifier = modifier) {
        Text("Client", style = MaterialTheme.typography.h6, fontWeight = FontWeight.SemiBold)
        Text("Connect to the host's Hamachi, Radmin VPN, or Porthole IP.", color = Color(0xFFAAB2AD))
        Spacer(Modifier.height(8.dp))
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
                modifier = Modifier.width(120.dp),
                singleLine = true,
            )
        }
        Button(onClick = onConnect, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFE2B45D))) {
            Text("Connect", color = Color(0xFF1A1204))
        }
    }
}

@Composable
private fun PlaybackPanel(
    state: SessionViewState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val duration = max(1L, state.durationMillis())
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.positionMillis()) {
        sliderPosition = min(state.positionMillis(), duration).toFloat()
    }

    Panel(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Text(state.trackName().ifBlank { "No track loaded" }, style = MaterialTheme.typography.h6)
                Text("${formatMillis(state.positionMillis())} / ${formatMillis(state.durationMillis())}", color = Color(0xFFAAB2AD))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPlay,
                    enabled = state.role() == DeviceRole.HOST && state.playbackStatus() != PlaybackStatus.LOADING,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3DB9A3)),
                ) {
                    Text("Play", color = Color(0xFF0D1412))
                }
                OutlinedButton(onClick = onPause, enabled = state.playbackStatus() == PlaybackStatus.PLAYING) {
                    Text("Pause")
                }
            }
        }
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = { onSeek(sliderPosition.toLong()) },
            valueRange = 0f..duration.toFloat(),
            enabled = state.role() == DeviceRole.HOST && state.durationMillis() > 0L,
        )
    }
}

@Composable
private fun StatusPanel(state: SessionViewState) {
    Panel(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
            StatusValue("Role", state.role().name)
            StatusValue("Network", state.connectionStatus().name)
            StatusValue("Playback", state.playbackStatus().name)
            StatusValue("Clock offset", "${state.clockOffsetMillis()} ms")
        }
        Spacer(Modifier.height(8.dp))
        Text(state.message(), color = Color(0xFFD4D9D3))
    }
}

@Composable
private fun StatusValue(label: String, value: String) {
    Column {
        Text(label, color = Color(0xFF8E9891), style = MaterialTheme.typography.caption)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusPill(text: String) {
    val color = when (text) {
        ConnectionStatus.CONNECTED.name.lowercase() -> Color(0xFF3DB9A3)
        ConnectionStatus.ERROR.name.lowercase() -> Color(0xFFE56E6E)
        else -> Color(0xFFE2B45D)
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        backgroundColor = Color(0xFF222426),
        contentColor = Color(0xFFE9EEE8),
        border = BorderStroke(1.dp, Color(0xFF333738)),
        shape = RoundedCornerShape(8.dp),
        elevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

private fun chooseFile(): File? {
    val dialog = FileDialog(null as java.awt.Frame?, "Choose audio file", FileDialog.LOAD)
    dialog.isVisible = true
    val file = dialog.file ?: return null
    return File(dialog.directory, file)
}

private fun formatMillis(value: Long): String {
    val safeValue = max(0L, value)
    val totalSeconds = safeValue / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

private fun xoverColors() = darkColors(
    primary = Color(0xFF3DB9A3),
    secondary = Color(0xFFE2B45D),
    background = Color(0xFF171819),
    surface = Color(0xFF222426),
    onPrimary = Color(0xFF0D1412),
    onSecondary = Color(0xFF1A1204),
    onBackground = Color(0xFFE9EEE8),
    onSurface = Color(0xFFE9EEE8),
)
