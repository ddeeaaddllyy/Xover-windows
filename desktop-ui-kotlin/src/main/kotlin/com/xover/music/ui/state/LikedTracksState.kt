package com.xover.music.ui

import androidx.compose.runtime.*
import com.xover.music.application.common.diagnostics.ErrorReporter
import com.xover.music.application.library.LikedTracksService
import com.xover.music.domain.LikedTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Stable
internal class LikedTracksState(
    private val service: LikedTracksService,
    private val reporter: ErrorReporter,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    var tracks by mutableStateOf<List<LikedTrack>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    fun refresh() = update("Load liked tracks") {}

    fun toggle(sourceUrl: String, title: String) {
        if (!ready) return
        val liked = tracks.any { it.sourceUrl() == sourceUrl }
        update("Update liked tracks") {
            if (liked) service.unlike(sourceUrl) else service.like(sourceUrl, title)
        }
    }

    private fun update(operation: String, mutation: () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                tracks = withContext(dispatcher) {
                    mutation()
                    service.list()
                }
                ready = true
                failed = false
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                failed = true
                reporter.report(operation, ex)
            } finally {
                busy = false
            }
        }
    }
}
