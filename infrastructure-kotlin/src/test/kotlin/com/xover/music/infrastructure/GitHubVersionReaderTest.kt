package com.xover.music.infrastructure

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets

class GitHubVersionReaderTest {
    @Test
    fun parsesVersionSectionAndIgnoresFutureSettings() {
        val parsed = VersionTomlParser.parse(
            """
            # Values published for the application
            [versions]
            current = "1.8.4" # stable
            latest = "2.0.0-rc1"
            allow_preview = false

            [release.notes]
            text = "Later"
            """.trimIndent(),
        )
        assertEquals("1.8.4", parsed.current())
        assertEquals("2.0.0-rc1", parsed.latest())
    }

    @Test
    fun rejectsMissingVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            VersionTomlParser.parse("[versions]\ncurrent = \"1.8.4\"")
        }
    }

    @Test
    fun readsPublishedTomlOverHttp() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/version.toml") { exchange ->
            val body = "[versions]\ncurrent = \"1.8.4\"\nlatest = \"2.0.0\"\n".toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val versions = GitHubVersionReader(URI.create("http://127.0.0.1:${server.address.port}/version.toml")).read()
            assertEquals("1.8.4", versions.current())
            assertEquals("2.0.0", versions.latest())
        } finally {
            server.stop(0)
        }
    }
}
