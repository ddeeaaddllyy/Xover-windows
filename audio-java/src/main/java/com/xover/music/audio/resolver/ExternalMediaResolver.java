package com.xover.music.audio.resolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

final class ExternalMediaResolver {
    Optional<URI> resolveWithYtDlp(URI mediaUri) throws IOException {
        Optional<String> command = command();
        if (command.isEmpty()) {
            return Optional.empty();
        }

        ProcessBuilder builder = new ProcessBuilder(
            command.get(),
            "--no-playlist",
            "--get-url",
            "-f",
            "bestaudio[ext=mp3][protocol^=http]/bestaudio[ext=mp3]/bestaudio/best",
            mediaUri.toString()
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

            boolean finished = process.waitFor(55, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException(command.get() + " timed out for " + mediaUri);
            }

            String response = new String(output.get(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IOException(command.get() + " failed for " + mediaUri + ": " + response);
            }

            return response.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("http://") || line.startsWith("https://"))
                .findFirst()
                .map(URI::create);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running " + command.get(), ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof UncheckedIOException unchecked) {
                throw new IOException("Could not read " + command.get() + " output", unchecked.getCause());
            }
            throw new IOException(command.get() + " failed", cause);
        }
    }

    private Optional<String> command() {
        String configured = System.getenv("XOVER_YT_DLP");
        if (configured != null && !configured.isBlank()) {
            return Optional.of(configured.trim());
        }

        List<String> candidates = isWindows()
            ? Arrays.asList("yt-dlp.exe", "yt-dlp", "youtube-dl.exe", "youtube-dl")
            : Arrays.asList("yt-dlp", "youtube-dl");

        for (String candidate : candidates) {
            if (isCommandAvailable(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private boolean isCommandAvailable(String candidate) {
        ProcessBuilder builder = isWindows()
            ? new ProcessBuilder("where.exe", candidate)
            : new ProcessBuilder("sh", "-lc", "command -v " + candidate);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            return process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
