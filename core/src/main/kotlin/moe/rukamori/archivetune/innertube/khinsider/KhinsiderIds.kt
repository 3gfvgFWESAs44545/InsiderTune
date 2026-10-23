/*
 * Pixel Notes — khinsider source for ArchiveTune
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.innertube.khinsider

import java.util.Base64

/**
 * ID scheme used to carry khinsider content through ArchiveTune's YouTube-shaped models.
 *
 * - Album browse id:    KHA_<album-slug>
 * - Album playlist id:  KHP_<album-slug>
 * - Song (media) id:    KHS_<base64url("<album-slug>/<track-file>")>
 * - Category browse id: KH_<category-key>       (see [KhinsiderCategory])
 * - Related browse id:  KHR_<album-slug>
 *
 * All ids are free of '/' so they are safe inside navigation routes.
 */
object KhinsiderIds {
    const val ALBUM_PREFIX = "KHA_"
    const val PLAYLIST_PREFIX = "KHP_"
    const val SONG_PREFIX = "KHS_"
    const val CATEGORY_PREFIX = "KH_"
    const val RELATED_PREFIX = "KHR_"
    const val CONTINUATION_PREFIX = "KHC_"

    fun isKhinsider(id: String?): Boolean =
        id != null &&
            (
                id.startsWith(ALBUM_PREFIX) ||
                    id.startsWith(PLAYLIST_PREFIX) ||
                    id.startsWith(SONG_PREFIX) ||
                    id.startsWith(CATEGORY_PREFIX) ||
                    id.startsWith(RELATED_PREFIX) ||
                    id.startsWith(CONTINUATION_PREFIX)
            )

    fun isSong(id: String?): Boolean = id?.startsWith(SONG_PREFIX) == true

    fun isAlbum(id: String?): Boolean = id?.startsWith(ALBUM_PREFIX) == true

    fun isPlaylist(id: String?): Boolean = id?.startsWith(PLAYLIST_PREFIX) == true

    fun isCategory(id: String?): Boolean = id?.startsWith(CATEGORY_PREFIX) == true

    fun isRelated(id: String?): Boolean = id?.startsWith(RELATED_PREFIX) == true

    fun isContinuation(id: String?): Boolean = id?.startsWith(CONTINUATION_PREFIX) == true

    fun albumId(slug: String): String = ALBUM_PREFIX + slug

    fun playlistId(slug: String): String = PLAYLIST_PREFIX + slug

    fun relatedId(slug: String): String = RELATED_PREFIX + slug

    /** Album slug from any album-ish id (KHA_, KHP_, KHR_ or KHS_). */
    fun albumSlug(id: String): String? =
        when {
            id.startsWith(ALBUM_PREFIX) -> id.removePrefix(ALBUM_PREFIX)
            id.startsWith(PLAYLIST_PREFIX) -> id.removePrefix(PLAYLIST_PREFIX)
            id.startsWith(RELATED_PREFIX) -> id.removePrefix(RELATED_PREFIX)
            id.startsWith(SONG_PREFIX) -> songPath(id)?.substringBefore('/')
            else -> null
        }?.takeIf { it.isNotBlank() }

    /** Song id from the track page path relative to /game-soundtracks/album/, e.g. "minecraft/1-01.%2520Key.mp3". */
    fun songId(trackPath: String): String =
        SONG_PREFIX +
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(trackPath.toByteArray(Charsets.UTF_8))

    /** Track page path relative to /game-soundtracks/album/ for a song id. */
    fun songPath(songId: String): String? =
        runCatching {
            String(
                Base64.getUrlDecoder().decode(songId.removePrefix(SONG_PREFIX)),
                Charsets.UTF_8,
            )
        }.getOrNull()?.takeIf { it.contains('/') }

    /**
     * Pixel Notes: the khinsider web page for any khinsider id, for sharing and Discord buttons.
     * Albums (and their album playlists) open the album page; songs open the track's page.
     */
    fun webUrl(id: String?): String? {
        if (id.isNullOrBlank()) return null
        val base = "https://downloads.khinsider.com"
        return when {
            id.startsWith(SONG_PREFIX) -> songPath(id)?.let { "$base/game-soundtracks/album/$it" }
            id.startsWith(CATEGORY_PREFIX) ->
                KhinsiderCategory.fromKey(categoryKey(id))?.let { base + it.path }
            else -> albumSlug(id)?.let { "$base/game-soundtracks/album/$it" }
        }
    }

    fun categoryId(key: String): String = CATEGORY_PREFIX + key

    fun categoryKey(id: String): String = id.removePrefix(CATEGORY_PREFIX)

    fun continuation(
        categoryKey: String,
        page: Int,
    ): String = "$CONTINUATION_PREFIX$categoryKey|$page"

    /** Returns category key and page for a continuation token. */
    fun parseContinuation(token: String): Pair<String, Int>? {
        val body = token.removePrefix(CONTINUATION_PREFIX)
        val key = body.substringBeforeLast('|', missingDelimiterValue = "")
        val page = body.substringAfterLast('|').toIntOrNull()
        if (key.isBlank() || page == null) return null
        return key to page
    }
}
