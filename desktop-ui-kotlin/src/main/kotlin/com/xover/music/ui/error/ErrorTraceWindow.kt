package com.xover.music.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.xover.music.application.common.diagnostics.ErrorEvent
import com.xover.music.domain.ConnectionStatus
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.Window as AwtWindow

@Composable
internal fun ErrorTraceWindow(
    event: ErrorEvent,
    onClose: () -> Unit,
) {
    val errorWindowState = rememberWindowState(
        position = WindowPosition(480.dp, 92.dp),
        size = ErrorWindowSize,
    )

    Window(
        onCloseRequest = onClose,
        title = "Xover Error",
        state = errorWindowState,
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        icon = appIconPainter(),
    ) {
        val minimizeWindow = {
            window.extendedState = Frame.ICONIFIED
        }

        MaterialTheme(
            colors = xoverColors(),
            typography = xoverTypography(),
        ) {
            ErrorPanel(
                event = event,
                awtWindow = window,
                onCopy = { copyErrorTrace(event) },
                onMinimize = minimizeWindow,
                onClose = onClose,
            )
        }
    }
}

@Composable
internal fun ErrorPanel(
    event: ErrorEvent,
    awtWindow: AwtWindow,
    onCopy: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
) {
    FloatingSurface(opacity = 0.96f) {
        ErrorTopBar(
            event = event,
            awtWindow = awtWindow,
            onMinimize = onMinimize,
            onClose = onClose,
        )

        Divider(color = BorderColor.copy(alpha = 0.7f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("Error") {
                ErrorDetailLine("Code", event.code().name)
                ErrorDetailLine("Title", event.title())
                ErrorDetailLine("Message", event.message())
                ErrorDetailLine("Time", formatErrorTimestamp(event))
                ErrorDetailLine("Exception", event.exceptionClass())
            }

            if (event.context().isNotEmpty()) {
                Section("Context") {
                    event.contextEntries().forEach { entry ->
                        ErrorDetailLine(entry.key, entry.value)
                    }
                }
            }

            Section("Trace") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoftLayerColor.copy(alpha = 0.86f))
                        .border(
                            BorderStroke(
                                1.dp,
                                BorderColor.copy(alpha = 0.7f)
                            ), RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SelectionContainer {
                        Text(
                            text = event.trace(),
                            color = PrimaryText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SoftButton("Copy trace", onCopy, modifier = Modifier.weight(1f))
            PrimaryButton("Close", onClose, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun ErrorTopBar(
    event: ErrorEvent,
    awtWindow: AwtWindow,
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
                    .background(DangerColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                StatusDot(ConnectionStatus.ERROR)
            }
            Column {
                Text("Xover", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                Text(event.title(), color = SecondaryText, style = MaterialTheme.typography.caption)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WindowControlButton(
                "-",
                ControlYellow,
                onMinimize,
                shakeOnHover = true
            )
            WindowControlButton(
                "x",
                ControlRed,
                onClose,
                shakeOnHover = true
            )
        }
    }
}

@Composable
internal fun ErrorDetailLine(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = SecondaryText, style = MaterialTheme.typography.caption)
        SelectionContainer {
            Text(
                text = value.ifBlank { "-" },
                color = PrimaryText,
                fontWeight = FontWeight.Medium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


internal fun formatErrorTimestamp(event: ErrorEvent): String =
    ErrorTimestampFormatter.format(event.occurredAt())

internal fun copyErrorTrace(event: ErrorEvent) {
    val selection = StringSelection(errorClipboardText(event))
    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
}

internal fun errorClipboardText(event: ErrorEvent): String = buildString {
    appendLine("Xover error report")
    appendLine("id=${event.id()}")
    appendLine("time=${formatErrorTimestamp(event)}")
    appendLine("code=${event.code().name}")
    appendLine("title=${event.title()}")
    appendLine("message=${event.message()}")
    appendLine("exception=${event.exceptionClass()}")
    if (event.context().isNotEmpty()) {
        appendLine()
        appendLine("context:")
        event.contextEntries().forEach { entry ->
            appendLine("${entry.key}=${entry.value}")
        }
    }
    appendLine()
    appendLine(event.trace())
}
