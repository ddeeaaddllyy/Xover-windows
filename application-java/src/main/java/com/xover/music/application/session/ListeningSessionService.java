package com.xover.music.application.session;

import com.xover.music.application.audio.AudioPlayerListener;
import com.xover.music.application.audio.AudioPlayerPort;
import com.xover.music.application.clock.Clock;
import com.xover.music.application.common.diagnostics.ErrorReporter;
import com.xover.music.application.playlist.TrackSourceValidator;
import com.xover.music.application.playlist.TrackTitleLookup;
import com.xover.music.application.network.error.NetworkTransportException;
import com.xover.music.application.network.error.RemoteProtocolException;
import com.xover.music.application.common.error.UnexpectedXoverException;
import com.xover.music.application.common.error.XoverException;
import com.xover.music.application.network.HostStartupConfig;
import com.xover.music.application.network.MessageType;
import com.xover.music.application.network.PeerAddress;
import com.xover.music.application.network.PeerMessage;
import com.xover.music.application.network.PeerTransportListener;
import com.xover.music.application.network.PeerTransportPort;
import com.xover.music.application.sync.ClockSynchronizer;
import com.xover.music.domain.ConnectionStatus;
import com.xover.music.domain.DeviceRole;
import com.xover.music.domain.PlaybackStatus;
import com.xover.music.domain.PlaylistTrack;
import com.xover.music.domain.SessionViewState;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

/**
 * Main use-case facade. It owns session orchestration and keeps framework
 * details behind ports.
 */
public final class ListeningSessionService implements PeerTransportListener, AudioPlayerListener, AutoCloseable {
    private static final long PLAY_SAFETY_DELAY_MILLIS = 750L;
    private static final long TIME_SYNC_PERIOD_SECONDS = 3L;
    private static final long READY_TIMEOUT_SECONDS = 120L;

    private final AudioPlayerPort audioPlayer;
    private final PeerTransportPort transport;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final ClockSynchronizer clockSynchronizer;
    private final ErrorReporter errorReporter;
    private final TrackTitleLookup trackTitleLookup;
    private final ExecutorService titleExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "xover-track-titles");
        thread.setDaemon(true);
        return thread;
    });
    private final List<SessionObserver> observers = new CopyOnWriteArrayList<>();

    private volatile SessionViewState state = SessionViewState.idle();
    private volatile HostStartupConfig hostConfig;
    private volatile ScheduledFuture<?> timeSyncTask;
    private volatile ScheduledFuture<?> pendingPlayTask;
    private ScheduledFuture<?> readinessTimeout;
    private String loadId = "";
    private boolean localReady;
    private boolean localFailed;
    private boolean readyAnnounced;
    private boolean playRequested;
    private long requestedPositionMillis;
    private long playGeneration;
    private final Set<String> readyPeers = new HashSet<>();
    private final Set<String> failedPeers = new HashSet<>();
    private final Map<String, Long> pendingClockSamples = new LinkedHashMap<>();

    public ListeningSessionService(
        AudioPlayerPort audioPlayer,
        PeerTransportPort transport,
        Clock clock,
        ScheduledExecutorService scheduler,
        ClockSynchronizer clockSynchronizer,
        ErrorReporter errorReporter
    ) {
        this(audioPlayer, transport, clock, scheduler, clockSynchronizer, errorReporter, uri -> Optional.empty());
    }

    public ListeningSessionService(
        AudioPlayerPort audioPlayer,
        PeerTransportPort transport,
        Clock clock,
        ScheduledExecutorService scheduler,
        ClockSynchronizer clockSynchronizer,
        ErrorReporter errorReporter,
        TrackTitleLookup trackTitleLookup
    ) {
        this.audioPlayer = Objects.requireNonNull(audioPlayer, "audioPlayer");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clockSynchronizer = Objects.requireNonNull(clockSynchronizer, "clockSynchronizer");
        this.errorReporter = Objects.requireNonNull(errorReporter, "errorReporter");
        this.trackTitleLookup = Objects.requireNonNull(trackTitleLookup, "trackTitleLookup");

        this.audioPlayer.setListener(this);
        this.transport.setListener(this);
    }

    public SessionViewState currentState() {
        return state;
    }

    public void addObserver(SessionObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
        observer.onStateChanged(state);
    }

    public void removeObserver(SessionObserver observer) {
        observers.remove(observer);
    }

    public void startHost(String advertisedHost, int port) {
        if (isHostActive(state)) {
            updateState(previous -> previous.withMessage("Hosting is already running"));
            return;
        }
        if (isClientActive(state)) {
            updateState(previous -> previous.withMessage("Disconnect before hosting"));
            return;
        }

        HostStartupConfig config = null;
        try {
            PeerAddress address = new PeerAddress(advertisedHost, port);
            config = new HostStartupConfig(address.host(), address.host(), address.port());
            hostConfig = config;

            updateState(previous -> previous
                .withRole(DeviceRole.HOST)
                .withConnectionStatus(ConnectionStatus.HOSTING)
                .withConnectedPeers(List.of())
                .withControllerPeers(List.of())
                .withCanControlRoom(true)
                .withMessage("Hosting on port " + port)
            );

            loadCurrentPlaylistTrack("Loading current track");
            transport.startHost(config);
            broadcastPlaylist();
        } catch (XoverException ex) {
            fail(ex.title(), ex);
        } catch (RuntimeException ex) {
            fail("Could not start host session", config == null ? ex : NetworkTransportException.hostStartupFailed(config, ex));
        }
    }

    public void connectToHost(String host, int port) {
        if (isClientActive(state)) {
            updateState(previous -> previous.withMessage("Client connection is already active"));
            return;
        }
        if (isHostActive(state)) {
            updateState(previous -> previous.withMessage("Stop hosting before connecting"));
            return;
        }

        PeerAddress address = null;
        try {
            address = new PeerAddress(host, port);
            synchronized (this) {
                resetReadiness("");
                clockSynchronizer.reset();
                pendingClockSamples.clear();
                audioPlayer.stop();
                hostConfig = null;
                updateState(previous -> previous
                    .withRole(DeviceRole.CLIENT)
                    .withConnectionStatus(ConnectionStatus.CONNECTING)
                    .withPlaybackStatus(PlaybackStatus.STOPPED)
                    .withConnectedPeers(List.of())
                    .withControllerPeers(List.of())
                    .withCanControlRoom(false)
                    .withMessage("Connecting to " + host + ":" + port)
                );
            }
            transport.connect(address);
        } catch (XoverException ex) {
            fail(ex.title(), ex);
        } catch (RuntimeException ex) {
            fail("Could not connect to host", address == null ? ex : NetworkTransportException.connectionFailed(address, ex));
        }
    }

    public synchronized void addTrackUrl(String sourceUrl) {
        if (state.role() == DeviceRole.CLIENT) {
            if (state.canControlRoom()) {
                transport.send(PeerMessage.playlistEditRequest(MessageType.PLAYLIST_ADD_REQUEST, "", sourceUrl, -1));
            } else {
                updateState(previous -> previous.withMessage("Host has not allowed playlist editing"));
            }
            return;
        }

        try {
            URI sourceUri = TrackSourceValidator.requireSafeSource(sourceUrl);
            PlaylistTrack track = PlaylistTrack.create(titleFromUri(sourceUri), sourceUri.toString());
            boolean[] shouldLoad = new boolean[1];

            updateState(previous -> {
                List<PlaylistTrack> nextPlaylist = new ArrayList<>(previous.playlist());
                nextPlaylist.add(track);
                int nextIndex = Math.max(previous.currentTrackIndex(), 0);
                shouldLoad[0] = previous.currentTrackIndex() < 0 && previous.role() == DeviceRole.HOST;
                return previous
                    .withPlaylist(nextPlaylist, nextIndex)
                    .withPlaybackStatus(shouldLoad[0] ? PlaybackStatus.LOADING : previous.playbackStatus())
                    .withMessage("Track added: " + track.title());
            });

            if (shouldLoad[0]) {
                loadCurrentPlaylistTrack("Loading added track");
            }
            broadcastPlaylist();
            titleExecutor.submit(() -> {
                try {
                    trackTitleLookup.lookup(sourceUri)
                        .ifPresent(title -> applyDetectedTitle(track.id(), track.title(), title));
                } catch (Exception ignored) {
                    // The URL-based title remains usable if metadata is unavailable.
                }
            });
        } catch (XoverException ex) {
            reportRecoverable(ex.title(), ex);
            updateState(previous -> previous.withMessage("Could not add track URL: " + ex.getMessage()));
        } catch (RuntimeException ex) {
            XoverException failure = new UnexpectedXoverException("Could not add track URL", ex);
            reportRecoverable("Could not add track URL", failure);
            updateState(previous -> previous.withMessage("Could not add track URL: " + ex.getMessage()));
        }
    }

    public synchronized void selectTrack(int trackIndex) {
        if (state.role() == DeviceRole.CLIENT) {
            sendTrackEditRequest(MessageType.PLAYLIST_SELECT_REQUEST, trackIndex, "", -1);
            return;
        }
        if (trackIndex < 0 || trackIndex >= state.playlist().size()) {
            updateState(previous -> previous.withMessage("Track selection is out of range"));
            return;
        }

        updateState(previous -> previous
            .withPlaylist(previous.playlist(), trackIndex)
            .withPlaybackStatus(PlaybackStatus.LOADING)
            .withMessage("Selected track: " + previous.playlist().get(trackIndex).title())
        );
        loadCurrentPlaylistTrack("Loading selected track");
        broadcastPlaylist();
    }

    public synchronized void removeTrackAt(int trackIndex) {
        if (state.role() == DeviceRole.CLIENT) {
            sendTrackEditRequest(MessageType.PLAYLIST_REMOVE_REQUEST, trackIndex, "", -1);
            return;
        }
        if (trackIndex < 0 || trackIndex >= state.playlist().size()) {
            return;
        }

        boolean removedCurrent = trackIndex == state.currentTrackIndex();
        updateState(previous -> {
            List<PlaylistTrack> nextPlaylist = new ArrayList<>(previous.playlist());
            PlaylistTrack removed = nextPlaylist.remove(trackIndex);
            int nextIndex = nextPlaylist.isEmpty()
                ? -1
                : nextIndexAfterRemoval(previous.currentTrackIndex(), trackIndex, nextPlaylist.size());
            PlaybackStatus nextStatus = nextPlaylist.isEmpty()
                ? PlaybackStatus.STOPPED
                : removedCurrent ? PlaybackStatus.LOADING : previous.playbackStatus();
            return previous
                .withPlaylist(nextPlaylist, nextIndex)
                .withPlaybackStatus(nextStatus)
                .withMessage("Removed track: " + removed.title());
        });

        if (state.playlist().isEmpty()) {
            loadCurrentPlaylistTrack("Playlist is empty");
        } else if (removedCurrent) {
            loadCurrentPlaylistTrack("Loading next track");
        }
        broadcastPlaylist();
    }

    public synchronized void moveTrack(int fromIndex, int toIndex) {
        if (state.role() == DeviceRole.CLIENT) {
            sendTrackEditRequest(MessageType.PLAYLIST_MOVE_REQUEST, fromIndex, "", toIndex);
            return;
        }
        int playlistSize = state.playlist().size();
        if (fromIndex < 0 || fromIndex >= playlistSize || playlistSize < 2) {
            return;
        }

        int normalizedToIndex = Math.max(0, Math.min(playlistSize - 1, toIndex));
        if (fromIndex == normalizedToIndex) {
            return;
        }

        updateState(previous -> {
            List<PlaylistTrack> nextPlaylist = new ArrayList<>(previous.playlist());
            PlaylistTrack moved = nextPlaylist.remove(fromIndex);
            nextPlaylist.add(normalizedToIndex, moved);
            int nextCurrentIndex = currentIndexAfterMove(previous.currentTrackIndex(), fromIndex, normalizedToIndex);
            return previous
                .withPlaylist(nextPlaylist, nextCurrentIndex)
                .withMessage("Moved track: " + moved.title());
        });
        broadcastPlaylist();
    }

    public synchronized void play() {
        if (state.role() == DeviceRole.CLIENT && state.canControlRoom()) {
            transport.send(PeerMessage.playbackRequest(MessageType.PLAY_REQUEST, 0L));
            return;
        }
        if (state.role() != DeviceRole.HOST) {
            updateState(previous -> previous.withMessage("Host has not allowed room control"));
            return;
        }
        if (state.currentTrack() == null) {
            updateState(previous -> previous.withMessage("Add a track before playback"));
            return;
        }
        if (playRequested || pendingPlayTask != null || state.playbackStatus() == PlaybackStatus.PLAYING) {
            return;
        }
        if (state.playbackStatus() == PlaybackStatus.ERROR || !failedPeers.isEmpty()) {
            updateState(previous -> previous.withMessage("Track failed to load; select it again to retry"));
            return;
        }
        playRequested = true;
        requestedPositionMillis = state.positionMillis();
        awaitReadiness();
    }

    public synchronized void nextTrack() {
        if (state.role() == DeviceRole.CLIENT && state.canControlRoom()) {
            transport.send(PeerMessage.playbackRequest(MessageType.NEXT_TRACK_REQUEST, 0L));
            return;
        }
        if (state.role() != DeviceRole.HOST || state.currentTrackIndex() + 1 >= state.playlist().size()) {
            return;
        }
        boolean resume = state.playbackStatus() == PlaybackStatus.PLAYING || playRequested || pendingPlayTask != null;
        selectTrack(state.currentTrackIndex() + 1);
        if (resume) play();
    }

    public synchronized void backTrack() {
        if (state.role() == DeviceRole.CLIENT && state.canControlRoom()) {
            transport.send(PeerMessage.playbackRequest(MessageType.BACK_TRACK_REQUEST, 0L));
            return;
        }
        if (state.role() != DeviceRole.HOST || state.currentTrackIndex() == 0) {
            return;
        }
        boolean resume = state.playbackStatus() == PlaybackStatus.PLAYING || playRequested || pendingPlayTask != null;
        if (state.positionMillis() > 2500) {
            seek(0L);
        }
        selectTrack(state.currentTrackIndex() - 1);
        if (resume) play();
    }

    public synchronized void renameTrack(int trackIndex, String title) {
        if (state.role() == DeviceRole.CLIENT) {
            sendTrackEditRequest(MessageType.PLAYLIST_RENAME_REQUEST, trackIndex, title, -1);
            return;
        }
        if (trackIndex < 0 || trackIndex >= state.playlist().size()) {
            return;
        }
        String nextTitle = title == null ? "" : title.trim();
        if (nextTitle.isBlank() || nextTitle.length() > 160) {
            updateState(previous -> previous.withMessage("Track title must be 1 to 160 characters"));
            return;
        }
        PlaylistTrack current = state.playlist().get(trackIndex);
        if (current.title().equals(nextTitle)) {
            return;
        }

        updateState(previous -> {
            List<PlaylistTrack> nextPlaylist = new ArrayList<>(previous.playlist());
            PlaylistTrack track = nextPlaylist.get(trackIndex);
            nextPlaylist.set(trackIndex, new PlaylistTrack(track.id(), nextTitle, track.sourceUrl()));
            return previous.withPlaylist(nextPlaylist, previous.currentTrackIndex())
                .withMessage("Renamed track: " + nextTitle);
        });
        broadcastPlaylist();
    }

    private synchronized void applyDetectedTitle(String trackId, String fallbackTitle, String title) {
        if (state.role() == DeviceRole.CLIENT || title == null) return;
        String resolvedTitle = title.trim();
        if (resolvedTitle.isEmpty() || resolvedTitle.length() > 160) return;
        for (int index = 0; index < state.playlist().size(); index++) {
            PlaylistTrack track = state.playlist().get(index);
            if (track.id().equals(trackId) && track.title().equals(fallbackTitle)) {
                renameTrack(index, resolvedTitle);
                return;
            }
        }
    }

    public synchronized void pause() {
        if (state.role() == DeviceRole.CLIENT && state.canControlRoom()) {
            transport.send(PeerMessage.playbackRequest(MessageType.PAUSE_REQUEST, 0L));
            return;
        }
        if (state.role() != DeviceRole.HOST) {
            return;
        }
        cancelPlayRequest();
        stopPendingPlayTask();
        long positionMillis = toMillis(audioPlayer.currentPosition());
        audioPlayer.pause();
        updateState(previous -> previous
            .withPlaybackStatus(localReady ? PlaybackStatus.PAUSED : PlaybackStatus.LOADING)
            .withPosition(positionMillis)
            .withMessage("Paused")
        );

        if (state.role() == DeviceRole.HOST) {
            transport.broadcast(PeerMessage.pause(loadId, positionMillis));
        }
    }

    public synchronized void seek(long positionMillis) {
        if (state.role() == DeviceRole.CLIENT && state.canControlRoom()) {
            transport.send(PeerMessage.playbackRequest(MessageType.SEEK_REQUEST, positionMillis));
            return;
        }
        if (state.role() != DeviceRole.HOST || !localReady) {
            return;
        }
        boolean resume = state.playbackStatus() == PlaybackStatus.PLAYING || playRequested || pendingPlayTask != null;
        pause();
        stopPendingPlayTask();
        long normalizedPosition = Math.max(0L, Math.min(state.durationMillis(), positionMillis));
        audioPlayer.seek(Duration.ofMillis(normalizedPosition));
        updateState(previous -> previous
            .withPosition(normalizedPosition)
            .withMessage("Seek " + normalizedPosition + " ms")
        );

        if (state.role() == DeviceRole.HOST) {
            transport.broadcast(PeerMessage.seek(loadId, normalizedPosition));
        }
        if (resume) play();
    }

    public void disconnect() {
        synchronized (this) {
            stopTimeSyncLoop();
            resetReadiness("");
            clockSynchronizer.reset();
            pendingClockSamples.clear();
            hostConfig = null;
            audioPlayer.stop();
            updateState(previous -> SessionViewState.idle()
                .withLocalVolumePercent(previous.localVolumePercent())
                .withMessage("Disconnected")
            );
        }
        transport.disconnect();
    }

    public synchronized void disconnectPeer(String peerId) {
        if (state.role() != DeviceRole.HOST) {
            updateState(previous -> previous.withMessage("Only host can disconnect listeners"));
            return;
        }
        if (peerId == null || peerId.isBlank()) {
            return;
        }
        if (!state.connectedPeerIds().contains(peerId)) {
            updateState(previous -> previous.withMessage("Listener is not connected: " + peerId));
            return;
        }

        transport.disconnectPeer(peerId);
        onPeerDisconnected(peerId);
    }

    public synchronized void setPeerRoomControl(String peerId, boolean allowed) {
        if (state.role() != DeviceRole.HOST || peerId == null || !state.connectedPeerIds().contains(peerId)) return;
        updateState(previous -> previous
            .withControllerPeers(allowed
                ? addPeer(previous.controllerPeerIds(), peerId)
                : removePeer(previous.controllerPeerIds(), peerId))
            .withMessage((allowed ? "Room control allowed for " : "Room control removed from ") + peerId));
        transport.sendToPeer(peerId, PeerMessage.roomControlPermission(allowed));
    }

    public synchronized void setLocalVolumePercent(int volumePercent) {
        int clampedVolume = Math.max(0, Math.min(100, volumePercent));
        audioPlayer.setVolume(clampedVolume / 100.0D);
        updateState(previous -> previous
            .withLocalVolumePercent(clampedVolume)
            .withMessage("Local volume: " + clampedVolume + "%")
        );
    }

    @Override
    public synchronized void onReady(String readyLoadId, Duration duration) {
        if (!isCurrentLoad(readyLoadId) || localFailed) return;
        localReady = true;
        updateState(previous -> previous
            .withPlaybackStatus(playRequested ? PlaybackStatus.WAITING : PlaybackStatus.READY)
            .withTrack(previous.trackName(), toMillis(duration))
            .withMessage("Track is ready")
        );
        announceReady();
        tryStartPlayback();
    }

    @Override
    public synchronized void onPositionChanged(String positionLoadId, Duration position) {
        if (!isCurrentLoad(positionLoadId) || !localReady) return;
        updateState(previous -> previous.withPosition(toMillis(position)));
    }

    @Override
    public synchronized void onError(String failedLoadId, String message, Throwable cause) {
        if (!isCurrentLoad(failedLoadId)) return;
        localReady = false;
        localFailed = true;
        cancelPlayRequest();
        stopPendingPlayTask();
        audioPlayer.pause();
        if (state.role() == DeviceRole.CLIENT) transport.send(PeerMessage.trackFailed(loadId));
        if (state.role() == DeviceRole.HOST) transport.broadcast(PeerMessage.pause(loadId, state.positionMillis()));
        reportRecoverable(message, cause);
        updateState(previous -> previous.withPlaybackStatus(PlaybackStatus.ERROR).withMessage(message));
    }

    @Override
    public synchronized void onEnded(String endedLoadId) {
        if (!isCurrentLoad(endedLoadId)) return;
        if (state.role() == DeviceRole.HOST) {
            if (state.playbackStatus() != PlaybackStatus.PLAYING || state.playlist().isEmpty()) return;
            int nextIndex = (state.currentTrackIndex() + 1) % state.playlist().size();
            selectTrack(nextIndex);
            play();
            return;
        }
        updateState(previous -> previous.withPlaybackStatus(PlaybackStatus.PAUSED).withMessage("Track ended"));
    }

    @Override
    public void onTransportReady(String detail) {
        updateState(previous -> previous.withMessage(detail));
    }

    @Override
    public synchronized void onPeerConnected(String peerId) {
        if (state.role() == DeviceRole.IDLE) return;
        boolean resume = playRequested || pendingPlayTask != null || state.playbackStatus() == PlaybackStatus.PLAYING;
        if (state.role() == DeviceRole.HOST) {
            if (resume) pause();
            readyPeers.remove(peerId);
            failedPeers.remove(peerId);
            updateState(previous -> {
                List<String> peers = addPeer(previous.connectedPeerIds(), peerId);
                return previous
                    .withConnectedPeers(peers)
                    .withConnectionStatus(ConnectionStatus.CONNECTED)
                    .withMessage("Peer connected: " + peerId);
            });
        } else {
            updateState(previous -> previous
                .withConnectionStatus(ConnectionStatus.CONNECTED)
                .withMessage("Connected to host")
            );
        }

        if (state.role() == DeviceRole.HOST && hostConfig != null) {
            sendPlaylistToPeer(peerId);
            transport.sendToPeer(peerId, PeerMessage.roomControlPermission(false));
            if (resume) play();
        }

        if (state.role() == DeviceRole.CLIENT) {
            startTimeSyncLoop();
        }
    }

    @Override
    public synchronized void onPeerDisconnected(String peerId) {
        if (state.role() == DeviceRole.IDLE) {
            return;
        }

        if (state.role() == DeviceRole.HOST) {
            if (!state.connectedPeerIds().contains(peerId)) return;
            pause();
            readyPeers.remove(peerId);
            failedPeers.remove(peerId);
            updateState(previous -> {
                List<String> peers = removePeer(previous.connectedPeerIds(), peerId);
                return previous
                    .withConnectedPeers(peers)
                    .withControllerPeers(removePeer(previous.controllerPeerIds(), peerId))
                    .withConnectionStatus(peers.isEmpty() ? ConnectionStatus.HOSTING : ConnectionStatus.CONNECTED)
                    .withMessage("Peer disconnected: " + peerId);
            });
        } else {
            resetReadiness("");
            audioPlayer.stop();
            updateState(previous -> previous
                .withConnectionStatus(ConnectionStatus.DISCONNECTED)
                .withPlaybackStatus(PlaybackStatus.STOPPED)
                .withCanControlRoom(false)
                .withMessage("Disconnected from host")
            );
        }
        if (state.role() == DeviceRole.CLIENT) {
            stopTimeSyncLoop();
        }
    }

    @Override
    public synchronized void onMessage(String peerId, PeerMessage message) {
        long receivedAtMillis = clock.nowMillis();

        try {
            if (!isRemoteMessageAllowed(message.type(), state.role())) {
                updateState(previous -> previous.withMessage("Ignored unexpected peer message: " + message.type()));
                return;
            }
            if (state.role() == DeviceRole.HOST && !state.connectedPeerIds().contains(peerId)) return;

            if (requiresRoomControl(message.type()) && !state.controllerPeerIds().contains(peerId)) {
                return;
            }

            switch (message.type()) {
                case ROOM_CONTROL_PERMISSION -> updateState(previous -> previous
                    .withCanControlRoom(message.canControlRoom())
                    .withMessage(message.canControlRoom() ? "Room control allowed" : "Room control removed"));
                case PLAYLIST_ADD_REQUEST -> addTrackUrl(message.trackName());
                case PLAYLIST_REMOVE_REQUEST -> editTrackById(message.mediaUri(), index -> removeTrackAt(index));
                case PLAYLIST_MOVE_REQUEST -> editTrackById(message.mediaUri(), index -> moveTrack(index, message.currentTrackIndex()));
                case PLAYLIST_RENAME_REQUEST -> editTrackById(message.mediaUri(), index -> renameTrack(index, message.trackName()));
                case PLAYLIST_SELECT_REQUEST -> editTrackById(message.mediaUri(), index -> selectTrack(index));
                case PLAY_REQUEST -> play();
                case PAUSE_REQUEST -> pause();
                case SEEK_REQUEST -> seek(message.positionMillis());
                case NEXT_TRACK_REQUEST -> nextTrack();
                case BACK_TRACK_REQUEST -> backTrack();
                case PLAYLIST_UPDATED -> applyRemotePlaylist(message);
                case TRACK_SELECTED -> loadRemoteTrack(message);
                case PLAY_AT -> {
                    if (isCurrentLoad(message.loadId()) && localReady && clockSynchronizer.isSynchronized()) {
                        schedulePlay(message.positionMillis(), message.startAtHostMillis());
                    }
                }
                case PAUSE -> { if (isCurrentLoad(message.loadId())) pauseFromRemote(message.positionMillis()); }
                case SEEK -> { if (isCurrentLoad(message.loadId())) seekFromRemote(message.positionMillis()); }
                case TRACK_READY -> {
                    if (isCurrentLoad(message.loadId()) && !failedPeers.contains(peerId)) {
                        readyPeers.add(peerId);
                        tryStartPlayback();
                    }
                }
                case TRACK_FAILED -> {
                    if (isCurrentLoad(message.loadId())) {
                        readyPeers.remove(peerId);
                        failedPeers.add(peerId);
                        pause();
                        updateState(previous -> previous.withMessage("Track could not load for " + peerId + "; select it again to retry"));
                    }
                }
                case TIME_SYNC_REQUEST -> respondToTimeSync(peerId, message, receivedAtMillis);
                case TIME_SYNC_RESPONSE -> applyTimeSync(message, receivedAtMillis);
            }
        } catch (RuntimeException ex) {
            fail("Could not apply remote sync message", new RemoteProtocolException("Could not apply remote sync message", ex));
        }
    }

    @Override
    public synchronized void onTransportError(String message, Throwable cause) {
        fail(message, cause);
    }

    @Override
    public void close() {
        titleExecutor.shutdownNow();
        synchronized (this) {
            stopTimeSyncLoop();
            resetReadiness("");
            state = SessionViewState.idle();
        }
        try {
            transport.close();
        } finally {
            audioPlayer.close();
            scheduler.shutdownNow();
        }
    }

    private void loadRemoteTrack(PeerMessage message) {
        try {
            requireLoadId(message);
            resetReadiness(message.loadId());
            updateState(previous -> previous
                .withPlaybackStatus(PlaybackStatus.LOADING)
                .withTrack(message.trackName(), 0L)
                .withMessage("Loading remote track")
            );
            audioPlayer.load(TrackSourceValidator.requireSafeSource(message.mediaUri()), loadId);
        } catch (RuntimeException ex) {
            fail("Could not load remote track", ex);
        }
    }

    private void applyRemotePlaylist(PeerMessage message) {
        try {
            List<PlaylistTrack> safePlaylist = safeRemotePlaylist(message.playlist());
            if (!safePlaylist.isEmpty()) requireLoadId(message);
            boolean newLoad = !loadId.equals(message.loadId());
            boolean[] trackChanged = new boolean[1];
            updateState(previous -> previous
                .withPlaylist(safePlaylist, message.currentTrackIndex())
                .withPlaybackStatus(nextPlaylistStatus(previous, safePlaylist, message.currentTrackIndex(), trackChanged))
                .withMessage(safePlaylist.isEmpty() ? "Playlist is empty" : "Playlist updated")
            );
            if (trackChanged[0] || newLoad) {
                resetReadiness(message.loadId());
                loadCurrentPlaylistTrack("Loading playlist track");
            }
        } catch (RuntimeException ex) {
            fail("Could not apply playlist update", ex);
        }
    }

    private synchronized void loadCurrentPlaylistTrack(String loadingMessage) {
        resetReadiness(state.role() == DeviceRole.CLIENT ? loadId : UUID.randomUUID().toString());
        PlaylistTrack track = state.currentTrack();
        if (track == null) {
            audioPlayer.stop();
            updateState(previous -> previous
                .withPlaybackStatus(PlaybackStatus.STOPPED)
                .withTrack("", 0L)
                .withMessage("Playlist is empty")
            );
            return;
        }

        updateState(previous -> previous
            .withPlaybackStatus(PlaybackStatus.LOADING)
            .withTrack(track.title(), 0L)
            .withPosition(0L)
            .withMessage(loadingMessage)
        );
        try {
            audioPlayer.load(TrackSourceValidator.requireSafeSource(track.sourceUrl()), loadId);
        } catch (RuntimeException ex) {
            onError(loadId, "Could not load track", ex);
        }
    }

    private void broadcastPlaylist() {
        if (state.role() == DeviceRole.HOST) {
            transport.broadcast(PeerMessage.playlistUpdated(state.playlist(), state.currentTrackIndex(), loadId));
        }
    }

    private void sendPlaylistToPeer(String peerId) {
        transport.sendToPeer(peerId, PeerMessage.playlistUpdated(state.playlist(), state.currentTrackIndex(), loadId));
    }

    private void sendTrackEditRequest(MessageType type, int trackIndex, String value, int toIndex) {
        if (!state.canControlRoom()) {
            updateState(previous -> previous.withMessage("Host has not allowed playlist editing"));
            return;
        }
        if (trackIndex < 0 || trackIndex >= state.playlist().size()) return;
        transport.send(PeerMessage.playlistEditRequest(type, state.playlist().get(trackIndex).id(), value, toIndex));
    }

    private void editTrackById(String trackId, java.util.function.IntConsumer edit) {
        for (int index = 0; index < state.playlist().size(); index++) {
            if (state.playlist().get(index).id().equals(trackId)) {
                edit.accept(index);
                return;
            }
        }
    }

    private boolean isPlaylistEditRequest(MessageType type) {
        return type == MessageType.PLAYLIST_ADD_REQUEST || type == MessageType.PLAYLIST_REMOVE_REQUEST
            || type == MessageType.PLAYLIST_MOVE_REQUEST || type == MessageType.PLAYLIST_RENAME_REQUEST
            || type == MessageType.PLAYLIST_SELECT_REQUEST;
    }

    private boolean requiresRoomControl(MessageType type) {
        return isPlaylistEditRequest(type) || type == MessageType.PLAY_REQUEST
            || type == MessageType.PAUSE_REQUEST || type == MessageType.SEEK_REQUEST
            || type == MessageType.NEXT_TRACK_REQUEST || type == MessageType.BACK_TRACK_REQUEST;
    }

    private List<PlaylistTrack> safeRemotePlaylist(List<PlaylistTrack> playlist) {
        List<PlaylistTrack> safePlaylist = new ArrayList<>();
        for (PlaylistTrack track : playlist) {
            URI sourceUri = TrackSourceValidator.requireSafeSource(track.sourceUrl());
            safePlaylist.add(new PlaylistTrack(track.id(), track.title(), sourceUri.toString()));
        }
        return safePlaylist;
    }

    private PlaybackStatus nextPlaylistStatus(
        SessionViewState previous,
        List<PlaylistTrack> playlist,
        int currentTrackIndex,
        boolean[] trackChanged
    ) {
        if (playlist.isEmpty()) {
            trackChanged[0] = previous.currentTrack() != null;
            return PlaybackStatus.STOPPED;
        }

        int nextIndex = Math.max(0, Math.min(playlist.size() - 1, currentTrackIndex));
        PlaylistTrack nextTrack = playlist.get(nextIndex);
        trackChanged[0] = !sameTrack(previous.currentTrack(), nextTrack);
        return trackChanged[0] ? PlaybackStatus.LOADING : previous.playbackStatus();
    }

    private boolean sameTrack(PlaylistTrack first, PlaylistTrack second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.id().equals(second.id()) && first.sourceUrl().equals(second.sourceUrl());
    }

    private boolean isHostActive(SessionViewState viewState) {
        return viewState.role() == DeviceRole.HOST
            && (
                viewState.connectionStatus() == ConnectionStatus.HOSTING
                    || viewState.connectionStatus() == ConnectionStatus.CONNECTED
            );
    }

    private boolean isClientActive(SessionViewState viewState) {
        return viewState.role() == DeviceRole.CLIENT
            && (
                viewState.connectionStatus() == ConnectionStatus.CONNECTING
                    || viewState.connectionStatus() == ConnectionStatus.CONNECTED
            );
    }

    private boolean isRemoteMessageAllowed(MessageType type, DeviceRole role) {
        if (type == null) {
            return false;
        }
        return switch (role) {
            case HOST -> type == MessageType.TIME_SYNC_REQUEST
                || type == MessageType.TRACK_READY || type == MessageType.TRACK_FAILED
                || requiresRoomControl(type);
            case CLIENT -> type == MessageType.PLAYLIST_UPDATED
                || type == MessageType.ROOM_CONTROL_PERMISSION
                || type == MessageType.TRACK_SELECTED
                || type == MessageType.PLAY_AT
                || type == MessageType.PAUSE
                || type == MessageType.SEEK
                || type == MessageType.TIME_SYNC_RESPONSE;
            case IDLE -> false;
        };
    }

    private List<String> addPeer(List<String> peers, String peerId) {
        List<String> nextPeers = new ArrayList<>(peers);
        if (!nextPeers.contains(peerId)) {
            nextPeers.add(peerId);
        }
        return nextPeers;
    }

    private List<String> removePeer(List<String> peers, String peerId) {
        List<String> nextPeers = new ArrayList<>(peers);
        nextPeers.remove(peerId);
        return nextPeers;
    }

    private void schedulePlay(long positionMillis, long startAtHostMillis) {
        stopPendingPlayTask();
        long generation = playGeneration;
        String scheduledLoadId = loadId;
        long localNow = clock.nowMillis();
        long delayMillis = state.role() == DeviceRole.CLIENT
            ? clockSynchronizer.localDelayUntilHostTime(startAtHostMillis, localNow)
            : Math.max(0L, startAtHostMillis - localNow);

        pendingPlayTask = scheduler.schedule(() -> {
            synchronized (this) {
                if (generation != playGeneration || !isCurrentLoad(scheduledLoadId) || !localReady) return;
                if (state.role() == DeviceRole.HOST && !allPeersReady()) return;
                pendingPlayTask = null;
                try {
                    audioPlayer.seek(Duration.ofMillis(Math.max(0L, positionMillis)));
                    audioPlayer.play();
                    updateState(previous -> previous
                        .withPlaybackStatus(PlaybackStatus.PLAYING)
                        .withPosition(positionMillis)
                        .withMessage("Playing")
                    );
                } catch (RuntimeException ex) {
                    onError(scheduledLoadId, "Could not start playback", ex);
                }
            }
        }, delayMillis, TimeUnit.MILLISECONDS);

        updateState(previous -> previous.withPlaybackStatus(PlaybackStatus.WAITING)
            .withMessage("Play scheduled in " + delayMillis + " ms"));
    }

    private void pauseFromRemote(long positionMillis) {
        stopPendingPlayTask();
        audioPlayer.pause();
        audioPlayer.seek(Duration.ofMillis(Math.max(0L, positionMillis)));
        updateState(previous -> previous
            .withPlaybackStatus(localReady ? PlaybackStatus.PAUSED : PlaybackStatus.LOADING)
            .withPosition(positionMillis)
            .withMessage("Paused by host")
        );
    }

    private void seekFromRemote(long positionMillis) {
        stopPendingPlayTask();
        audioPlayer.seek(Duration.ofMillis(Math.max(0L, positionMillis)));
        updateState(previous -> previous
            .withPosition(positionMillis)
            .withMessage("Seek by host")
        );
    }

    private void respondToTimeSync(String peerId, PeerMessage message, long hostReceivedAtMillis) {
        if (state.role() != DeviceRole.HOST) {
            return;
        }
        transport.sendToPeer(peerId, PeerMessage.timeSyncResponse(
            message.nonce(),
            message.clientSentAtMillis(),
            hostReceivedAtMillis,
            clock.nowMillis()
        ));
    }

    private void applyTimeSync(PeerMessage message, long clientReceivedAtMillis) {
        if (state.role() != DeviceRole.CLIENT) {
            return;
        }
        Long sentAt = pendingClockSamples.remove(message.nonce());
        if (sentAt == null || sentAt != message.clientSentAtMillis()
            || clientReceivedAtMillis < sentAt || clientReceivedAtMillis - sentAt > 15_000L
            || message.hostSentAtMillis() < message.hostReceivedAtMillis()) return;
        clockSynchronizer.applySample(
            message.clientSentAtMillis(),
            clientReceivedAtMillis,
            message.hostReceivedAtMillis(),
            message.hostSentAtMillis()
        );
        updateState(previous -> previous
            .withClockOffset(clockSynchronizer.offsetMillis())
        );
        announceReady();
    }

    private void startTimeSyncLoop() {
        stopTimeSyncLoop();
        timeSyncTask = scheduler.scheduleAtFixedRate(
            () -> {
                try {
                    synchronized (this) {
                        if (state.role() != DeviceRole.CLIENT || state.connectionStatus() != ConnectionStatus.CONNECTED) return;
                        PeerMessage request = PeerMessage.timeSyncRequest(clock.nowMillis());
                        if (pendingClockSamples.size() >= 8) pendingClockSamples.clear();
                        pendingClockSamples.put(request.nonce(), request.clientSentAtMillis());
                        transport.send(request);
                    }
                } catch (RuntimeException ex) {
                    fail("Could not send time sync request", ex);
                }
            },
            0L,
            TIME_SYNC_PERIOD_SECONDS,
            TimeUnit.SECONDS
        );
    }

    private void stopTimeSyncLoop() {
        ScheduledFuture<?> task = timeSyncTask;
        if (task != null) {
            task.cancel(true);
            timeSyncTask = null;
        }
    }

    private void stopPendingPlayTask() {
        playGeneration++;
        ScheduledFuture<?> task = pendingPlayTask;
        if (task != null) {
            task.cancel(false);
            pendingPlayTask = null;
        }
    }

    private boolean isCurrentLoad(String candidate) {
        return state.role() != DeviceRole.IDLE && !loadId.isBlank() && loadId.equals(candidate);
    }

    private void requireLoadId(PeerMessage message) {
        if (message.loadId().isBlank()) {
            throw new IllegalArgumentException("Track load identifier is missing; update both Xover apps");
        }
    }

    private void resetReadiness(String nextLoadId) {
        cancelPlayRequest();
        stopPendingPlayTask();
        loadId = nextLoadId;
        localReady = false;
        localFailed = false;
        readyAnnounced = false;
        readyPeers.clear();
        failedPeers.clear();
    }

    private void cancelPlayRequest() {
        playRequested = false;
        if (readinessTimeout != null) {
            readinessTimeout.cancel(false);
            readinessTimeout = null;
        }
    }

    private boolean allPeersReady() {
        return failedPeers.isEmpty() && readyPeers.containsAll(state.connectedPeerIds());
    }

    private void announceReady() {
        if (state.role() == DeviceRole.CLIENT && localReady && clockSynchronizer.isSynchronized() && !readyAnnounced) {
            readyAnnounced = true;
            transport.send(PeerMessage.trackReady(loadId));
        }
    }

    private void awaitReadiness() {
        if (readinessTimeout == null) {
            String waitingLoadId = loadId;
            readinessTimeout = scheduler.schedule(() -> {
                synchronized (this) {
                    if (!isCurrentLoad(waitingLoadId) || !playRequested) return;
                    readinessTimeout = null;
                    cancelPlayRequest();
                    stopPendingPlayTask();
                    updateState(previous -> previous
                        .withPlaybackStatus(localReady ? PlaybackStatus.PAUSED : PlaybackStatus.LOADING)
                        .withMessage("Loading timed out. Check listeners, then press Play to retry."));
                }
            }, READY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        tryStartPlayback();
    }

    private void tryStartPlayback() {
        if (state.role() != DeviceRole.HOST || !playRequested) return;
        if (!localReady || !allPeersReady()) {
            updateState(previous -> previous.withPlaybackStatus(PlaybackStatus.WAITING)
                .withMessage(!localReady ? "Waiting for this device to load the track"
                    : "Waiting for listeners: " + readyPeers.size() + "/" + previous.connectedPeerIds().size() + " ready"));
            return;
        }
        long position = requestedPositionMillis;
        cancelPlayRequest();
        long startAt = clock.nowMillis() + PLAY_SAFETY_DELAY_MILLIS;
        transport.broadcast(PeerMessage.playAt(loadId, position, startAt));
        if (localReady) schedulePlay(position, startAt);
    }

    private String titleFromUri(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().replaceFirst("^www\\.", "");
        String path = uri.getPath() == null ? "" : uri.getPath();
        String[] parts = path.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isBlank()) {
                String decoded = URLDecoder.decode(parts[i], StandardCharsets.UTF_8);
                String trimmedExtension = decoded.replaceFirst("\\.[A-Za-z0-9]{2,5}$", "");
                String readable = trimmedExtension.replace('-', ' ').replace('_', ' ').trim();
                return host.isBlank() ? readable : host + " / " + readable;
            }
        }
        return uri.toString();
    }

    private int nextIndexAfterRemoval(int currentIndex, int removedIndex, int nextSize) {
        if (nextSize <= 0) {
            return -1;
        }
        if (removedIndex < currentIndex) {
            return currentIndex - 1;
        }
        if (removedIndex == currentIndex) {
            return Math.min(removedIndex, nextSize - 1);
        }
        return Math.min(currentIndex, nextSize - 1);
    }

    private int currentIndexAfterMove(int currentIndex, int fromIndex, int toIndex) {
        if (currentIndex == fromIndex) {
            return toIndex;
        }
        if (fromIndex < currentIndex && toIndex >= currentIndex) {
            return currentIndex - 1;
        }
        if (fromIndex > currentIndex && toIndex <= currentIndex) {
            return currentIndex + 1;
        }
        return currentIndex;
    }

    private synchronized void fail(String message, Throwable cause) {
        boolean notifyFailure = !localFailed && !loadId.isBlank();
        cancelPlayRequest();
        stopPendingPlayTask();
        stopTimeSyncLoop();
        localReady = false;
        localFailed = true;
        audioPlayer.pause();
        if (notifyFailure && state.role() == DeviceRole.CLIENT) {
            transport.send(PeerMessage.trackFailed(loadId));
        }
        if (notifyFailure && state.role() == DeviceRole.HOST) {
            transport.broadcast(PeerMessage.pause(loadId, state.positionMillis()));
        }
        Throwable failure = normalizeFailure(message, cause);
        errorReporter.report(message, failure);
        String detail = failure.getMessage() == null ? message : message + ": " + failure.getMessage();
        updateState(previous -> previous
            .withConnectionStatus(ConnectionStatus.ERROR)
            .withPlaybackStatus(PlaybackStatus.ERROR)
            .withMessage(detail)
        );
    }

    private void reportRecoverable(String message, Throwable cause) {
        errorReporter.report(message, normalizeFailure(message, cause));
    }

    private Throwable normalizeFailure(String message, Throwable cause) {
        if (cause instanceof XoverException) {
            return cause;
        }
        return new UnexpectedXoverException(message, cause);
    }

    private void updateState(UnaryOperator<SessionViewState> mutation) {
        SessionViewState next;
        synchronized (this) {
            next = mutation.apply(state);
            state = next;
        }
        notifyObservers(next);
    }

    private void notifyObservers(SessionViewState next) {
        for (SessionObserver observer : observers) {
            observer.onStateChanged(next);
        }
    }

    public static long toMillis(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return 0L;
        }
        return duration.toMillis();
    }
}
