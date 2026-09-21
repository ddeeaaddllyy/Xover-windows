package com.xover.music.audio;

import com.xover.music.application.audio.AudioPlayerListener;
import com.xover.music.application.audio.AudioPlayerPort;
import com.xover.music.application.error.AudioPlaybackException;
import com.xover.music.application.error.XoverException;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class JavaFxAudioPlayer implements AudioPlayerPort {
    private static final AtomicBoolean TOOLKIT_STARTED = new AtomicBoolean(false);

    private final AtomicReference<MediaPlayer> player = new AtomicReference<>();
    private final SoundCloudMediaResolver mediaResolver = new SoundCloudMediaResolver();
    private final ExecutorService resolverExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "xover-media-resolver");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong loadGeneration = new AtomicLong();
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
    public void load(URI mediaUri) {
        Objects.requireNonNull(mediaUri, "mediaUri");
        long generation = loadGeneration.incrementAndGet();
        runOnFx(this::disposeCurrentPlayer);

        CompletableFuture
            .supplyAsync(() -> mediaResolver.resolve(mediaUri), resolverExecutor)
            .thenAccept(resolvedUri -> runOnFx(() -> loadResolvedMedia(mediaUri, resolvedUri, generation)))
            .exceptionally(ex -> {
                if (generation == loadGeneration.get()) {
                    Throwable failure = mediaResolutionFailure(mediaUri, unwrapCompletionException(ex));
                    listener.onError("Could not resolve media: " + mediaUri, failure);
                }
                return null;
            });
    }

    @Override
    public void play() {
        runOnFx(() -> withPlayer(MediaPlayer::play));
    }

    @Override
    public void pause() {
        runOnFx(() -> withPlayer(MediaPlayer::pause));
    }

    @Override
    public void stop() {
        runOnFx(() -> withPlayer(MediaPlayer::stop));
    }

    @Override
    public void seek(Duration position) {
        Duration safePosition = position == null || position.isNegative() ? Duration.ZERO : position;
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
        return callOnFx(() -> {
            MediaPlayer current = player.get();
            return current == null ? Duration.ZERO : toJavaDuration(current.getCurrentTime());
        });
    }

    @Override
    public Duration duration() {
        return callOnFx(() -> {
            MediaPlayer current = player.get();
            return current == null ? Duration.ZERO : toJavaDuration(current.getTotalDuration());
        });
    }

    @Override
    public void close() {
        loadGeneration.incrementAndGet();
        runOnFx(this::disposeCurrentPlayer);
        resolverExecutor.shutdownNow();
    }

    private void loadResolvedMedia(URI originalUri, URI resolvedUri, long generation) {
        if (generation != loadGeneration.get()) {
            return;
        }

        try {
            Media media = new Media(resolvedUri.toString());
            MediaPlayer nextPlayer = new MediaPlayer(media);
            nextPlayer.setVolume(volume);
            nextPlayer.setOnReady(() -> listener.onReady(toJavaDuration(nextPlayer.getTotalDuration())));
            nextPlayer.setOnError(() -> {
                AudioPlaybackException failure = AudioPlaybackException.engineFailed(nextPlayer.getError());
                listener.onError(failure.title(), failure);
            });
            nextPlayer.currentTimeProperty().addListener((ignored, oldValue, newValue) ->
                listener.onPositionChanged(toJavaDuration(newValue))
            );
            player.set(nextPlayer);
        } catch (MediaException ex) {
            AudioPlaybackException failure = AudioPlaybackException.loadFailed(originalUri, ex);
            listener.onError(failure.title(), failure);
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

    private <T> T callOnFx(Supplier<T> supplier) {
        ensureToolkit();
        if (Platform.isFxApplicationThread()) {
            return supplier.get();
        }

        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(supplier.get());
            } catch (RuntimeException ex) {
                result.completeExceptionally(ex);
            }
        });

        try {
            return result.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw AudioPlaybackException.fxThreadFailed("Interrupted while waiting for JavaFX audio engine", ex);
        } catch (ExecutionException ex) {
            throw AudioPlaybackException.fxThreadFailed("JavaFX audio engine failed", ex.getCause());
        }
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
