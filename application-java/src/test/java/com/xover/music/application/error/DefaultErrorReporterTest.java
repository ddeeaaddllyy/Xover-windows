package com.xover.music.application.error;

import com.xover.music.application.common.diagnostics.DefaultErrorReporter;
import com.xover.music.application.common.diagnostics.ErrorEvent;
import com.xover.music.application.common.error.XoverErrorCode;
import com.xover.music.application.audio.error.AudioPlaybackException;
import com.xover.music.application.playlist.error.InvalidTrackSourceException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DefaultErrorReporterTest {
    @Test
    void publishesTypedErrorEventsWithContextAndTrace() {
        DefaultErrorReporter reporter = new DefaultErrorReporter();
        List<ErrorEvent> events = new ArrayList<>();
        reporter.addListener(events::add);

        reporter.report("Add track", InvalidTrackSourceException.blank());

        assertEquals(1, events.size());
        ErrorEvent event = events.getFirst();
        assertEquals(XoverErrorCode.VALIDATION, event.code());
        assertEquals("Add track", event.context().get("operation"));
        assertTrue(event.trace().contains("InvalidTrackSourceException"));
    }

    @Test
    void redactsSensitiveValuesFromErrorEvents() {
        DefaultErrorReporter reporter = new DefaultErrorReporter();
        List<ErrorEvent> events = new ArrayList<>();
        reporter.addListener(events::add);

        reporter.report(
            "Load https://example.test/audio.mp3?token=secret-token",
            AudioPlaybackException.resolutionFailed(
                URI.create("https://example.test/audio.mp3?token=secret-token&safe=1"),
                new IOException("Authorization: OAuth secret-oauth-token")
            )
        );

        ErrorEvent event = events.getFirst();
        assertTrue(event.message().contains("token=<redacted>"));
        assertTrue(event.context().get("sourceUri").contains("token=<redacted>"));
        assertTrue(event.trace().contains("OAuth <redacted>"));
        assertTrue(event.context().get("operation").contains("token=<redacted>"));
        assertTrue(!event.message().contains("secret-token"));
        assertTrue(!event.trace().contains("secret-oauth-token"));
    }
}
