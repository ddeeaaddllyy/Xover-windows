package com.xover.music.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SoundCloudMediaResolver {
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
        "<script[^>]+src=[\"'](https://a-v2\\.sndcdn\\.com/assets/[^\"']+\\.js)[\"']",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CLIENT_ID_PATTERN = Pattern.compile(
        "[\"']?(?:client_id|clientId)[\"']?\\s*[:=]\\s*[\"']([A-Za-z0-9]{32})[\"']"
    );
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Xover/1.2";
    private static final String ACCEPT_HEADER = "application/json,text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private static final String CURL_EFFECTIVE_URL_MARKER = "\nXOVER_EFFECTIVE_URL:";

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> cachedClientId = new AtomicReference<>("");

    URI resolve(URI mediaUri) {
        if (!isSoundCloudPage(mediaUri)) {
            return mediaUri;
        }

        try {
            FetchResult pageResponse = get(mediaUri);
            String page = pageResponse.body();
            String pageUrl = pageResponse.uri().toString();
            List<String> clientIds = clientIds(page);
            IOException lastFailure = null;

            for (String clientId : clientIds) {
                try {
                    return resolveWithClientId(pageUrl, clientId);
                } catch (IOException ex) {
                    lastFailure = ex;
                    cachedClientId.compareAndSet(clientId, "");
                }
            }

            if (lastFailure != null) {
                throw lastFailure;
            }
            throw new IOException("SoundCloud client id was not found");
        } catch (IOException ex) {
            throw new IllegalStateException("Could not resolve SoundCloud URL", ex);
        }
    }

    private URI resolveWithClientId(String pageUrl, String clientId) throws IOException {
        String resolveUrl = "https://api-v2.soundcloud.com/resolve?url="
            + URLEncoder.encode(pageUrl, StandardCharsets.UTF_8)
            + "&client_id="
            + URLEncoder.encode(clientId, StandardCharsets.UTF_8);
        JsonNode resolved = readJson(URI.create(resolveUrl));
        JsonNode transcodings = resolved.path("media").path("transcodings");
        if (!transcodings.isArray() || transcodings.isEmpty()) {
            throw new IOException("SoundCloud URL did not resolve to a playable track");
        }

        JsonNode transcoding = chooseTranscoding(transcodings)
            .orElseThrow(() -> new IOException("SoundCloud track has no supported stream"));
        String transcodingUrl = transcoding.path("url").asText("");
        if (transcodingUrl.isBlank()) {
            throw new IOException("SoundCloud stream URL is empty");
        }

        JsonNode streamInfo = readJson(URI.create(withClientId(transcodingUrl, clientId)));
        String streamUrl = streamInfo.path("url").asText("");
        if (streamUrl.isBlank()) {
            throw new IOException("SoundCloud stream endpoint returned no URL");
        }

        cachedClientId.set(clientId);
        return URI.create(streamUrl);
    }

    private Optional<JsonNode> chooseTranscoding(JsonNode transcodings) {
        Optional<JsonNode> progressiveMp3 = findTranscoding(transcodings, "progressive", "audio/mpeg");
        if (progressiveMp3.isPresent()) {
            return progressiveMp3;
        }

        Optional<JsonNode> progressive = findTranscoding(transcodings, "progressive", "");
        if (progressive.isPresent()) {
            return progressive;
        }

        Optional<JsonNode> hlsMp3 = findTranscoding(transcodings, "hls", "audio/mpeg");
        if (hlsMp3.isPresent()) {
            return hlsMp3;
        }

        return Optional.of(transcodings.get(0));
    }

    private Optional<JsonNode> findTranscoding(JsonNode transcodings, String protocol, String mimeType) {
        for (JsonNode transcoding : transcodings) {
            String candidateProtocol = transcoding.path("format").path("protocol").asText("");
            String candidateMimeType = transcoding.path("format").path("mime_type").asText("");
            boolean protocolMatches = protocol.isBlank() || protocol.equalsIgnoreCase(candidateProtocol);
            boolean mimeMatches = mimeType.isBlank() || candidateMimeType.toLowerCase(Locale.ROOT).contains(mimeType);
            if (protocolMatches && mimeMatches) {
                return Optional.of(transcoding);
            }
        }
        return Optional.empty();
    }

    private List<String> clientIds(String page) throws IOException {
        List<String> clientIds = new ArrayList<>();
        String cached = cachedClientId.get();
        if (!cached.isBlank()) {
            clientIds.add(cached);
        }

        addClientIdsFromText(page, clientIds);

        Matcher scriptMatcher = SCRIPT_PATTERN.matcher(page);
        List<String> scripts = new ArrayList<>();
        while (scriptMatcher.find()) {
            scripts.add(scriptMatcher.group(1));
        }

        for (int i = scripts.size() - 1; i >= 0; i--) {
            String script = get(URI.create(scripts.get(i))).body();
            addClientIdsFromText(script, clientIds);
        }

        return clientIds;
    }

    private void addClientIdsFromText(String text, List<String> clientIds) {
        Matcher clientIdMatcher = CLIENT_ID_PATTERN.matcher(text);
        while (clientIdMatcher.find()) {
            String clientId = clientIdMatcher.group(1);
            if (!clientIds.contains(clientId)) {
                clientIds.add(clientId);
            }
        }
    }

    private JsonNode readJson(URI uri) throws IOException {
        return objectMapper.readTree(get(uri).body());
    }

    private FetchResult get(URI uri) throws IOException {
        try {
            return getWithHttpClient(uri);
        } catch (IOException ex) {
            return getWithCurl(uri, ex);
        }
    }

    private FetchResult getWithHttpClient(URI uri) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(20))
            .header("Accept", ACCEPT_HEADER)
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

    private FetchResult getWithCurl(URI uri, IOException javaHttpFailure) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(
            curlCommand(),
            "-L",
            "--silent",
            "--show-error",
            "--max-time",
            "35",
            "-A",
            USER_AGENT,
            "-H",
            "Accept: " + ACCEPT_HEADER,
            "-w",
            CURL_EFFECTIVE_URL_MARKER + "%{url_effective}",
            uri.toString()
        );
        builder.redirectErrorStream(true);

        try {
            Process process = builder.start();
            CompletableFuture<byte[]> output = CompletableFuture.supplyAsync(() -> {
                try {
                    return process.getInputStream().readAllBytes();
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });

            boolean finished = process.waitFor(40, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("curl timed out for " + uri, javaHttpFailure);
            }

            String response = new String(output.get(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IOException("curl failed for " + uri + ": " + response, javaHttpFailure);
            }

            int markerIndex = response.lastIndexOf(CURL_EFFECTIVE_URL_MARKER);
            if (markerIndex < 0) {
                throw new IOException("curl returned no effective URL for " + uri, javaHttpFailure);
            }

            String body = response.substring(0, markerIndex);
            String effectiveUrl = response.substring(markerIndex + CURL_EFFECTIVE_URL_MARKER.length()).trim();
            return new FetchResult(URI.create(effectiveUrl), body);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + uri + " with curl", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof UncheckedIOException unchecked) {
                throw new IOException("Could not read curl output for " + uri, unchecked.getCause());
            }
            throw new IOException("curl failed for " + uri, cause);
        } catch (IOException ex) {
            ex.addSuppressed(javaHttpFailure);
            throw ex;
        }
    }

    private boolean isSoundCloudPage(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return normalizedHost.endsWith("soundcloud.com")
            && !normalizedHost.startsWith("api.")
            && !normalizedHost.startsWith("api-v2.");
    }

    private String withClientId(String url, String clientId) {
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8);
    }

    private String curlCommand() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return osName.contains("win") ? "curl.exe" : "curl";
    }

    private record FetchResult(URI uri, String body) {
    }
}
