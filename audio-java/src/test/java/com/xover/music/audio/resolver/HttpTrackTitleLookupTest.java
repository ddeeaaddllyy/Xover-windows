package com.xover.music.audio.resolver;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HttpTrackTitleLookupTest {
    @Test
    void readsSocialTitleRegardlessOfAttributeOrderAndDecodesEntities() {
        String html = "<html><head><meta content='Artist &amp; Friend — Song' property='og:title'></head></html>";
        assertEquals(Optional.of("Artist & Friend — Song"), HttpTrackTitleLookup.titleFromPage(html));
    }

    @Test
    void ignoresPagesWithoutTrackTitleMetadata() {
        assertEquals(Optional.empty(), HttpTrackTitleLookup.titleFromPage("<title>Sign in</title>"));
    }
}
