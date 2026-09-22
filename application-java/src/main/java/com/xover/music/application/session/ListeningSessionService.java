package com.xover.music.application.session;

import com.xover.music.application.audio.AudioPlayerListener;
import com.xover.music.application.audio.AudioPlayerPort;
import com.xover.music.application.clock.Clock;
import com.xover.music.application.common.diagnostics.ErrorReporter;
import com.xover.music.application.playlist.error.InvalidTrackSourceException;
import com.xover.music.application.network.error.NetworkTransportException;
import com.xover.music.application.network.error.RemoteProtocolException;
import com.xover.music.application.common.error.UnexpectedXoverException;
import com.xover.music.application.common.error.XoverException;
import com.xover.music.application.network.HostStartupConfig;
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
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
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

    private final AudioPlayerPort audioPlayer;
    private final PeerTransportPort transport;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final ClockSynchronizer clockSynchronizer;
    private final ErrorReporter errorReporter;
    private final List<SessionObserver> observers = new CopyOnWriteArrayList<>();

    private volatile SessionViewState state = SessionViewState.idle();
    private volatile HostStartupConfig hostConfig;
    private volatile ScheduledFuture<?> timeSyncTask;

    public ListeningSessionService(
        AudioPlayerPort audioPlayer,
        PeerTransportPort transport,
        Clock clock,
        ScheduledExecutorService scheduler,
        ClockSynchronizer clockSynchronizer,
        ErrorReporter errorReporter
    ) {
        this.audioPlayer = Objects.requireNonNull(audioPlayer, "audioPlayer");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clockSynchronizer = Objects.requireNonNull(clockSynchronizer, "clockSynchronizer");
        this.errorReporter = Objects.requireNonNull(errorReporter, "errorReporter");

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
            config = new HostStartupConfig("0.0.0.0", advertisedHost, port);
            hostConfig = config;

            updateState(previous -> previous
                .withRole(DeviceRole.HOST)
                .withConnectionStatus(ConnectionStatus.HOSTING)
                .withConnectedPeers(List.of())
                .withMessage("Hosting on port " + port)
            );

            transport.startHost(config);
            loadCurrentPlaylistTrack("Loading current track");
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
            hostConfig = null;
            updateState(previous -> previous
                .withRole(DeviceRole.CLIENT)
                .withConnectionStatus(ConnectionStatus.CONNECTING)
                .withPlaybackStatus(PlaybackStatus.STOPPED)
                .withConnectedPeers(List.of())
                .withMessage("Connecting to " + host + ":" + port)
            );
            address = new PeerAddress(host, port);
            transport.connect(address);
        } catch (XoverException ex) {
            fail(ex.title(), ex);
        } catch (RuntimeException ex) {
            fail("Could not connect to host", address == null ? ex : NetworkTransportException.connectionFailed(address, ex));
        }
    }

    public void addTrackUrl(String sourceUrl) {
        if (state.role() == DeviceRole.CLIENT) {
            updateState(previous -> previous.withMessage("Only host can edit the playlist"));
            return;
        }

        try {
            URI sourceUri = validateTrackSource(sourceUrl);
            PlaylistTrack track = PlaylistTrack.create(titleFromUri(sourceUri), sourceUri.toString());
            boolean[] shouldLoad = new boolean[1];

            updateState(previous -> {
                List<PlaylistTrack> nextPlaylist = new ArrayList<>(previous.playlist());
                nextPlaylist.add(track);
                int nextIndex = previous.currentTrackIndex() < 0 ? 0 : previous.currentTrackIndex();
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
        } catch (XoverException ex) {
            reportRecoverable(ex.title(), ex);
            updateState(previous -> previous.withMessage("Could not add track URL: " + ex.getMessage()));
        } catch (RuntimeException ex) {
            XoverException failure = new UnexpectedXoverException("Could not add track URL", ex);
            reportRecoverable("Could not add track URL", failure);
            updateState(previous -> previous.withMessage("Could not add track URL: " + ex.getMessage()));
        }
    }

    public void selectTrack(int trackIndex) {
        if (state.role() == DeviceRole.CLIENT) {
            updateState(previous -> previous.withMessage("Only host can select playlist tracks"));
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

    public void removeTrackAt(int trackIndex) {
        if (state.role() == DeviceRole.CLIENT) {
            updateState(previous -> previous.withMessage("Only host can edit the playlist"));
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
            audioPlayer.stop();
        } else if (removedCurrent) {
            loadCurrentPlaylistTrack("Loading next track");
        }
        broadcastPlaylist();
    }

    public void moveTrack(int fromIndex, int toIndex) {
        if (state.role() == DeviceRole.CLIENT) {
            updateState(previous -> previous.withMessage("Only host can edit the playlist"));
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

    public void play() {
        if (state.role() != DeviceRole.HOST) {
            updateState(previous -> previous.withMessage("Only host controls synchronized playback in this MVP"));
            return;
        }
        if (state.currentTrack() == null) {
            updateState(previous -> previous.withMessage("Add a track before playback"));
            return;
        }

        long positionMillis = toMillis(audioPlayer.currentPosition());
        long startAtHostMillis = clock.nowMillis() + PLAY_SAFETY_DELAY_MILLIS;
        PeerMessage message = PeerMessage.playAt(positionMillis, startAtHostMillis);
        transport.broadcast(message);
        schedulePlay(positionMillis, startAtHostMillis);
    }

    public void pause() {
        long positionMillis = toMillis(audioPlayer.currentPosition());
        audioPlayer.pause();
        updateState(previous -> previous
            .withPlaybackStatus(PlaybackStatus.PAUSED)
            .withPosition(positionMillis)
            .withMessage("Paused")
        );

        if (state.role() == DeviceRole.HOST) {
            transport.broadcast(PeerMessage.pause(positionMillis));
        }
    }

    public void seek(long positionMillis) {
        long normalizedPosition = Math.max(0L, positionMillis);
        audioPlayer.seek(Duration.ofMillis(normalizedPosition));
        updateState(previous -> previous
            .withPosition(normalizedPosition)
            .withMessage("Seek " + normalizedPosition + " ms")
        );

        if (state.role() == DeviceRole.HOST) {
            transport.broadcast(PeerMessage.seek(normalizedPosition));
        }
    }

    public void disconnect() {
        stopTimeSyncLoop();
        hostConfig = null;
        audioPlayer.stop();
        transport.disconnect();
        updateState(previous -> SessionViewState.idle()
            .withLocalVolumePercent(previous.localVolumePercent())
            .withMessage("Disconnected")
        );
    }

    public void setLocalVolumePercent(int volumePercent) {
        int clampedVolume = Math.max(0, Math.min(100, volumePercent));
        audioPlayer.setVolume(clampedVolume / 100.0D);
        updateState(previous -> previous
            .withLocalVolumePercent(clampedVolume)
            .withMessage("Local volume: " + clampedVolume + "%")
        );
    }

    @Override
    public void onReady(Duration duration) {
        updateState(previous -> previous
            .withPlaybackStatus(PlaybackStatus.READY)
            .withTrack(previous.trackName(), toMillis(duration))
            .withMessage("Track is ready")
        );
    }

    @Override
    public void onPositionChanged(Duration position) {
        updateState(previous -> previous.withPosition(toMillis(position)));
    }

    @Override
    public void onError(String message, Throwable cause) {
        fail(message, cause);
    }

    @Override
    public void onTransportReady(String detail) {
        updateState(previous -> previous.withMessage(detail));
    }

    @Override
    public void onPeerConnected(String peerId) {
        if (state.role() == DeviceRole.HOST) {
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
            broadcastPlaylist();
        }

        if (state.role() == DeviceRole.CLIENT) {
            startTimeSyncLoop();
        }
    }

    @Override
    public void onPeerDisconnected(String peerId) {
        if (state.role() == DeviceRole.IDLE) {
            return;
        }

        if (state.role() == DeviceRole.HOST) {
            updateState(previous -> {
                List<String> peers = removePeer(previous.connectedPeerIds(), peerId);
                return previous
                    .withConnectedPeers(peers)
                    .withConnectionStatus(peers.isEmpty() ? ConnectionStatus.HOSTING : ConnectionStatus.CONNECTED)
                    .withMessage("Peer disconnected: " + peerId);
            });
        } else {
            updateState(previous -> previous
                .withConnectionStatus(ConnectionStatus.DISCONNECTED)
                .withMessage("Disconnected from host")
            );
        }
        if (state.role() == DeviceRole.CLIENT) {
            stopTimeSyncLoop();
        }
    }

    @Override
    public void onMessage(PeerMessage message) {
        long receivedAtMillis = clock.nowMillis();

        try {
            switch (message.type()) {
                case PLAYLIST_UPDATED -> applyRemotePlaylist(message);
                case TRACK_SELECTED -> loadRemoteTrack(message);
                case PLAY_AT -> schedulePlay(message.positionMillis(), message.startAtHostMillis());
                case PAUSE -> pauseFromRemote(message.positionMillis());
                case SEEK -> seekFromRemote(message.positionMillis());
                case TIME_SYNC_REQUEST -> respondToTimeSync(message, receivedAtMillis);
                case TIME_SYNC_RESPONSE -> applyTimeSync(message, receivedAtMillis);
            }
        } catch (RuntimeException ex) {
            fail("Could not apply remote sync message", new RemoteProtocolException("Could not apply remote sync message", ex));
        }
    }

    @Override
    public void onTransportError(String message, Throwable cause) {
        fail(message, cause);
    }

    @Override
    public void close() {
        stopTimeSyncLoop();
        try {
            transport.close();
        } finally {
            audioPlayer.close();
            scheduler.shutdownNow();
        }
    }

    private void loadRemoteTrack(PeerMessage message) {
        try {
            updateState(previous -> previous
                .withPlaybackStatus(PlaybackStatus.LOADING)
                .withTrack(message.trackName(), 0L)
                .withMessage("Loading remote track")
            );
            audioPlayer.load(URI.create(message.mediaUri()));
        } catch (RuntimeException ex) {
            fail("Could not load remote track", ex);
        }
    }

    private void applyRemotePlaylist(PeerMessage message) {
        try {
            boolean[] trackChanged = new boolean[1];
            updateState(previous -> previous
                .withPlaylist(message.playlist(), message.currentTrackIndex())
                .withPlaybackStatus(nextPlaylistStatus(previous, message, trackChanged))
                .withMessage(message.playlist().isEmpty() ? "Playlist is empty" : "Playlist updated")
            );
            if (trackChanged[0]) {
                loadCurrentPlaylistTrack("Loading playlist track");
            }
        } catch (RuntimeException ex) {
            fail("Could not apply playlist update", ex);
        }
    }

    private void loadCurrentPlaylistTrack(String loadingMessage) {
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
            .withMessage(loadingMessage)
        );
        audioPlayer.load(URI.create(track.sourceUrl()));
    }

    private void broadcastPlaylist() {
        if (state.role() == DeviceRole.HOST) {
            transport.broadcast(PeerMessage.playlistUpdated(state.playlist(), state.currentTrackIndex()));
        }
    }

    private PlaybackStatus nextPlaylistStatus(SessionViewState previous, PeerMessage message, boolean[] trackChanged) {
        if (message.playlist().isEmpty()) {
            trackChanged[0] = previous.currentTrack() != null;
            return PlaybackStatus.STOPPED;
        }

        int nextIndex = Math.max(0, Math.min(message.playlist().size() - 1, message.currentTrackIndex()));
        PlaylistTrack nextTrack = message.playlist().get(nextIndex);
        trackChanged[0] = !sameTrack(previous.currentTrack(), nextTrack);
        return trackChanged[0] ? PlaybackStatus.LOADING : previous.playbackStatus();
    }

    private boolean sameTrack(PlaylistTrack first, PlaylistTrack second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.id().equals(second.id());
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
        long localNow = clock.nowMillis();
        long delayMillis = state.role() == DeviceRole.CLIENT
            ? clockSynchronizer.localDelayUntilHostTime(startAtHostMillis, localNow)
            : Math.max(0L, startAtHostMillis - localNow);

        scheduler.schedule(() -> {
            audioPlayer.seek(Duration.ofMillis(Math.max(0L, positionMillis)));
            audioPlayer.play();
            updateState(previous -> previous
                .withPlaybackStatus(PlaybackStatus.PLAYING)
                .withPosition(positionMillis)
                .withMessage("Playing")
            );
        }, delayMillis, TimeUnit.MILLISECONDS);

        updateState(previous -> previous.withMessage("Play scheduled in " + delayMillis + " ms"));
    }

    private void pauseFromRemote(long positionMillis) {
        audioPlayer.pause();
        audioPlayer.seek(Duration.ofMillis(Math.max(0L, positionMillis)));
        updateState(previous -> previous
            .withPlaybackStatus(PlaybackStatus.PAUSED)
            .withPosition(positionMillis)
            .withMessage("Paused by host")
        );
    }

    private void seekFromRemote(long positionMillis) {
        audioPlayer.seek(Duration.ofMillis(Math.max(0L, positionMillis)));
        updateState(previous -> previous
            .withPosition(positionMillis)
            .withMessage("Seek by host")
        );
    }

    private void respondToTimeSync(PeerMessage message, long hostReceivedAtMillis) {
        if (state.role() != DeviceRole.HOST) {
            return;
        }
        transport.send(PeerMessage.timeSyncResponse(
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
        clockSynchronizer.applySample(
            message.clientSentAtMillis(),
            clientReceivedAtMillis,
            message.hostReceivedAtMillis(),
            message.hostSentAtMillis()
        );
        updateState(previous -> previous
            .withClockOffset(clockSynchronizer.offsetMillis())
            .withMessage("Clock offset: " + clockSynchronizer.offsetMillis() + " ms")
        );
    }

    private void startTimeSyncLoop() {
        stopTimeSyncLoop();
        timeSyncTask = scheduler.scheduleAtFixedRate(
            () -> {
                try {
                    transport.send(PeerMessage.timeSyncRequest(clock.nowMillis()));
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

    private URI validateTrackSource(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw InvalidTrackSourceException.blank();
        }
        URI uri;
        try {
            uri = URI.create(sourceUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw InvalidTrackSourceException.malformed(sourceUrl, ex);
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw InvalidTrackSourceException.unsupportedScheme(uri);
        }
        return uri;
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

    private void fail(String message, Throwable cause) {
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

    private long toMillis(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return 0L;
        }
        return duration.toMillis();
    }
}
