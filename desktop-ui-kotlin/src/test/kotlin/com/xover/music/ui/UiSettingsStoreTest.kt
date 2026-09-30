package com.xover.music.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class UiSettingsStoreTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `restores Addresses And Opacity From A New Store Instance`() {
        val file = directory.resolve("profile/settings.properties")
        val settings = UiSettings("26.10.20.30", "26.40.50.60", "48484", 0.82f, true)
        UiSettingsStore(file).save(settings)
        assertEquals(settings, UiSettingsStore(file).load())
    }

    @Test
    fun usesDefaultsWhenFileIsMissing() {
        assertEquals(UiSettings(), UiSettingsStore(directory.resolve("missing.properties")).load())
    }

    @Test
    fun ignoresInvalidValuesAndBoundsOpacity() {
        val file = directory.resolve("settings.properties")
        Files.writeString(file, "port=99999\nopacity=NaN\npinned=broken\n")
        assertEquals(UiSettings(), UiSettingsStore(file).load())
        Files.writeString(file, "opacity=0.1\n")
        assertEquals(0.78f, UiSettingsStore(file).load().opacity)
    }

    @Test
    fun overwritesAtomicallyWithoutLeavingTemporaryFiles() {
        val file = directory.resolve("settings.properties")
        val store = UiSettingsStore(file)
        store.save(UiSettings(opacity = 0.8f))
        store.save(UiSettings(opacity = 0.9f))
        assertEquals(0.9f, store.load().opacity)
        Files.list(directory).use { assertEquals(listOf(file), it.toList()) }
    }
}
