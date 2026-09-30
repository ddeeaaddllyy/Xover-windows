package com.xover.music.application.session;

import com.xover.music.application.audio.AudioPlayerListener;
import com.xover.music.application.audio.AudioPlayerPort;
import com.xover.music.application.common.diagnostics.DefaultErrorReporter;
import com.xover.music.application.network.HostStartupConfig;
import com.xover.music.application.network.MessageType;
import com.xover.music.application.network.PeerAddress;
import com.xover.music.application.network.PeerMessage;
import com.xover.music.application.network.PeerTransportListener;
import com.xover.music.application.network.PeerTransportPort;
import com.xover.music.application.sync.ClockSynchronizer;
import com.xover.music.domain.PlaybackStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

final class PlaylistProgressionTest {
    private final ManualScheduler scheduler = new ManualScheduler();
    private final FakeAudio audio = new FakeAudio();
    private final FakeTransport transport = new FakeTransport();
    private final ListeningSessionService service = new ListeningSessionService(
        audio, transport, () -> 10_000L + scheduler.now, scheduler,
        new ClockSynchronizer(), new DefaultErrorReporter()
    );

    @AfterEach
    void close() {
        service.close();
    }

    @Test
    void finishedTrackWaitsForListenersThenWrapsAfterTheLastTrack() {
        hostWithTracks(2);
        service.onPeerConnected("friend");
        audio.ready();
        service.onMessage("friend", PeerMessage.trackReady(audio.loadId));
        service.play();
        scheduler.advance(750);
        assertEquals(1, audio.plays);

        String firstLoad = audio.loadId;
        audio.end();
        assertEquals(1, service.currentState().currentTrackIndex());
        assertNotEquals(firstLoad, audio.loadId);
        assertEquals(PlaybackStatus.WAITING, service.currentState().playbackStatus());
        assertEquals(1, audio.plays);

        audio.ready();
        scheduler.advance(750);
        assertEquals(1, audio.plays, "The host waits for the listener's new load");
        service.onMessage("friend", PeerMessage.trackReady(audio.loadId));
        scheduler.advance(750);
        assertEquals(2, audio.plays);

        String lastLoad = audio.loadId;
        audio.end();
        assertEquals(0, service.currentState().currentTrackIndex());
        assertNotEquals(lastLoad, audio.loadId);
        assertEquals(MessageType.PLAYLIST_UPDATED, transport.broadcasts.getLast().type());
        assertEquals(0, transport.broadcasts.getLast().currentTrackIndex());
        audio.ready();
        service.onMessage("friend", PeerMessage.trackReady(audio.loadId));
        scheduler.advance(750);
        assertEquals(3, audio.plays);
    }

    @Test
    void oneTrackRepeatsButLateOrPausedEndCallbacksDoNotAdvance() {
        hostWithTracks(1);
        audio.ready();
        service.play();
        scheduler.advance(750);
        String firstLoad = audio.loadId;

        audio.end();
        assertEquals(0, service.currentState().currentTrackIndex());
        assertNotEquals(firstLoad, audio.loadId);
        service.onEnded(firstLoad);
        assertEquals(2, audio.loads, "A stale end event must not load again");

        audio.ready();
        scheduler.advance(750);
        assertEquals(2, audio.plays);
        service.pause();
        audio.end();
        assertEquals(2, audio.loads, "An end event after pause must not restart playback");
    }

    @Test
    void reorderingKeepsTheSelectedTrackPlayingAndBroadcastsTheNewOrder() {
        hostWithTracks(3);
        audio.ready();
        service.play();
        scheduler.advance(750);
        String selectedId = service.currentState().currentTrack().id();
        String load = audio.loadId;

        service.moveTrack(0, 2);
        assertEquals(2, service.currentState().currentTrackIndex());
        assertEquals(selectedId, service.currentState().currentTrack().id());
        assertEquals(load, audio.loadId);
        assertEquals(1, audio.loads);
        assertEquals(PlaybackStatus.PLAYING, service.currentState().playbackStatus());
        PeerMessage update = transport.broadcasts.getLast();
        assertEquals(MessageType.PLAYLIST_UPDATED, update.type());
        assertEquals(2, update.currentTrackIndex());
        assertEquals(selectedId, update.playlist().get(2).id());

        String nextId = service.currentState().playlist().getFirst().id();
        audio.end();
        assertEquals(0, service.currentState().currentTrackIndex());
        assertEquals(nextId, service.currentState().currentTrack().id(),
            "The next track follows the reordered playlist");
    }

    private void hostWithTracks(int count) {
        for (int index = 0; index < count; index++) {
            service.addTrackUrl("https://example.com/track-" + index + ".mp3");
        }
        service.startHost("127.0.0.1", 47321);
    }

    private static final class FakeAudio implements AudioPlayerPort {
        AudioPlayerListener listener;
        String loadId;
        int plays;
        int loads;

        public void setListener(AudioPlayerListener listener) { this.listener = listener; }
        public void load(URI uri, String nextLoadId) { loadId = nextLoadId; loads++; }
        void ready() { listener.onReady(loadId, Duration.ofMinutes(3)); }
        void end() { listener.onEnded(loadId); }
        public void play() { plays++; }
        public void pause() { }
        public void stop() { }
        public void seek(Duration position) { }
        public void setVolume(double volume) { }
        public double volume() { return 1; }
        public Duration currentPosition() { return Duration.ZERO; }
        public Duration duration() { return Duration.ofMinutes(3); }
        public void close() { }
    }

    private static final class FakeTransport implements PeerTransportPort {
        final List<PeerMessage> broadcasts = new ArrayList<>();

        public void setListener(PeerTransportListener listener) { }
        public void startHost(HostStartupConfig config) { }
        public void connect(PeerAddress address) { }
        public void send(PeerMessage message) { }
        public void sendToPeer(String peerId, PeerMessage message) { }
        public void broadcast(PeerMessage message) { broadcasts.add(message); }
        public void disconnectPeer(String peerId) { }
        public void disconnect() { }
        public void close() { }
    }

    private static final class ManualScheduler extends ScheduledThreadPoolExecutor {
        long now;
        final List<ManualTask> tasks = new ArrayList<>();

        ManualScheduler() { super(1); }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            ManualTask task = new ManualTask(command, now + unit.toMillis(delay));
            tasks.add(task);
            return task;
        }

        void advance(long millis) {
            long target = now + millis;
            while (true) {
                ManualTask next = tasks.stream().filter(task -> !task.isDone() && task.deadline <= target)
                    .min(Comparator.comparingLong(task -> task.deadline)).orElse(null);
                if (next == null) break;
                now = next.deadline;
                next.run();
                try { next.get(); } catch (Exception ex) { throw new AssertionError(ex); }
            }
            now = target;
        }

        private static final class ManualTask extends FutureTask<Void> implements ScheduledFuture<Void> {
            final long deadline;

            ManualTask(Runnable command, long deadline) { super(command, null); this.deadline = deadline; }
            public long getDelay(TimeUnit unit) { return unit.convert(deadline, TimeUnit.MILLISECONDS); }
            public int compareTo(java.util.concurrent.Delayed other) {
                return Long.compare(getDelay(TimeUnit.MILLISECONDS), other.getDelay(TimeUnit.MILLISECONDS));
            }
        }
    }
}
