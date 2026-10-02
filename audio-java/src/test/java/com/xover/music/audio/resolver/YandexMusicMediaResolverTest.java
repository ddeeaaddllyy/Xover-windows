package com.xover.music.audio.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class YandexMusicMediaResolverTest {
    private final YandexMusicMediaResolver resolver = new YandexMusicMediaResolver();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void selectsHighestBitrateFullMp3() throws Exception {
        JsonNode variants = json.readTree("""
            [
              {"codec":"aac","bitrateInKbps":320,"downloadInfoUrl":"https://example.com/aac"},
              {"codec":"mp3","bitrateInKbps":128,"downloadInfoUrl":"https://example.com/low"},
              {"codec":"mp3","bitrateInKbps":320,"preview":true,"downloadInfoUrl":"https://example.com/preview"},
              {"codec":"mp3","bitrateInKbps":192,"downloadInfoUrl":"https://example.com/high"}
            ]
            """);

        assertEquals("https://example.com/high",
            resolver.chooseDownloadInfo(variants).orElseThrow().path("downloadInfoUrl").asText());
    }

    @Test
    void rejectsVariantsThatCannotProduceAnMp3Url() throws Exception {
        JsonNode variants = json.readTree("[{\"codec\":\"aac\",\"downloadInfoUrl\":\"https://example.com/aac\"}]");
        assertTrue(resolver.chooseDownloadInfo(variants).isEmpty());
    }
}
