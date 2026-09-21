package com.xover.music.application.error;

import org.junit.jupiter.api.Test;

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
}
