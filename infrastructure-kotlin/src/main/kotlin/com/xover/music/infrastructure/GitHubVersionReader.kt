package com.xover.music.infrastructure

import com.xover.music.application.version.RemoteVersionPort
import com.xover.music.application.version.RemoteVersions
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class GitHubVersionReader(
    private val source: URI = URI.create("https://raw.githubusercontent.com/ddeeaaddllyy/Xover-windows/main/version.toml"),
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
) : RemoteVersionPort {
    override fun read(): RemoteVersions {
        val request = HttpRequest.newBuilder(source)
            .timeout(Duration.ofSeconds(8))
            .header("Cache-Control", "no-cache")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() == 200) { "Could not load version.toml: HTTP ${response.statusCode()}" }
        require(response.body().length <= 32_768) { "version.toml is too large" }
        return VersionTomlParser.parse(response.body())
    }
}

/** Reads only the string values used by the version display; other TOML sections are ignored. */
internal object VersionTomlParser {
    private val sectionPattern = Regex("^\\[([A-Za-z0-9_.-]+)](?:\\s*#.*)?$")
    private val valuePattern = Regex("^([A-Za-z0-9_-]+)\\s*=\\s*\"([A-Za-z0-9.+_-]+)\"(?:\\s*#.*)?$")
    private val versionPattern = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?$")

    fun parse(source: String): RemoteVersions {
        var section = ""
        val versions = mutableMapOf<String, String>()
        source.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trim().removePrefix("\uFEFF")
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            if (line.startsWith("[")) {
                section = requireNotNull(sectionPattern.matchEntire(line)?.groupValues?.get(1)) {
                    "Invalid TOML section at line ${index + 1}"
                }
                return@forEachIndexed
            }
            if (section != "versions") return@forEachIndexed
            val key = line.substringBefore('=').trim()
            if (key != "current" && key != "latest") return@forEachIndexed
            val match = requireNotNull(valuePattern.matchEntire(line)) {
                "Invalid version value at line ${index + 1}"
            }
            require(versions.putIfAbsent(key, match.groupValues[2]) == null) { "Duplicate version key: $key" }
        }
        val current = requireNotNull(versions["current"]) { "Missing versions.current" }
        val latest = requireNotNull(versions["latest"]) { "Missing versions.latest" }
        require(versionPattern.matches(current) && versionPattern.matches(latest)) {
            "Versions must use major.minor.patch format"
        }
        return RemoteVersions(current, latest)
    }
}
