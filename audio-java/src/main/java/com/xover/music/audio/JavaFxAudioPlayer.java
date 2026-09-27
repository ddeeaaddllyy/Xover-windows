package com.xover.music.audio;

import com.xover.music.application.audio.AudioPlayerListener;
import com.xover.music.application.audio.AudioPlayerPort;
import com.xover.music.application.audio.error.AudioPlaybackException;
import com.xover.music.application.common.error.XoverException;
import com.xover.music.audio.resolver.MediaResolver;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class JavaFxAudioPlayer implements AudioPlayerPort {
    private static final AtomicBoolean TOOLKIT_STARTED = new AtomicBoolean(false);

    private final AtomicReference<MediaPlayer> player = new AtomicReference<>();
    private final MediaResolver mediaResolver = new MediaResolver();
    private final ExecutorService resolverExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "xover-media-resolver");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong loadGeneration = new AtomicLong();
    private volatile Future<?> resolverTask;
    private volatile Duration currentPosition = Duration.ZERO;
    private volatile Duration currentDuration = Duration.ZERO;
    private volatile double volume = 1.0D;
    private volatile AudioPlayerListener listener = new AudioPlayerListener() {
    };

    public JavaFxAudioPlayer() {
        ensureToolkit();
    }

    @Override
    public void setListener(AudioPlayerListener listener) {
        this.listener = listener == null ? new AudioPlayerListener() {
        } : listener;
    }

    @Override
    public void load(URI mediaUri, String loadId) {
        Objects.requireNonNull(mediaUri, "mediaUri");
        long generation = loadGeneration.incrementAndGet();
        cancelResolution();
        currentPosition = Duration.ZERO;
        currentDuration = Duration.ZERO;
        runOnFx(this::disposeCurrentPlayer);
        resolverTask = resolverExecutor.submit(() -> {
            try {
                URI resolvedUri = mediaResolver.resolve(mediaUri);
                runOnFx(() -> loadResolvedMedia(mediaUri, resolvedUri, generation, loadId));
            } catch (RuntimeException ex) {
                if (generation == loadGeneration.get()) {
                    Throwable failure = mediaResolutionFailure(mediaUri, unwrapCompletionException(ex));
                    listener.onError(loadId, "Could not resolve media: " + mediaUri, failure);
                }
            }
        });
    }

    @Override
    public void play() {
        long generation = loadGeneration.get();
        runOnFx(() -> {
            if (generation != loadGeneration.get()) return;
            withPlayer(mediaPlayer -> {
                if (mediaPlayer.getStatus() != MediaPlayer.Status.UNKNOWN
                    && mediaPlayer.getStatus() != MediaPlayer.Status.HALTED
                    && mediaPlayer.getStatus() != MediaPlayer.Status.DISPOSED) mediaPlayer.play();
            });
        });
    }

    @Override
    public void pause() {
        runOnFx(() -> withPlayer(MediaPlayer::pause));
    }

    @Override
    public void stop() {
        loadGeneration.incrementAndGet();
        cancelResolution();
        currentPosition = Duration.ZERO;
        currentDuration = Duration.ZERO;
        runOnFx(this::disposeCurrentPlayer);
    }

    @Override
    public void seek(Duration position) {
        Duration safePosition = position == null || position.isNegative() ? Duration.ZERO : position;
        currentPosition = safePosition;
        runOnFx(() -> withPlayer(mediaPlayer ->
            mediaPlayer.seek(javafx.util.Duration.millis(safePosition.toMillis()))
        ));
    }

    @Override
    public void setVolume(double volume) {
        double nextVolume = Math.max(0.0D, Math.min(1.0D, volume));
        this.volume = nextVolume;
        runOnFx(() -> withPlayer(mediaPlayer -> mediaPlayer.setVolume(nextVolume)));
    }

    @Override
    public double volume() {
        return volume;
    }

    @Override
    public Duration currentPosition() {
        return currentPosition;
    }

    @Override
    public Duration duration() {
        return currentDuration;
    }

    @Override
    public void close() {
        stop();
        resolverExecutor.shutdownNow();
    }

    private void loadResolvedMedia(URI originalUri, URI resolvedUri, long generation, String loadId) {
        if (generation != loadGeneration.get()) {
            return;
        }

        try {
            Media media = new Media(resolvedUri.toString());
            MediaPlayer nextPlayer = new MediaPlayer(media);
            nextPlayer.setVolume(volume);
            nextPlayer.setAutoPlay(false);
            nextPlayer.setOnReady(() -> {
                if (generation != loadGeneration.get()) return;
                currentDuration = toJavaDuration(nextPlayer.getTotalDuration());
                listener.onReady(loadId, currentDuration);
            });
            nextPlayer.setOnError(() -> {
                if (generation != loadGeneration.get()) return;
                AudioPlaybackException failure = AudioPlaybackException.engineFailed(nextPlayer.getError());
                listener.onError(loadId, failure.title(), failure);
            });
            media.setOnError(() -> {
                if (generation != loadGeneration.get()) return;
                AudioPlaybackException failure = AudioPlaybackException.engineFailed(media.getError());
                listener.onError(loadId, failure.title(), failure);
            });
            nextPlayer.currentTimeProperty().addListener((ignored, oldValue, newValue) -> {
                if (generation != loadGeneration.get()) return;
                currentPosition = toJavaDuration(newValue);
                listener.onPositionChanged(loadId, currentPosition);
            });
            nextPlayer.setOnEndOfMedia(() -> {
                if (generation == loadGeneration.get()) listener.onEnded(loadId);
            });
            player.set(nextPlayer);
        } catch (MediaException ex) {
            AudioPlaybackException failure = AudioPlaybackException.loadFailed(originalUri, ex);
            listener.onError(loadId, failure.title(), failure);
        }
    }

    private void withPlayer(PlayerAction action) {
        MediaPlayer current = player.get();
        if (current != null) {
            action.apply(current);
        }
    }

    private void disposeCurrentPlayer() {
        MediaPlayer current = player.getAndSet(null);
        if (current != null) {
            current.stop();
            current.dispose();
        }
    }

    private void runOnFx(Runnable action) {
        ensureToolkit();
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        Platform.runLater(action);
    }

    private void cancelResolution() {
        Future<?> task = resolverTask;
        if (task != null) task.cancel(true);
        resolverTask = null;
    }

    private static void ensureToolkit() {
        if (TOOLKIT_STARTED.compareAndSet(false, true)) {
            new JFXPanel();
            Platform.setImplicitExit(false);
        }
    }

    private Duration toJavaDuration(javafx.util.Duration fxDuration) {
        if (fxDuration == null || fxDuration.isUnknown() || fxDuration.isIndefinite()) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(Math.max(0L, Math.round(fxDuration.toMillis())));
    }

    private Throwable unwrapCompletionException(Throwable throwable) {
        if (throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private Throwable mediaResolutionFailure(URI mediaUri, Throwable failure) {
        if (failure instanceof XoverException) {
            return failure;
        }
        return AudioPlaybackException.resolutionFailed(mediaUri, failure);
    }

    @FunctionalInterface
    private interface PlayerAction {
        void apply(MediaPlayer mediaPlayer);
    }
}
