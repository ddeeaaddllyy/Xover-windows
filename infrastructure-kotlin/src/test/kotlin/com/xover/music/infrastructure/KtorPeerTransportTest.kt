package com.xover.music.infrastructure

import com.xover.music.application.audio.AudioPlayerListener
import com.xover.music.application.audio.AudioPlayerPort
import com.xover.music.application.common.diagnostics.ErrorReporter
import com.xover.music.application.network.*
import com.xover.music.application.session.ListeningSessionService
import com.xover.music.application.sync.ClockSynchronizer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.ServerSocket
import java.net.URI
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@Timeout(30)
class KtorPeerTransportTest {
    @Test
    fun preservesCommandOrderAndAssociatesReadinessWithTheSender() {
        val host = KtorPeerTransport()
        val client = KtorPeerTransport()
        val connections = LinkedBlockingQueue<String>()
        val received = LinkedBlockingQueue<PeerMessage>()
        val replies = LinkedBlockingQueue<Pair<String, PeerMessage>>()
        val errors = CopyOnWriteArrayList<Throwable>()
        host.setListener(object : PeerTransportListener {
            override fun onPeerConnected(peerId: String) { connections.add(peerId) }
            override fun onMessage(peerId: String, message: PeerMessage) { replies.add(peerId to message) }
            override fun onTransportError(message: String, cause: Throwable) { errors.add(cause) }
        })
        client.setListener(object : PeerTransportListener {
            override fun onMessage(peerId: String, message: PeerMessage) { received.add(message) }
            override fun onTransportError(message: String, cause: Throwable) { errors.add(cause) }
        })
        try {
            val port = freePort()
            host.startHost(HostStartupConfig("127.0.0.1", "127.0.0.1", port))
            client.connect(PeerAddress("127.0.0.1", port))
            val peerId = requireNotNull(connections.poll(5, TimeUnit.SECONDS))
            val messages = (0L until 40).map { index ->
                if (index % 2 == 0L) PeerMessage.playAt("load-1", index, 10_000)
                else PeerMessage.pause("load-1", index)
            }
            messages.forEach { host.sendToPeer(peerId, it) }
            messages.forEach { assertEquals(it, received.poll(5, TimeUnit.SECONDS)) }
            val ready = PeerMessage.trackReady("load-1")
            client.send(ready)
            assertEquals(peerId to ready, replies.poll(5, TimeUnit.SECONDS))
            assertTrue(errors.isEmpty(), errors.toString())
        } finally {
            client.close()
            host.close()
        }
    }

    @Test
    fun realHostWaitsForTwoClientsWithDifferentLoadTimes() {
        val errors = CopyOnWriteArrayList<Throwable>()
        fun service(audio: TestAudio) = ListeningSessionService(
            audio, KtorPeerTransport(), System::currentTimeMillis,
            Executors.newScheduledThreadPool(2), ClockSynchronizer(),
            ErrorReporter { _, cause -> errors.add(cause) },
        )
        val hostAudio = TestAudio()
        val fastAudio = TestAudio()
        val slowAudio = TestAudio()
        val host = service(hostAudio)
        val fast = service(fastAudio)
        val slow = service(slowAudio)
        try {
            val port = freePort()
            host.addTrackUrl("https://example.com/music.mp3")
            host.startHost("127.0.0.1", port)
            val bothConnected = CountDownLatch(1)
            host.addObserver { if (it.connectedPeerIds().size == 2) bothConnected.countDown() }
            fast.connectToHost("127.0.0.1", port)
            slow.connectToHost("127.0.0.1", port)
            assertTrue(bothConnected.await(5, TimeUnit.SECONDS))
            assertTrue(fastAudio.loaded.await(5, TimeUnit.SECONDS))
            assertTrue(slowAudio.loaded.await(5, TimeUnit.SECONDS))
            hostAudio.ready()
            fastAudio.ready()
            host.play()
            assertFalse(hostAudio.played.await(900, TimeUnit.MILLISECONDS))
            assertEquals(1L, fastAudio.played.count)
            slowAudio.ready()
            assertTrue(hostAudio.played.await(5, TimeUnit.SECONDS))
            assertTrue(fastAudio.played.await(5, TimeUnit.SECONDS))
            assertTrue(slowAudio.played.await(5, TimeUnit.SECONDS))
            assertTrue(errors.isEmpty(), errors.toString())
        } finally {
            slow.close()
            fast.close()
            host.close()
        }
    }

    @Test
    fun hostAndClientKeepPlayingAcrossTrackEndAndPlaylistWrap() {
        val errors = CopyOnWriteArrayList<Throwable>()
        fun service(audio: TestAudio) = ListeningSessionService(
            audio, KtorPeerTransport(), System::currentTimeMillis,
            Executors.newScheduledThreadPool(2), ClockSynchronizer(),
            ErrorReporter { _, cause -> errors.add(cause) },
        )
        val hostAudio = TestAudio()
        val clientAudio = TestAudio()
        val host = service(hostAudio)
        val client = service(clientAudio)
        try {
            host.addTrackUrl("https://example.com/first.mp3")
            host.addTrackUrl("https://example.com/second.mp3")
            val port = freePort()
            host.startHost("127.0.0.1", port)
            client.connectToHost("127.0.0.1", port)
            assertNotNull(hostAudio.loadEvents.poll(5, TimeUnit.SECONDS))
            assertNotNull(clientAudio.loadEvents.poll(5, TimeUnit.SECONDS))
            hostAudio.ready()
            clientAudio.ready()
            host.play()
            assertNotNull(hostAudio.playEvents.poll(5, TimeUnit.SECONDS))
            assertNotNull(clientAudio.playEvents.poll(5, TimeUnit.SECONDS))

            host.pause()
            assertTrue(awaitState(client) { it.playbackStatus() == com.xover.music.domain.PlaybackStatus.PAUSED })
            host.seek(1_000)
            assertTrue(awaitState(client) { it.positionMillis() == 1_000L })
            host.play()
            assertNotNull(hostAudio.playEvents.poll(5, TimeUnit.SECONDS))
            assertNotNull(clientAudio.playEvents.poll(5, TimeUnit.SECONDS))

            hostAudio.end()
            assertNotNull(hostAudio.loadEvents.poll(5, TimeUnit.SECONDS))
            assertNotNull(clientAudio.loadEvents.poll(5, TimeUnit.SECONDS))
            assertEquals(1, host.currentState().currentTrackIndex())
            assertEquals(1, client.currentState().currentTrackIndex())
            hostAudio.ready()
            clientAudio.ready()
            assertNotNull(hostAudio.playEvents.poll(5, TimeUnit.SECONDS))
            assertNotNull(clientAudio.playEvents.poll(5, TimeUnit.SECONDS))

            hostAudio.end()
            assertNotNull(hostAudio.loadEvents.poll(5, TimeUnit.SECONDS))
            assertNotNull(clientAudio.loadEvents.poll(5, TimeUnit.SECONDS))
            assertEquals(0, host.currentState().currentTrackIndex())
            assertEquals(0, client.currentState().currentTrackIndex())
            hostAudio.ready()
            clientAudio.ready()
            assertNotNull(hostAudio.playEvents.poll(5, TimeUnit.SECONDS))
            assertNotNull(clientAudio.playEvents.poll(5, TimeUnit.SECONDS))
            assertTrue(errors.isEmpty(), errors.toString())
        } finally {
            client.close()
            host.close()
        }
    }

    private fun awaitState(service: ListeningSessionService, matches: (com.xover.music.domain.SessionViewState) -> Boolean): Boolean {
        val reached = CountDownLatch(1)
        service.addObserver { if (matches(it)) reached.countDown() }
        return reached.await(5, TimeUnit.SECONDS)
    }

    private fun freePort() = ServerSocket(0).use { it.localPort }

    private class TestAudio : AudioPlayerPort {
        lateinit var audioListener: AudioPlayerListener
        lateinit var loadId: String
        val loaded = CountDownLatch(1)
        val played = CountDownLatch(1)
        val loadEvents = LinkedBlockingQueue<String>()
        val playEvents = LinkedBlockingQueue<Unit>()
        override fun setListener(listener: AudioPlayerListener) { this.audioListener = listener }
        override fun load(uri: URI, loadId: String) { this.loadId = loadId; loaded.countDown(); loadEvents.add(loadId) }
        fun ready() { audioListener.onReady(loadId, Duration.ofMinutes(2)) }
        fun end() { audioListener.onEnded(loadId) }
        override fun play() { played.countDown(); playEvents.add(Unit) }
        override fun pause() = Unit
        override fun stop() = Unit
        override fun seek(position: Duration) = Unit
        override fun setVolume(volume: Double) = Unit
        override fun volume() = 1.0
        override fun currentPosition(): Duration = Duration.ZERO
        override fun duration(): Duration = Duration.ofMinutes(2)
        override fun close() = Unit
    }
}
