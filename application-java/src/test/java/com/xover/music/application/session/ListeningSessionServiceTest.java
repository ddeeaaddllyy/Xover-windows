package com.xover.music.application.session;

import com.xover.music.application.audio.AudioPlayerListener;
import com.xover.music.application.audio.AudioPlayerPort;
import com.xover.music.application.common.diagnostics.DefaultErrorReporter;
import com.xover.music.application.network.*;
import com.xover.music.application.sync.ClockSynchronizer;
import com.xover.music.domain.PlaybackStatus;
import com.xover.music.domain.PlaylistTrack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

final class ListeningSessionServiceTest {
    private final ManualScheduler scheduler = new ManualScheduler();
    private final FakeAudio audio = new FakeAudio();
    private final FakeTransport transport = new FakeTransport();
    private final ListeningSessionService service = new ListeningSessionService(
        audio, transport, () -> 10_000L + scheduler.now, scheduler,
        new ClockSynchronizer(), new DefaultErrorReporter()
    );

    @AfterEach
    void close() { service.close(); }

    @Test
    void waitsForBothHostAndListenerBeforeScheduling() {
        hostWithTrack("friend");
        service.play();
        scheduler.advance(1_000);
        assertEquals(0, audio.plays);
        service.onMessage("friend", PeerMessage.trackReady(audio.loadId));
        scheduler.advance(1_000);
        assertEquals(0, transport.playCount());
        audio.ready();
        assertEquals(1, transport.playCount());
        scheduler.advance(749);
        assertEquals(0, audio.plays);
        scheduler.advance(1);
        assertEquals(1, audio.plays);
    }

    @Test
    void waitsForEveryListenerAndIgnoresUnknownOrDuplicateAcknowledgements() {
        hostWithTrack("one", "two");
        audio.ready();
        service.play();
        service.onMessage("stranger", PeerMessage.trackReady(audio.loadId));
        service.onMessage("one", PeerMessage.trackReady(audio.loadId));
        service.onMessage("one", PeerMessage.trackReady(audio.loadId));
        assertEquals(0, transport.playCount());
        service.onMessage("two", PeerMessage.trackReady(audio.loadId));
        service.onMessage("two", PeerMessage.trackReady(audio.loadId));
        assertEquals(1, transport.playCount());
    }

    @Test
    void ignoresStaleAudioCallbacksAndReadinessAfterReselection() {
        hostWithTrack("friend");
        String oldLoad = audio.loadId;
        audio.ready();
        service.play();
        service.selectTrack(0);
        assertNotEquals(oldLoad, audio.loadId);
        service.onReady(oldLoad, Duration.ofSeconds(90));
        service.onError(oldLoad, "stale error", new IllegalStateException());
        service.onMessage("friend", PeerMessage.trackReady(oldLoad));
        service.play();
        audio.ready();
        assertEquals(0, transport.playCount());
        service.onMessage("friend", PeerMessage.trackReady(audio.loadId));
        assertEquals(1, transport.playCount());
    }

    @Test
    void pauseCancelsWaitingAndLateReadinessDoesNotAutoplay() {
        hostWithTrack("friend");
        service.play();
        service.pause();
        audio.ready();
        service.onMessage("friend", PeerMessage.trackReady(audio.loadId));
        scheduler.advance(1_000);
        assertEquals(0, transport.playCount());
        assertEquals(0, audio.plays);
    }

    @Test
    void pauseCancelsScheduledPlayback() {
        hostWithTrack();
        audio.ready();
        service.play();
        service.pause();
        scheduler.advance(1_000);
        assertEquals(0, audio.plays);
    }

    @Test
    void changingTrackCancelsScheduledPlayback() {
        hostWithTrack();
        audio.ready();
        service.play();
        service.addTrackUrl("https://example.com/second.mp3");
        service.selectTrack(1);
        scheduler.advance(1_000);
        assertEquals(0, audio.plays);
        assertEquals(PlaybackStatus.LOADING, service.currentState().playbackStatus());
    }

    @Test
    void removingLastTrackCancelsScheduledPlayback() {
        hostWithTrack();
        audio.ready();
        service.play();
        service.removeTrackAt(0);
        scheduler.advance(1_000);
        assertEquals(0, audio.plays);
        assertNull(service.currentState().currentTrack());
    }

    @Test
    void playlistReorderPreservesReadinessForTheSameTrack() {
        hostWithTrack("friend");
        audio.ready();
        service.onMessage("friend", PeerMessage.trackReady(audio.loadId));
        String load = audio.loadId;
        service.addTrackUrl("https://example.com/second.mp3");
        service.moveTrack(0, 1);
        service.play();
        assertEquals(load, audio.loadId);
        assertEquals(1, transport.playCount());
    }

    @Test
    void renamingCurrentTrackKeepsPlaybackAndSharesTitleWithListeners() {
        hostWithTrack("friend");
        audio.ready();
        service.onMessage("friend", PeerMessage.trackReady(audio.loadId));
        service.play();
        scheduler.advance(750);
        String load = audio.loadId;
        int loads = audio.loads;
        service.renameTrack(0, "  Midnight Drive  ");

        assertEquals("Midnight Drive", service.currentState().trackName());
        assertEquals("Midnight Drive", service.currentState().currentTrack().title());
        assertEquals(PlaybackStatus.PLAYING, service.currentState().playbackStatus());
        assertEquals(load, audio.loadId);
        assertEquals(loads, audio.loads);
        PeerMessage update = transport.broadcasts.getLast();
        assertEquals(MessageType.PLAYLIST_UPDATED, update.type());
        assertEquals(load, update.loadId());
        assertEquals("Midnight Drive", update.playlist().getFirst().title());
    }

    @Test
    void listenerAcceptsRenamedTitleWithoutReloadAndCannotRenameItLocally() {
        readyClient();
        String load = audio.loadId;
        int loads = audio.loads;
        PlaylistTrack track = service.currentState().currentTrack();
        service.onMessage("host", PeerMessage.playlistUpdated(
            List.of(new PlaylistTrack(track.id(), "Night Ride", track.sourceUrl())), 0, load));
        assertEquals("Night Ride", service.currentState().trackName());
        assertEquals(loads, audio.loads);
        service.renameTrack(0, "My own title");
        assertEquals("Night Ride", service.currentState().trackName());
    }

    @Test
    void blankTitleDoesNotChangeThePlaylist() {
        hostWithTrack();
        String original = service.currentState().currentTrack().title();
        int messages = transport.broadcasts.size();
        service.renameTrack(0, "   ");
        assertEquals(original, service.currentState().currentTrack().title());
        assertEquals(messages, transport.broadcasts.size());
    }

    @Test
    void listenerFailureCancelsStartAndRequiresReload() {
        hostWithTrack("friend");
        audio.ready();
        service.play();
        service.onMessage("friend", PeerMessage.trackFailed(audio.loadId));
        service.onMessage("friend", PeerMessage.trackReady(audio.loadId));
        service.play();
        scheduler.advance(1_000);
        assertEquals(0, audio.plays);
        assertEquals(0, transport.playCount());
    }

    @Test
    void localErrorCancelsStartEvenAfterItWasScheduled() {
        hostWithTrack();
        audio.ready();
        service.play();
        service.onError(audio.loadId, "failed", new IllegalStateException());
        scheduler.advance(1_000);
        assertEquals(0, audio.plays);
        assertEquals(PlaybackStatus.ERROR, service.currentState().playbackStatus());
    }

    @Test
    void disconnectingListenerDoesNotTriggerSoloAutoplay() {
        hostWithTrack("friend");
        audio.ready();
        service.play();
        service.onPeerDisconnected("friend");
        scheduler.advance(1_000);
        assertEquals(0, audio.plays);
        assertEquals(0, transport.playCount());
    }

    @Test
    void newListenerPausesPlayingHostUntilItIsReady() {
        hostWithTrack();
        audio.ready();
        service.play();
        scheduler.advance(750);
        assertEquals(1, audio.plays);
        service.onPeerConnected("new-friend");
        assertTrue(audio.pauses > 0);
        assertEquals(PlaybackStatus.WAITING, service.currentState().playbackStatus());
        scheduler.advance(1_000);
        assertEquals(1, audio.plays);
        service.onMessage("new-friend", PeerMessage.trackReady(audio.loadId));
        scheduler.advance(750);
        assertEquals(2, audio.plays);
    }

    @Test
    void timeoutCancelsAutomaticStartButAllowsExplicitRetry() {
        hostWithTrack("friend");
        audio.ready();
        service.play();
        scheduler.advance(120_000);
        service.onMessage("friend", PeerMessage.trackReady(audio.loadId));
        assertEquals(0, transport.playCount());
        service.play();
        assertEquals(1, transport.playCount());
    }

    @Test
    void clientAnnouncesReadinessOnlyAfterMatchingClockResponse() {
        connectClient();
        scheduler.advance(0);
        PeerMessage request = transport.sent.getFirst();
        service.onMessage("host", remotePlaylist("load-1"));
        audio.ready();
        assertEquals(0, transport.readyCount());
        service.onMessage("host", PeerMessage.timeSyncResponse("unrelated", request.clientSentAtMillis(), 10_000, 10_000));
        assertEquals(0, transport.readyCount());
        service.onMessage("host", PeerMessage.timeSyncResponse(request.nonce(), request.clientSentAtMillis(), 10_000, 10_000));
        assertEquals(1, transport.readyCount());
    }

    @Test
    void clientNeverQueuesPlayForUnloadedMedia() {
        connectClient();
        service.onMessage("host", remotePlaylist("load-1"));
        service.onMessage("host", PeerMessage.playAt("load-1", 0, 10_750));
        audio.ready();
        scheduler.advance(1_000);
        assertEquals(0, audio.plays);
    }

    @Test
    void clientPausesAndCancelsPlaybackWhenHostDisconnects() {
        readyClient();
        service.onMessage("host", PeerMessage.playAt("load-1", 0, 10_750));
        service.onPeerDisconnected("host");
        scheduler.advance(1_000);
        assertEquals(0, audio.plays);
        assertTrue(audio.stops > 0);
    }

    @Test
    void clientRejectsPlaybackCommandsForOldLoad() {
        readyClient();
        service.onMessage("host", PeerMessage.playAt("old-load", 0, 10_750));
        scheduler.advance(1_000);
        assertEquals(0, audio.plays);
    }

    @Test
    void clientCanPlayAtAgreedTimeAfterReadiness() {
        readyClient();
        service.onMessage("host", PeerMessage.playAt("load-1", 1_000, 10_750));
        scheduler.advance(749);
        assertEquals(0, audio.plays);
        scheduler.advance(1);
        assertEquals(1, audio.plays);
        assertEquals(Duration.ofSeconds(1), audio.position);
    }

    @Test
    void sendsClockResponseOnlyToRequestingListener() {
        hostWithTrack("one", "two");
        transport.directed.clear();
        service.onMessage("two", PeerMessage.timeSyncRequest(5_000));
        assertEquals(List.of("two"), transport.directed);
    }

    private void hostWithTrack(String... peers) {
        service.addTrackUrl("https://example.com/song.mp3");
        service.startHost("127.0.0.1", 47321);
        for (String peer : peers) service.onPeerConnected(peer);
    }

    @Test
    void seekDuringPlaybackPausesAndSchedulesTheNewPositionForEveryone() {
        hostWithTrack("friend");
        audio.ready();
        service.onMessage("friend", PeerMessage.trackReady(audio.loadId));
        service.play();
        scheduler.advance(750);
        transport.broadcasts.clear();
        service.seek(4_000);
        assertEquals(List.of(MessageType.PAUSE, MessageType.SEEK, MessageType.PLAY_AT),
            transport.broadcasts.stream().map(PeerMessage::type).toList());
        assertEquals(1, audio.plays);
        scheduler.advance(750);
        assertEquals(2, audio.plays);
        assertEquals(Duration.ofSeconds(4), audio.position);
    }

    @Test
    void transportFailureCancelsStartAndSendsPause() {
        hostWithTrack();
        audio.ready();
        service.play();
        service.onTransportError("Connection failed", new IllegalStateException());
        scheduler.advance(1_000);
        assertEquals(0, audio.plays);
        assertEquals(MessageType.PAUSE, transport.broadcasts.getLast().type());
    }

    @Test
    void nextTrackKeepsPlayingButWaitsForNewReadiness() {
        hostWithTrack("friend");
        service.addTrackUrl("https://example.com/second.mp3");
        audio.ready();
        service.onMessage("friend", PeerMessage.trackReady(audio.loadId));
        service.play();
        scheduler.advance(750);
        String previousLoad = audio.loadId;
        service.nextTrack();
        assertEquals(1, service.currentState().currentTrackIndex());
        assertNotEquals(previousLoad, audio.loadId);
        audio.ready();
        scheduler.advance(1_000);
        assertEquals(1, audio.plays);
        service.onMessage("friend", PeerMessage.trackReady(audio.loadId));
        scheduler.advance(750);
        assertEquals(2, audio.plays);
    }

    @Test
    void nextTrackWhilePausedDoesNotStartAndStopsAtEndOfPlaylist() {
        hostWithTrack();
        service.addTrackUrl("https://example.com/second.mp3");
        service.nextTrack();
        audio.ready();
        String lastLoad = audio.loadId;
        service.nextTrack();
        scheduler.advance(1_000);
        assertEquals(1, service.currentState().currentTrackIndex());
        assertEquals(lastLoad, audio.loadId);
        assertEquals(0, audio.plays);
    }

    @Test
    void stopRewindsEveryoneAndDoesNotResume() {
        hostWithTrack();
        audio.ready();
        service.play();
        scheduler.advance(750);
        audio.position = Duration.ofSeconds(12);
        service.stopPlayback();
        scheduler.advance(1_000);
        assertEquals(Duration.ZERO, audio.position);
        assertEquals(0, service.currentState().positionMillis());
        assertEquals(PlaybackStatus.PAUSED, service.currentState().playbackStatus());
        assertEquals(MessageType.SEEK, transport.broadcasts.getLast().type());
        assertEquals(0, transport.broadcasts.getLast().positionMillis());
        assertEquals(1, audio.plays);
    }

    @Test
    void stopCancelsWaitingAndScheduledPlayback() {
        hostWithTrack();
        service.play();
        service.stopPlayback();
        audio.ready();
        scheduler.advance(1_000);
        assertEquals(0, audio.plays);
        service.play();
        service.stopPlayback();
        scheduler.advance(1_000);
        assertEquals(0, audio.plays);
    }

    @Test
    void compactPlaybackActionsCannotControlAClientSession() {
        readyClient();
        int pauses = audio.pauses;
        String load = audio.loadId;
        service.nextTrack();
        service.stopPlayback();
        assertEquals(load, audio.loadId);
        assertEquals(pauses, audio.pauses);
        assertEquals(0, transport.playCount());
    }

    @Test
    void readyCallbackAfterAnErrorCannotReviveTheFailedLoad() {
        hostWithTrack();
        service.onError(audio.loadId, "failed", new IllegalStateException());
        audio.ready();
        service.play();
        assertEquals(PlaybackStatus.ERROR, service.currentState().playbackStatus());
        assertEquals(0, transport.playCount());
    }

    @Test
    void reconnectRequiresANewClockSampleBeforeAnnouncingReadiness() {
        readyClient();
        assertEquals(1, transport.readyCount());
        service.disconnect();
        connectClient();
        service.onMessage("host", remotePlaylist("load-2"));
        audio.ready();
        assertEquals(1, transport.readyCount());
        scheduler.advance(0);
        PeerMessage request = transport.sent.getLast();
        service.onMessage("host", PeerMessage.timeSyncResponse(request.nonce(), request.clientSentAtMillis(), 10_000, 10_000));
        assertEquals(2, transport.readyCount());
    }

    private void connectClient() {
        service.connectToHost("127.0.0.1", 47321);
        service.onPeerConnected("host");
    }

    private void readyClient() {
        connectClient();
        scheduler.advance(0);
        PeerMessage request = transport.sent.getFirst();
        service.onMessage("host", PeerMessage.timeSyncResponse(request.nonce(), request.clientSentAtMillis(), 10_000, 10_000));
        service.onMessage("host", remotePlaylist("load-1"));
        audio.ready();
    }

    private PeerMessage remotePlaylist(String loadId) {
        return PeerMessage.playlistUpdated(List.of(new PlaylistTrack("track-1", "Song", "https://example.com/song.mp3")), 0, loadId);
    }

    private static final class FakeAudio implements AudioPlayerPort {
        AudioPlayerListener listener;
        String loadId;
        Duration position = Duration.ZERO;
        int plays, pauses, stops, loads;
        public void setListener(AudioPlayerListener listener) { this.listener = listener; }
        public void load(URI uri, String loadId) { this.loadId = loadId; loads++; position = Duration.ZERO; }
        void ready() { listener.onReady(loadId, Duration.ofMinutes(3)); }
        public void play() { plays++; }
        public void pause() { pauses++; }
        public void stop() { stops++; }
        public void seek(Duration position) { this.position = position; }
        public void setVolume(double volume) { }
        public double volume() { return 1; }
        public Duration currentPosition() { return position; }
        public Duration duration() { return Duration.ofMinutes(3); }
        public void close() { }
    }

    private static final class FakeTransport implements PeerTransportPort {
        final List<PeerMessage> sent = new ArrayList<>();
        final List<PeerMessage> broadcasts = new ArrayList<>();
        final List<String> directed = new ArrayList<>();
        public void setListener(PeerTransportListener listener) { }
        public void startHost(HostStartupConfig config) { }
        public void connect(PeerAddress address) { }
        public void send(PeerMessage message) { sent.add(message); }
        public void sendToPeer(String peerId, PeerMessage message) { directed.add(peerId); }
        public void broadcast(PeerMessage message) { broadcasts.add(message); }
        public void disconnectPeer(String peerId) { }
        public void disconnect() { }
        public void close() { }
        long playCount() { return broadcasts.stream().filter(m -> m.type() == MessageType.PLAY_AT).count(); }
        long readyCount() { return sent.stream().filter(m -> m.type() == MessageType.TRACK_READY).count(); }
    }

    /** Runs deadlines deterministically; no sleeping or networking in orchestration tests. */
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
        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            return schedule(command, initialDelay, unit);
        }
        void advance(long millis) {
            long target = now + millis;
            while (true) {
                ManualTask next = tasks.stream().filter(t -> !t.isDone() && t.deadline <= target)
                    .min(java.util.Comparator.comparingLong(t -> t.deadline)).orElse(null);
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
            public int compareTo(Delayed other) { return Long.compare(getDelay(TimeUnit.MILLISECONDS), other.getDelay(TimeUnit.MILLISECONDS)); }
        }
    }
}
