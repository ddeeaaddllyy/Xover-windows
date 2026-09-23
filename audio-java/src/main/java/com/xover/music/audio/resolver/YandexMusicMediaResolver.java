package com.xover.music.audio.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xover.music.application.audio.error.AudioPlaybackException;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

final class YandexMusicMediaResolver {
    private static final String API_BASE_URL = "https://api.music.yandex.net";
    private static final String SIGN_SALT = "XGRlBW9FXlekgbPrRHuSiA";
    private static final String USER_AGENT = "Xover/1.5.1";

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    URI resolve(URI mediaUri) {
        Optional<String> trackId = MediaSourceDetector.yandexTrackId(mediaUri);
        if (trackId.isEmpty()) {
            return mediaUri;
        }

        try {
            String token = yandexToken();
            JsonNode downloadInfo = readJson(apiUri("/tracks/" + trackId.get() + "/download-info"), token);
            JsonNode selectedInfo = chooseDownloadInfo(downloadInfo.path("result"))
                .orElseThrow(() -> new IOException("Yandex Music returned no downloadable audio variants"));
            String downloadInfoUrl = selectedInfo.path("downloadInfoUrl").asText("");
            if (downloadInfoUrl.isBlank()) {
                throw new IOException("Yandex Music downloadInfoUrl is empty");
            }

            String xml = get(URI.create(downloadInfoUrl), token, "application/xml,text/xml,*/*").body();
            return URI.create(directMp3Url(xml));
        } catch (IOException ex) {
            throw AudioPlaybackException.resolutionFailed(mediaUri, ex);
        }
    }

    private Optional<JsonNode> chooseDownloadInfo(JsonNode variants) {
        if (!variants.isArray() || variants.isEmpty()) {
            return Optional.empty();
        }

        return stream(variants)
            .filter(variant -> !variant.path("preview").asBoolean(false))
            .max(Comparator
                .comparingInt(this::codecScore)
                .thenComparingInt(variant -> variant.path("bitrateInKbps").asInt(0)));
    }

    private java.util.stream.Stream<JsonNode> stream(JsonNode array) {
        java.util.List<JsonNode> nodes = new java.util.ArrayList<>();
        array.forEach(nodes::add);
        return nodes.stream();
    }

    private int codecScore(JsonNode variant) {
        String codec = variant.path("codec").asText("").toLowerCase(Locale.ROOT);
        return "mp3".equals(codec) ? 2 : 1;
    }

    private String directMp3Url(String xml) throws IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (javax.xml.parsers.ParserConfigurationException ignored) {
            // The parser still handles a trusted API response; unsupported hardening flags are non-fatal.
        }

        try {
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            String host = text(document, "host");
            String path = text(document, "path");
            String ts = text(document, "ts");
            String s = text(document, "s");

            if (host.isBlank() || path.isBlank() || ts.isBlank() || s.isBlank()) {
                throw new IOException("Yandex Music download info is incomplete");
            }

            String normalizedPath = path.startsWith("/") ? path : "/" + path;
            String sign = md5Hex(SIGN_SALT + normalizedPath.substring(1) + s);
            return "https://" + host + "/get-mp3/" + sign + "/" + ts + normalizedPath;
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("Could not parse Yandex Music download info", ex);
        }
    }

    private String text(Document document, String tagName) {
        var nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }

    private JsonNode readJson(URI uri, String token) throws IOException {
        return objectMapper.readTree(get(uri, token, "application/json").body());
    }

    private FetchResult get(URI uri, String token, String acceptHeader) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(25))
            .header("Accept", acceptHeader)
            .header("Authorization", "OAuth " + token)
            .header("User-Agent", USER_AGENT)
            .GET()
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + " for " + uri);
            }
            return new FetchResult(response.uri(), response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + uri, ex);
        }
    }

    private URI apiUri(String path) {
        return URI.create(API_BASE_URL + path);
    }

    private String yandexToken() throws IOException {
        return EnvFile.value()
            .orElseThrow(() -> new IOException(
                "Yandex Music token is missing. Add YANDEX_MUSIC_TOKEN=... to .env"
            ));
    }

    private String md5Hex(String value) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("MD5 is not available", ex);
        }
    }

    private record FetchResult(URI uri, String body) {
    }
}
