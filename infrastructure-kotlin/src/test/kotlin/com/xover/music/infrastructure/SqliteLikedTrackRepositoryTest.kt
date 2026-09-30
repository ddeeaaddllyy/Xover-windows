package com.xover.music.infrastructure

import com.xover.music.domain.LikedTrack
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class SqliteLikedTrackRepositoryTest {
    @TempDir
    lateinit var directory: Path

    private fun repository() = SqliteLikedTrackRepository(directory.resolve("profile/liked-tracks.db"))

    @Test
    fun createsDatabaseAndPersistsLinksAcrossRepositoryInstances() {
        val first = repository()
        assertTrue(first.findAll().isEmpty())
        val track = LikedTrack("https://example.com/one.mp3", "Любимый трек")
        first.save(track)
        assertEquals(listOf(track), repository().findAll())
        val header = Files.readAllBytes(directory.resolve("profile/liked-tracks.db")).take(15).toByteArray()
        assertEquals("SQLite format 3", String(header))
    }

    @Test
    fun deduplicatesByUrlAndUpdatesTitleWithoutReordering() {
        val repository = repository()
        repository.save(LikedTrack("https://example.com/one", "One"))
        repository.save(LikedTrack("https://example.com/two", "Two"))
        repository.save(LikedTrack("https://example.com/one", "Updated"))
        assertEquals(listOf("Two", "Updated"), repository.findAll().map { it.title() })
    }

    @Test
    fun removesOnlyRequestedUrlAndAllowsReliking() {
        val repository = repository()
        val track = LikedTrack("https://example.com/one", "One")
        repository.save(track)
        repository.save(LikedTrack("https://example.com/two", "Two"))
        repository.remove(track.sourceUrl())
        repository.remove(track.sourceUrl())
        assertEquals(listOf("Two"), repository().findAll().map { it.title() })
        repository.save(track)
        assertEquals(listOf("One", "Two"), repository().findAll().map { it.title() })
    }

    @Test
    fun storesQuotesAsDataAndDoesNotInterpretSql() {
        val track = LikedTrack("https://example.com/it's.mp3", "'); DROP TABLE liked_tracks; --")
        repository().save(track)
        repository().remove("' OR 1=1 --")
        assertEquals(listOf(track), repository().findAll())
    }

    @Test
    fun concurrentInstancesDoNotDuplicateLinks() {
        repository().findAll()
        Executors.newFixedThreadPool(2).use { executor ->
            val tasks = (1..12).map {
                Callable { repository().save(LikedTrack("https://example.com/one", "One")) }
            }
            executor.invokeAll(tasks).forEach { it.get() }
        }
        assertEquals(1, repository().findAll().size)
    }

    @Test
    fun reportsCorruptDatabaseWithoutReplacingIt() {
        val file = directory.resolve("broken.db")
        val content = "This is not a database"
        Files.writeString(file, content)
        assertThrows(IllegalStateException::class.java) { SqliteLikedTrackRepository(file).findAll() }
        assertEquals(content, Files.readString(file))
    }
}
