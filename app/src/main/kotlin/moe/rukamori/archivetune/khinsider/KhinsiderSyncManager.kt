/*
 * Pixel Notes — optional khinsider account sync
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.khinsider

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.KhinsiderCookieKey
import moe.rukamori.archivetune.constants.KhinsiderLastSyncKey
import moe.rukamori.archivetune.constants.KhinsiderSyncFavoritesKey
import moe.rukamori.archivetune.constants.KhinsiderSyncPlaylistsKey
import moe.rukamori.archivetune.constants.KhinsiderUsernameKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.innertube.khinsider.KhinsiderAccount
import moe.rukamori.archivetune.innertube.khinsider.KhinsiderBackend
import moe.rukamori.archivetune.innertube.khinsider.KhinsiderIds
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.reportException
import java.security.MessageDigest
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

data class KhinsiderSyncResult(
    val favoritesImported: Int = 0,
    val favoritesUploaded: Int = 0,
    val playlistsImported: Int = 0,
    val errors: List<String> = emptyList(),
)

@Singleton
class KhinsiderSyncManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
    ) {
        private val mutex = Mutex()

        /** Stores a verified session. Returns the username, or null if the cookie isn't signed in. */
        suspend fun signIn(cookie: String): String? {
            val profile = KhinsiderAccount.verify(cookie) ?: return null
            context.dataStore.updateData { prefs ->
                prefs.toMutablePreferences().apply {
                    this[KhinsiderCookieKey] = cookie
                    this[KhinsiderUsernameKey] = profile.username
                }
            }
            KhinsiderAccount.cookie = cookie
            return profile.username
        }

        suspend fun signOut() {
            KhinsiderAccount.signOut()
            context.dataStore.updateData { prefs ->
                prefs.toMutablePreferences().apply {
                    remove(KhinsiderCookieKey)
                    remove(KhinsiderUsernameKey)
                    remove(KhinsiderLastSyncKey)
                }
            }
        }

        /** Two-way merge of favourites, then imports khinsider playlists. Safe to call when signed out. */
        suspend fun syncNow(): KhinsiderSyncResult =
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    if (!KhinsiderAccount.isSignedIn) return@withContext KhinsiderSyncResult()
                    val prefs = context.dataStore.data.first()
                    val errors = mutableListOf<String>()
                    var imported = 0
                    var uploaded = 0
                    var playlists = 0

                    if (prefs[KhinsiderSyncFavoritesKey] ?: true) {
                        try {
                            val (down, up) = syncFavorites()
                            imported = down
                            uploaded = up
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: KhinsiderAccount.SignedOutException) {
                            signOut()
                            return@withContext KhinsiderSyncResult(errors = listOf("Your khinsider session expired. Please sign in again."))
                        } catch (e: Exception) {
                            reportException(e)
                            errors += "Favourites: ${e.message ?: "sync failed"}"
                        }
                    }

                    if (prefs[KhinsiderSyncPlaylistsKey] ?: true) {
                        try {
                            playlists = importPlaylists()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            reportException(e)
                            errors += "Playlists: ${e.message ?: "import failed"}"
                        }
                    }

                    context.dataStore.updateData { current ->
                        current.toMutablePreferences().apply { this[KhinsiderLastSyncKey] = System.currentTimeMillis() }
                    }
                    KhinsiderSyncResult(imported, uploaded, playlists, errors)
                }
            }

        private suspend fun syncFavorites(): Pair<Int, Int> {
            val remote = KhinsiderAccount.favorites()
            val remoteSlugs = remote.map { it.slug }.toSet()

            // khinsider -> app
            var imported = 0
            remote.forEach { summary ->
                val albumId = KhinsiderIds.albumId(summary.slug)
                val existing = database.album(albumId).firstOrNull()
                if (existing?.album?.bookmarkedAt != null) return@forEach
                runCatching {
                    if (existing == null) {
                        val page = KhinsiderBackend.album(albumId)
                        database.withTransaction { insert(page) }
                    }
                    val stored = database.album(albumId).firstOrNull() ?: return@runCatching
                    database.withTransaction { update(stored.album.copy(bookmarkedAt = LocalDateTime.now())) }
                    imported++
                }.onFailure { if (it is CancellationException) throw it }
            }

            // app -> khinsider
            var uploaded = 0
            database
                .albumsLikedByCreateDateAsc()
                .first()
                .mapNotNull { album -> KhinsiderIds.albumSlug(album.id)?.takeIf { KhinsiderIds.isAlbum(album.id) } }
                .filterNot { it in remoteSlugs }
                .forEach { slug ->
                    runCatching { KhinsiderAccount.setFavorite(slug, favorite = true) }
                        .onSuccess { uploaded++ }
                        .onFailure { if (it is CancellationException) throw it }
                }
            return imported to uploaded
        }

        private suspend fun importPlaylists(): Int {
            var count = 0
            KhinsiderAccount.playlists().forEach { remote ->
                val tracks = KhinsiderAccount.playlistTracks(remote)
                if (tracks.isEmpty()) return@forEach

                // Resolve each track to the album's own song item (title match covers url-encoding differences).
                val songIds = mutableListOf<String>()
                tracks.groupBy { it.albumSlug }.forEach { (slug, albumTracks) ->
                    val page = runCatching { KhinsiderBackend.album(KhinsiderIds.albumId(slug)) }.getOrNull() ?: return@forEach
                    val byId = page.songs.associateBy { it.id }
                    val byTitle = page.songs.associateBy { it.title.lowercase() }
                    albumTracks.forEach { track ->
                        val song = byId[track.songId] ?: byTitle[track.title.lowercase()] ?: return@forEach
                        database.withTransaction { insert(song.toMediaMetadata()) }
                        songIds += song.id
                    }
                }
                if (songIds.isEmpty()) return@forEach

                val playlistId = "KHPL_" + sha1(remote.url).take(16)
                val existing = database.playlist(playlistId).firstOrNull()
                if (existing == null) {
                    database.withTransaction {
                        insert(
                            PlaylistEntity(
                                id = playlistId,
                                name = remote.name,
                                isEditable = true,
                                bookmarkedAt = LocalDateTime.now(),
                            ),
                        )
                    }
                } else {
                    database.withTransaction { clearPlaylist(playlistId) }
                }
                val playlist = database.playlist(playlistId).firstOrNull() ?: return@forEach
                database.withTransaction { addSongToPlaylist(playlist.copy(songCount = 0), songIds.distinct()) }
                count++
            }
            return count
        }

        private fun sha1(text: String): String =
            MessageDigest
                .getInstance("SHA-1")
                .digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
