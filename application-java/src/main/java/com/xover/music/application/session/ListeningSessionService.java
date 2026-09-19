package com.xover.music.application.session;

import com.xover.music.application.audio.AudioPlayerListener;
import com.xover.music.application.audio.AudioPlayerPort;
import com.xover.music.application.clock.Clock;
import com.xover.music.application.network.HostStartupConfig;
import com.xover.music.application.network.PeerAddress;
import com.xover.music.application.network.PeerMessage;
import com.xover.music.application.network.PeerTransportListener;
import com.xover.music.application.network.PeerTransportPort;
import com.xover.music.application.sync.ClockSynchronizer;
import com.xover.music.domain.ConnectionStatus;
import com.xover.music.domain.DeviceRole;
import com.xover.music.domain.PlaybackStatus;
import com.xover.music.domain.SessionViewState;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
    private final List<SessionObserver> observers = new CopyOnWriteArrayList<>();

    private volatile SessionViewState state = SessionViewState.idle();
    private volatile HostStartupConfig hostConfig;
    private volatile ScheduledFuture<?> timeSyncTask;

    public ListeningSessionService(
        AudioPlayerPort audioPlayer,
        PeerTransportPort transport,
        Clock clock,
        ScheduledExecutorService scheduler,
        ClockSynchronizer clockSynchronizer
    ) {
        this.audioPlayer = Objects.requireNonNull(audioPlayer, "audioPlayer");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clockSynchronizer = Objects.requireNonNull(clockSynchronizer, "clockSynchronizer");

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

    public void startHost(Path trackFile, String advertisedHost, int port) {
        try {
            if (trackFile == null || !Files.isRegularFile(trackFile)) {
                throw new IllegalArgumentException("Choose an existing audio file before hosting");
            }

            HostStartupConfig config = new HostStartupConfig("0.0.0.0", advertisedHost, port, trackFile.toAbsolutePath());
            hostConfig = config;

            updateState(previous -> previous
                .withRole(DeviceRole.HOST)
                .withConnectionStatus(ConnectionStatus.HOSTING)
                .withPlaybackStatus(PlaybackStatus.LOADING)
                .withTrack(trackFile.getFileName().toString(), 0L)
                .withMessage("Hosting on port " + port)
            );

            audioPlayer.load(trackFile.toUri());
            transport.startHost(config);
        } catch (RuntimeException ex) {
            fail("Could not start host session", ex);
        }
    }

    public void connectToHost(String host, int port) {
        try {
            hostConfig = null;
            updateState(previous -> previous
                .withRole(DeviceRole.CLIENT)
                .withConnectionStatus(ConnectionStatus.CONNECTING)
                .withPlaybackStatus(PlaybackStatus.STOPPED)
                .withMessage("Connecting to " + host + ":" + port)
            );
            transport.connect(new PeerAddress(host, port));
        } catch (RuntimeException ex) {
            fail("Could not connect to host", ex);
        }
    }

    public void play() {
        if (state.role() != DeviceRole.HOST) {
            updateState(previous -> previous.withMessage("Only host controls synchronized playback in this MVP"));
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
        updateState(previous -> previous
            .withConnectionStatus(ConnectionStatus.CONNECTED)
            .withMessage("Peer connected: " + peerId)
        );

        if (state.role() == DeviceRole.HOST && hostConfig != null) {
            transport.broadcast(PeerMessage.trackSelected(
                hostConfig.trackFile().getFileName().toString(),
                hostConfig.mediaUri()
            ));
        }

        if (state.role() == DeviceRole.CLIENT) {
            startTimeSyncLoop();
        }
    }

    @Override
    public void onPeerDisconnected(String peerId) {
        updateState(previous -> previous
            .withConnectionStatus(state.role() == DeviceRole.HOST ? ConnectionStatus.HOSTING : ConnectionStatus.DISCONNECTED)
            .withMessage("Peer disconnected: " + peerId)
        );
        if (state.role() == DeviceRole.CLIENT) {
            stopTimeSyncLoop();
        }
    }

    @Override
    public void onMessage(PeerMessage message) {
        long receivedAtMillis = clock.nowMillis();

        switch (message.type()) {
            case TRACK_SELECTED -> loadRemoteTrack(message);
            case PLAY_AT -> schedulePlay(message.positionMillis(), message.startAtHostMillis());
            case PAUSE -> pauseFromRemote(message.positionMillis());
            case SEEK -> seekFromRemote(message.positionMillis());
            case TIME_SYNC_REQUEST -> respondToTimeSync(message, receivedAtMillis);
            case TIME_SYNC_RESPONSE -> applyTimeSync(message, receivedAtMillis);
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
            () -> transport.send(PeerMessage.timeSyncRequest(clock.nowMillis())),
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

    private void fail(String message, Throwable cause) {
        String detail = cause == null ? message : message + ": " + cause.getMessage();
        updateState(previous -> previous
            .withConnectionStatus(ConnectionStatus.ERROR)
            .withPlaybackStatus(PlaybackStatus.ERROR)
            .withMessage(detail)
        );
    }

    private void updateState(UnaryOperator<SessionViewState> mutation) {
        SessionViewState next;
        synchronized (this) {
            next = mutation.apply(state);
            state = next;
        }
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
