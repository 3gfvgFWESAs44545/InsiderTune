/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Pixel Notes: lyrics were removed. The table is kept (always empty) only so existing
 * databases open without a migration.
 */
@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val id: String,
    val lyrics: String,
    @ColumnInfo(defaultValue = "'REMOTE'")
    val source: String = "REMOTE",
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0L,
)
