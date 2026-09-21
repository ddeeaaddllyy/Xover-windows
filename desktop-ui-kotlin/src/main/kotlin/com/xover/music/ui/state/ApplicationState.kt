package com.xover.music.ui

import androidx.compose.runtime.*
import com.xover.music.application.error.ErrorEvent
import com.xover.music.application.error.ErrorEventListener
import com.xover.music.application.error.ErrorEventSource
import com.xover.music.application.error.ErrorReporter
import com.xover.music.application.session.ListeningSessionService
import com.xover.music.application.session.SessionObserver
import com.xover.music.domain.SessionViewState
import java.awt.EventQueue

@Composable
internal fun rememberSessionState(sessionService: ListeningSessionService): MutableState<SessionViewState> {
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
internal fun rememberLatestError(errorEvents: ErrorEventSource): MutableState<ErrorEvent?> {
    val latestError = remember { mutableStateOf<ErrorEvent?>(null) }
    DisposableEffect(errorEvents) {
        val listener = ErrorEventListener { event ->
            EventQueue.invokeLater {
                latestError.value = event
            }
        }
        errorEvents.addListener(listener)
        onDispose {
            errorEvents.removeListener(listener)
        }
    }
    return latestError
}

internal fun reportUiFailure(
    errorReporter: ErrorReporter,
    operation: String,
    action: () -> Unit,
) {
    try {
        action()
    } catch (ex: RuntimeException) {
        errorReporter.report(operation, ex)
    }
}
