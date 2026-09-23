package com.xover.music.audio.resolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

final class EnvFile {
    private static final AtomicReference<Map<String, String>> CACHE = new AtomicReference<>();

    private EnvFile() {
    }

    static Optional<String> value() {
        String environmentValue = System.getenv("YANDEX_MUSIC_TOKEN");
        if (environmentValue != null && !environmentValue.isBlank()) {
            return Optional.of(environmentValue.trim());
        }
        return Optional.ofNullable(values().get("YANDEX_MUSIC_TOKEN")).filter(value -> !value.isBlank());
    }

    private static Map<String, String> values() {
        Map<String, String> cached = CACHE.get();
        if (cached != null) {
            return cached;
        }

        Map<String, String> loaded = load();
        CACHE.compareAndSet(null, loaded);
        return CACHE.get();
    }

    private static Map<String, String> load() {
        for (Path candidate : candidateFiles()) {
            if (Files.isRegularFile(candidate)) {
                return read(candidate);
            }
        }
        return Map.of();
    }

    private static Iterable<Path> candidateFiles() {
        Map<Path, Path> candidates = new java.util.LinkedHashMap<>();
        addWithParents(candidates, Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize());

        String appHome = System.getProperty("app.home", "");
        if (!appHome.isBlank()) {
            addWithParents(candidates, Path.of(appHome).toAbsolutePath().normalize());
        }

        return candidates.keySet().stream()
            .map(path -> path.resolve(".env"))
            .toList();
    }

    private static void addWithParents(Map<Path, Path> candidates, Path start) {
        Path current = Files.isRegularFile(start) ? start.getParent() : start;
        int depth = 0;
        while (current != null && depth < 8) {
            candidates.putIfAbsent(current, current);
            current = current.getParent();
            depth++;
        }
    }

    private static Map<String, String> read(Path file) {
        Map<String, String> values = new HashMap<>();
        try {
            for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                parseLine(rawLine).ifPresent(entry -> values.put(entry.key(), entry.value()));
            }
        } catch (IOException ignored) {
            return Map.of();
        }
        return Map.copyOf(values);
    }

    private static Optional<Entry> parseLine(String rawLine) {
        if (rawLine == null) {
            return Optional.empty();
        }

        String line = rawLine.strip();
        if (line.isBlank() || line.startsWith("#")) {
            return Optional.empty();
        }
        if (line.startsWith("export ")) {
            line = line.substring("export ".length()).strip();
        }

        int separatorIndex = line.indexOf('=');
        if (separatorIndex <= 0) {
            return Optional.empty();
        }

        String key = line.substring(0, separatorIndex).strip();
        String value = line.substring(separatorIndex + 1).strip();
        if ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }

        return key.isBlank() ? Optional.empty() : Optional.of(new Entry(key, value));
    }

    private record Entry(String key, String value) {
    }
}
