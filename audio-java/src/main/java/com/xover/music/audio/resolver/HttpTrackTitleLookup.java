package com.xover.music.audio.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xover.music.application.playlist.TrackTitleLookup;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fetches only bounded metadata, never audio bytes. */
public final class HttpTrackTitleLookup implements TrackTitleLookup {
    private static final int MAX_RESPONSE_BYTES = 192 * 1024;
    private static final Pattern META_TAG = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w:-]+)\\s*=\\s*([\\\"'])(.*?)\\2", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public Optional<String> lookup(URI sourceUri) throws Exception {
        return switch (MediaSourceDetector.detect(sourceUri)) {
            case SOUNDCLOUD -> soundCloudTitle(sourceUri);
            case YANDEX_MUSIC -> yandexTitle(sourceUri);
            case DIRECT_AUDIO -> Optional.empty();
        };
    }

    private Optional<String> soundCloudTitle(URI sourceUri) throws IOException, InterruptedException {
        URI uri = URI.create("https://soundcloud.com/oembed?format=json&url="
            + URLEncoder.encode(sourceUri.toString(), StandardCharsets.UTF_8));
        return clean(json.readTree(fetch(uri, Optional.empty())).path("title").asText(""));
    }

    private Optional<String> yandexTitle(URI sourceUri) throws IOException, InterruptedException {
        Optional<String> token = EnvFile.value();
        Optional<String> id = MediaSourceDetector.yandexTrackId(sourceUri);
        if (token.isPresent() && id.isPresent()) {
            try {
                URI api = URI.create("https://api.music.yandex.net/tracks/" + id.get());
                JsonNode result = json.readTree(fetch(api, token)).path("result");
                if (result.isArray() && !result.isEmpty()) {
                    JsonNode track = result.get(0);
                    String title = track.path("title").asText("");
                    JsonNode artists = track.path("artists");
                    if (!title.isBlank()) {
                        String artist = artists.isArray() && !artists.isEmpty() ? artists.get(0).path("name").asText("") : "";
                        return clean(artist.isBlank() ? title : artist + " — " + title);
                    }
                }
            } catch (IOException ignored) {
                // Public page metadata may still be available.
            }
        }
        return titleFromPage(fetch(sourceUri, Optional.empty()))
            .filter(title -> !title.equalsIgnoreCase("Яндекс Музыка") && !title.equalsIgnoreCase("Yandex Music"));
    }

    private String fetch(URI uri, Optional<String> token) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
            .GET()
            .timeout(Duration.ofSeconds(7))
            .header("User-Agent", "Xover/2.8")
            .header("Accept", "application/json,text/html;q=0.9");
        token.ifPresent(value -> request.header("Authorization", "OAuth " + value));
        HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Metadata request returned HTTP " + response.statusCode());
            }
            byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw new IOException("Metadata response is too large");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    static Optional<String> titleFromPage(String html) {
        Matcher tags = META_TAG.matcher(html);
        while (tags.find()) {
            Matcher attributes = ATTRIBUTE.matcher(tags.group());
            String property = "";
            String content = "";
            while (attributes.find()) {
                if (attributes.group(1).equalsIgnoreCase("property") || attributes.group(1).equalsIgnoreCase("name")) {
                    property = attributes.group(3);
                } else if (attributes.group(1).equalsIgnoreCase("content")) {
                    content = attributes.group(3);
                }
            }
            if (property.equalsIgnoreCase("og:title") || property.equalsIgnoreCase("twitter:title")) {
                Optional<String> title = clean(decodeHtml(content));
                if (title.isPresent()) return title;
            }
        }
        return Optional.empty();
    }

    private static String decodeHtml(String value) {
        return value.replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
    }

    private static Optional<String> clean(String value) {
        String title = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        return title.isEmpty() || title.length() > 160 ? Optional.empty() : Optional.of(title);
    }
}
