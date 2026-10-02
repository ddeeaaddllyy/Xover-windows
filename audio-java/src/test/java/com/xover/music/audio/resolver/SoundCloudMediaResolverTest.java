package com.xover.music.audio.resolver;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SoundCloudMediaResolverTest {
    private final SoundCloudMediaResolver resolver = new SoundCloudMediaResolver();

    @Test
    void keepsPageClientIdWhenPlayerScriptCannotBeRead() throws Exception {
        String id = "12345678901234567890123456789012";
        String page = "client_id='" + id + "'<script src='https://a-v2.sndcdn.com/assets/app.js'></script>";

        assertEquals(List.of(id), resolver.clientIds(page, uri -> {
            throw new IOException("Script unavailable");
        }));
    }

    @Test
    void usesOtherPlayerScriptsWhenOneIsUnavailable() throws Exception {
        String id = "12345678901234567890123456789012";
        String page = "<script src='https://a-v2.sndcdn.com/assets/one.js'></script>"
            + "<script src='https://a-v2.sndcdn.com/assets/two.js'></script>";

        assertEquals(List.of(id), resolver.clientIds(page, uri -> {
            if (uri.getPath().endsWith("two.js")) throw new IOException("Script unavailable");
            return "client_id='" + id + "'";
        }));
    }

    @Test
    void reportsScriptFailureWhenNoClientIdWasFound() {
        String page = "<script src='https://a-v2.sndcdn.com/assets/app.js'></script>";

        assertThrows(IOException.class, () -> resolver.clientIds(page, uri -> {
            throw new IOException("Script unavailable");
        }));
    }
}
