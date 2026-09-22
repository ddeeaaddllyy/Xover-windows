package com.xover.music.infrastructure

import com.xover.music.application.network.HostStartupConfig
import com.xover.music.application.network.MessageType
import com.xover.music.application.network.error.NetworkTransportException
import com.xover.music.application.network.error.RemoteProtocolException
import com.xover.music.application.common.error.XoverException
import com.xover.music.application.network.PeerAddress
import com.xover.music.application.network.PeerMessage
import com.xover.music.application.network.PeerTransportListener
import com.xover.music.application.network.PeerTransportPort
import com.xover.music.domain.PlaylistTrack
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.plugins.origin
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class KtorPeerTransport : PeerTransportPort {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val listenerRef = AtomicReference<PeerTransportListener>(object : PeerTransportListener {})
    private val serverSessions = ConcurrentHashMap.newKeySet<DefaultWebSocketServerSession>()
    private val serverPeerIds = ConcurrentHashMap<DefaultWebSocketServerSession, String>()
    private val nextPeerNumber = AtomicInteger(1)

    @Volatile
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    @Volatile
    private var client: HttpClient? = null

    @Volatile
    private var clientSession: DefaultClientWebSocketSession? = null

    override fun setListener(listener: PeerTransportListener?) {
        listenerRef.set(listener ?: object : PeerTransportListener {})
    }

    override fun startHost(config: HostStartupConfig) {
        closeServer()

        try {
            server = embeddedServer(Netty, host = config.bindHost(), port = config.port()) {
                install(ServerWebSockets)
                routing {
                    get("/health") {
                        call.respondText("Xover host is alive")
                    }
                    webSocket("/sync") {
                        val peerNumber = nextPeerNumber.getAndIncrement()
                        val peerId = "client-$peerNumber (${call.request.origin.remoteHost})"
                        serverSessions.add(this)
                        serverPeerIds[this] = peerId
                        listenerRef.get().onPeerConnected(peerId)
                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    listenerRef.get().onMessage(decodeFrame(frame.readText()))
                                }
                            }
                        } catch (ex: RuntimeException) {
                            listenerRef.get().onTransportError("Host WebSocket failed", protocolFailure("Host WebSocket failed", ex))
                        } finally {
                            serverSessions.remove(this)
                            serverPeerIds.remove(this)
                            listenerRef.get().onPeerDisconnected(peerId)
                        }
                    }
                }
            }.start(wait = false)

            listenerRef.get().onTransportReady("Host server started on ${config.bindHost()}:${config.port()}")
        } catch (ex: RuntimeException) {
            throw NetworkTransportException.hostStartupFailed(config, ex)
        }
    }

    override fun connect(address: PeerAddress) {
        closeClient()

        val nextClient = HttpClient(CIO) {
            install(ClientWebSockets)
        }
        client = nextClient

        scope.launch {
            try {
                nextClient.webSocket(
                    method = HttpMethod.Get,
                    host = address.host(),
                    port = address.port(),
                    path = "/sync",
                ) {
                    clientSession = this
                    listenerRef.get().onPeerConnected("${address.host()}:${address.port()}")
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                listenerRef.get().onMessage(decodeFrame(frame.readText()))
                            }
                        }
                    } catch (ex: RuntimeException) {
                        listenerRef.get().onTransportError("Client WebSocket failed", protocolFailure("Client WebSocket failed", ex))
                    } finally {
                        clientSession = null
                        listenerRef.get().onPeerDisconnected("${address.host()}:${address.port()}")
                    }
                }
            } catch (ex: RuntimeException) {
                listenerRef.get().onTransportError(
                    "Could not connect to ${address.host()}:${address.port()}",
                    NetworkTransportException.connectionFailed(address, ex),
                )
            }
        }
    }

    override fun send(message: PeerMessage) {
        val payload = encodeMessage(message) ?: return

        clientSession?.let { session ->
            sendFrame(session, payload, "host")
            return
        }

        serverSessions.forEach { session ->
            sendFrame(session, payload, serverPeerIds[session] ?: "peer")
        }
    }

    override fun broadcast(message: PeerMessage) {
        val payload = encodeMessage(message) ?: return
        serverSessions.forEach { session ->
            sendFrame(session, payload, serverPeerIds[session] ?: "peer")
        }
    }

    override fun disconnectPeer(peerId: String) {
        serverPeerIds.entries
            .firstOrNull { (_, candidatePeerId) -> candidatePeerId == peerId }
            ?.key
            ?.let { session ->
                serverSessions.remove(session)
                serverPeerIds.remove(session)
                scope.launch {
                    session.close()
                }
            }
    }

    override fun disconnect() {
        closeClient()
        closeServer()
    }

    override fun close() {
        disconnect()
        scope.cancel()
    }

    private fun closeClient() {
        val session = clientSession
        clientSession = null
        if (session != null) {
            scope.launch {
                session.close()
            }
        }
        client?.close()
        client = null
    }

    private fun closeServer() {
        serverSessions.forEach { session ->
            scope.launch {
                session.close()
            }
        }
        serverSessions.clear()
        serverPeerIds.clear()
        server?.stop()
        server = null
    }

    private fun encode(message: PeerMessage): String =
        json.encodeToString(PeerMessageDto.serializer(), message.toDto())

    private fun decode(payload: String): PeerMessage =
        json.decodeFromString(PeerMessageDto.serializer(), payload).toDomain()

    private fun encodeMessage(message: PeerMessage): String? =
        try {
            encode(message)
        } catch (ex: RuntimeException) {
            listenerRef.get().onTransportError("Could not encode sync message", RemoteProtocolException("Could not encode sync message", ex))
            null
        }

    private fun decodeFrame(payload: String): PeerMessage =
        try {
            decode(payload)
        } catch (ex: RuntimeException) {
            throw protocolFailure("Could not decode sync message", ex)
        }

    private fun protocolFailure(message: String, failure: RuntimeException): RuntimeException =
        if (failure is XoverException) {
            failure
        } else {
            RemoteProtocolException(message, failure)
        }

    private fun sendFrame(session: io.ktor.websocket.WebSocketSession, payload: String, target: String) {
        scope.launch {
            try {
                session.send(Frame.Text(payload))
            } catch (ex: RuntimeException) {
                listenerRef.get().onTransportError("Could not send sync message", NetworkTransportException.sendFailed(target, ex))
            }
        }
    }

    private fun PeerMessage.toDto(): PeerMessageDto = PeerMessageDto(
        type = type().name,
        nonce = nonce(),
        trackName = trackName(),
        mediaUri = mediaUri(),
        positionMillis = positionMillis(),
        startAtHostMillis = startAtHostMillis(),
        clientSentAtMillis = clientSentAtMillis(),
        hostReceivedAtMillis = hostReceivedAtMillis(),
        hostSentAtMillis = hostSentAtMillis(),
        playlist = playlist().map { it.toDto() },
        currentTrackIndex = currentTrackIndex(),
    )

    private fun PeerMessageDto.toDomain(): PeerMessage = PeerMessage(
        MessageType.valueOf(type),
        nonce,
        trackName,
        mediaUri,
        positionMillis,
        startAtHostMillis,
        clientSentAtMillis,
        hostReceivedAtMillis,
        hostSentAtMillis,
        playlist.map { it.toDomain() },
        currentTrackIndex,
    )

    private fun PlaylistTrack.toDto(): PlaylistTrackDto = PlaylistTrackDto(
        id = id(),
        title = title(),
        sourceUrl = sourceUrl(),
    )

    private fun PlaylistTrackDto.toDomain(): PlaylistTrack = PlaylistTrack(
        id,
        title,
        sourceUrl,
    )
}

@Serializable
private data class PeerMessageDto(
    val type: String,
    val nonce: String = "",
    val trackName: String = "",
    val mediaUri: String = "",
    val positionMillis: Long = 0L,
    val startAtHostMillis: Long = 0L,
    val clientSentAtMillis: Long = 0L,
    val hostReceivedAtMillis: Long = 0L,
    val hostSentAtMillis: Long = 0L,
    val playlist: List<PlaylistTrackDto> = emptyList(),
    val currentTrackIndex: Int = -1,
)

@Serializable
private data class PlaylistTrackDto(
    val id: String = "",
    val title: String = "",
    val sourceUrl: String = "",
)
