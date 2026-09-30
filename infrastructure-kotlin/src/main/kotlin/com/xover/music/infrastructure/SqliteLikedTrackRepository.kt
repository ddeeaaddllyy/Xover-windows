package com.xover.music.infrastructure

import com.xover.music.application.library.LikedTrackRepository
import com.xover.music.domain.LikedTrack
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/** Short-lived connections leave no database handle open when the application exits. */
class SqliteLikedTrackRepository(private val file: Path) : LikedTrackRepository {
    override fun findAll(): List<LikedTrack> = database { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT source_url, title FROM liked_tracks ORDER BY id DESC").use { rows ->
                buildList {
                    while (rows.next()) add(LikedTrack(rows.getString("source_url"), rows.getString("title")))
                }
            }
        }
    }

    override fun save(track: LikedTrack) {
        database { connection ->
            connection.prepareStatement(
                "INSERT INTO liked_tracks(source_url, title) VALUES (?, ?) " +
                    "ON CONFLICT(source_url) DO UPDATE SET title = excluded.title",
            ).use { statement ->
                statement.setString(1, track.sourceUrl())
                statement.setString(2, track.title())
                statement.executeUpdate()
            }
        }
    }

    override fun remove(sourceUrl: String) {
        database { connection ->
            connection.prepareStatement("DELETE FROM liked_tracks WHERE source_url = ?").use { statement ->
                statement.setString(1, sourceUrl)
                statement.executeUpdate()
            }
        }
    }

    private fun <T> database(action: (Connection) -> T): T {
        try {
            Files.createDirectories(file.toAbsolutePath().parent)
            Class.forName("org.sqlite.JDBC")
            return DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("PRAGMA busy_timeout = 5000")
                    statement.execute(
                        "CREATE TABLE IF NOT EXISTS liked_tracks (" +
                            "id INTEGER PRIMARY KEY, source_url TEXT NOT NULL UNIQUE, title TEXT NOT NULL)",
                    )
                }
                action(connection)
            }
        } catch (ex: Exception) {
            throw IllegalStateException("Could not access liked tracks at $file", ex)
        }
    }
}
