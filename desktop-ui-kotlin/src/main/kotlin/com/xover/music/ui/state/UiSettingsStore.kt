package com.xover.music.ui

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

internal data class UiSettings(
    val advertisedHost: String = "127.0.0.1",
    val connectHost: String = "127.0.0.1",
    val port: String = DefaultPort.toString(),
    val opacity: Float = 0.94f,
    val pinned: Boolean = false,
)

/** Non-secret preferences, independent of the installation or working directory. */
internal class UiSettingsStore(
    private val file: Path = Path.of(System.getProperty("user.home"), ".xover", "settings.properties"),
) {
    fun load(): UiSettings {
        if (!Files.exists(file)) return UiSettings()
        val properties = Properties()
        Files.newBufferedReader(file).use { properties.load(it) }
        val defaults = UiSettings()
        return UiSettings(
            advertisedHost = properties.getProperty("advertisedHost", defaults.advertisedHost).take(253),
            connectHost = properties.getProperty("connectHost", defaults.connectHost).take(253),
            port = properties.getProperty("port")?.toIntOrNull()
                ?.takeIf { it in 1..65535 }?.toString() ?: defaults.port,
            opacity = properties.getProperty("opacity")?.toFloatOrNull()
                ?.takeIf { it.isFinite() }?.coerceIn(0.78f, 0.98f) ?: defaults.opacity,
            pinned = properties.getProperty("pinned")?.toBooleanStrictOrNull() ?: defaults.pinned,
        )
    }

    @Synchronized
    fun save(settings: UiSettings) {
        val properties = Properties().apply {
            setProperty("advertisedHost", settings.advertisedHost)
            setProperty("connectHost", settings.connectHost)
            setProperty("port", settings.port)
            setProperty("opacity", settings.opacity.toString())
            setProperty("pinned", settings.pinned.toString())
        }
        Files.createDirectories(file.toAbsolutePath().parent)
        val temporary = Files.createTempFile(file.toAbsolutePath().parent, "settings-", ".tmp")
        try {
            Files.newBufferedWriter(temporary).use { properties.store(it, "Xover preferences") }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
