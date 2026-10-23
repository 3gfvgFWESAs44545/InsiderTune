/*
 * Pixel Notes — khinsider source for ArchiveTune
 * GPL-3.0 License
 *
 * Builds ArchiveTune page models (HomePage, AlbumPage, BrowseResult, ...) from khinsider data.
 * YouTube.kt delegates to these functions so every existing screen shows khinsider content.
 */

package moe.rukamori.archivetune.innertube.khinsider

import moe.rukamori.archivetune.innertube.khinsider.Khinsider.toAlbumItem
import moe.rukamori.archivetune.innertube.khinsider.Khinsider.toSongItems
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.models.SearchSuggestions
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.AlbumPage
import moe.rukamori.archivetune.innertube.pages.BrowseResult
import moe.rukamori.archivetune.innertube.pages.ChartsPage
import moe.rukamori.archivetune.innertube.pages.ExplorePage
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.innertube.pages.MoodAndGenres
import moe.rukamori.archivetune.innertube.pages.NextResult
import moe.rukamori.archivetune.innertube.pages.RelatedPage
import moe.rukamori.archivetune.innertube.pages.SearchResult
import moe.rukamori.archivetune.innertube.pages.SearchSummaryPage

object KhinsiderBackend {
    /** Albums per row on the home screen before "see all". */
    private const val HOME_SECTION_SIZE = 20

    private val homeCategories =
        listOf(
            KhinsiderCategory.TOP_40,
            KhinsiderCategory.MOST_FAVORITES,
            KhinsiderCategory.TOP_100_LAST_6_MONTHS,
            KhinsiderCategory.CURRENTLY_VIEWED,
            KhinsiderCategory.TOP_1000_ALL_TIME,
        )

    private fun section(
        title: String,
        items: List<YTItem>,
        endpoint: BrowseEndpoint?,
        label: String? = null,
    ) = HomePage.Section(
        title = title,
        label = label,
        thumbnail = null,
        endpoint = endpoint,
        items = items,
    )

    // ---------------------------------------------------------------- home

    suspend fun home(params: String?): HomePage {
        // A chip was selected: show that category on its own.
        if (params != null && KhinsiderIds.isCategory(params)) {
            val category = KhinsiderCategory.fromBrowseId(params) ?: throw IllegalArgumentException("Unknown category $params")
            val page = Khinsider.category(category)
            return HomePage(
                chips = chips(),
                sections =
                    page.albums
                        .chunked(HOME_SECTION_SIZE)
                        .mapIndexed { index, chunk ->
                            section(
                                title = if (index == 0) category.title else "${category.title} · ${index * HOME_SECTION_SIZE + 1}+",
                                items = chunk.map { it.toAlbumItem() },
                                endpoint = BrowseEndpoint(browseId = category.browseId),
                            )
                        },
            )
        }

        val latest = runCatching { Khinsider.latest() }.getOrDefault(emptyList())
        val categories = Khinsider.categories(homeCategories)
        val sections =
            buildList {
                if (latest.isNotEmpty()) {
                    add(
                        section(
                            title = "Latest additions",
                            label = "New on khinsider",
                            items = latest.take(HOME_SECTION_SIZE * 2).map { it.toAlbumItem() },
                            endpoint = BrowseEndpoint(browseId = KhinsiderCategory.TOP_100_NEWLY_ADDED.browseId),
                        ),
                    )
                }
                categories.forEach { (category, page) ->
                    if (page.albums.isNotEmpty()) {
                        add(
                            section(
                                title = category.title,
                                // Random picks from each chart so Home looks different every visit.
                                items = page.albums.shuffled().take(HOME_SECTION_SIZE).map { it.toAlbumItem() },
                                endpoint = BrowseEndpoint(browseId = category.browseId),
                            ),
                        )
                    }
                }
            }
        if (sections.isEmpty()) throw java.io.IOException("Could not load khinsider")
        // No chips row: the charts are already rows on Home and categories in Browse.
        return HomePage(chips = null, sections = sections, continuation = null)
    }

    private fun chips(): List<HomePage.Chip> =
        listOf(
            KhinsiderCategory.TOP_40,
            KhinsiderCategory.MOST_FAVORITES,
            KhinsiderCategory.TOP_100_NEWLY_ADDED,
            KhinsiderCategory.TOP_100_LAST_6_MONTHS,
            KhinsiderCategory.CURRENTLY_VIEWED,
        ).map { category ->
            HomePage.Chip(
                title = category.title,
                endpoint = BrowseEndpoint(browseId = category.browseId, params = category.browseId),
                deselectEndPoint = null,
            )
        }

    // ---------------------------------------------------------------- explore / browse

    fun moodAndGenres(): List<MoodAndGenres> =
        KhinsiderCategory.groups.map { group ->
            MoodAndGenres(
                title = group.title,
                items =
                    group.categories.map { category ->
                        MoodAndGenres.Item(
                            title = category.title,
                            stripeColor = category.color,
                            endpoint = BrowseEndpoint(browseId = category.browseId),
                        )
                    },
            )
        }

    suspend fun explore(): ExplorePage =
        ExplorePage(
            newReleaseAlbums = newReleaseAlbums(),
            // Search tab shows these as shortcuts; the Browse tab lists platforms and years too.
            moodAndGenres = moodAndGenres().take(2).flatMap { it.items },
        )

    suspend fun newReleaseAlbums(): List<AlbumItem> =
        Khinsider
            .category(KhinsiderCategory.TOP_100_NEWLY_ADDED)
            .albums
            .map { it.toAlbumItem() }

    fun charts(): ChartsPage = ChartsPage(sections = emptyList(), continuation = null)

    /** Browse result for a category or "related albums" id. */
    suspend fun browse(browseId: String): BrowseResult {
        if (KhinsiderIds.isRelated(browseId)) {
            val slug = KhinsiderIds.albumSlug(browseId) ?: throw IllegalArgumentException(browseId)
            val album = Khinsider.album(slug)
            return BrowseResult(
                title = "Fans also viewed",
                thumbnail = album.coverUrl,
                items = listOf(BrowseResult.Item(title = null, items = album.related.map { it.toAlbumItem() })),
            )
        }
        val category = KhinsiderCategory.fromBrowseId(browseId) ?: throw IllegalArgumentException("Unknown category $browseId")
        val page = Khinsider.category(category)
        return BrowseResult(
            title = category.title,
            thumbnail = page.albums.firstOrNull()?.coverUrl,
            items = listOf(BrowseResult.Item(title = null, items = page.albums.map { it.toAlbumItem() })),
        )
    }

    data class CategoryPage(
        val title: String,
        val items: List<YTItem>,
        val continuation: String?,
    )

    /** First page of a category for the paged grid screen. */
    suspend fun categoryPage(browseId: String): CategoryPage {
        if (KhinsiderIds.isRelated(browseId)) {
            val result = browse(browseId)
            return CategoryPage(result.title.orEmpty(), result.items.flatMap { it.items }, null)
        }
        val category = KhinsiderCategory.fromBrowseId(browseId) ?: throw IllegalArgumentException("Unknown category $browseId")
        return categoryPage(category, 1)
    }

    /** Next page from a continuation token made by [categoryPage]. */
    suspend fun categoryContinuation(token: String): CategoryPage {
        val (key, page) = KhinsiderIds.parseContinuation(token) ?: throw IllegalArgumentException("Bad continuation $token")
        val category = KhinsiderCategory.fromKey(key) ?: throw IllegalArgumentException("Unknown category $key")
        return categoryPage(category, page)
    }

    private suspend fun categoryPage(
        category: KhinsiderCategory,
        page: Int,
    ): CategoryPage {
        val result = Khinsider.category(category, page)
        return CategoryPage(
            title = category.title,
            items = result.albums.map { it.toAlbumItem() },
            continuation = if (result.hasNextPage) KhinsiderIds.continuation(category.key, page + 1) else null,
        )
    }

    // ---------------------------------------------------------------- search

    /** Results come from [search] (album filter); a summary would list the same albums twice. */
    @Suppress("UNUSED_PARAMETER")
    fun searchSummary(query: String): SearchSummaryPage = SearchSummaryPage(summaries = emptyList())

    suspend fun search(query: String): SearchResult = SearchResult(items = Khinsider.search(query).map { it.toAlbumItem() }, continuation = null)

    fun searchSuggestions(): SearchSuggestions = SearchSuggestions(queries = emptyList(), recommendedItems = emptyList())

    // ---------------------------------------------------------------- album / songs

    suspend fun album(browseId: String): AlbumPage {
        val slug = KhinsiderIds.albumSlug(browseId) ?: throw IllegalArgumentException("Not a khinsider album: $browseId")
        val album = Khinsider.album(slug)
        return AlbumPage(
            album = album.toAlbumItem(),
            songs = album.toSongItems(),
            otherVersions = album.related.map { it.toAlbumItem() },
        )
    }

    suspend fun albumSongs(playlistId: String): List<SongItem> {
        val slug = KhinsiderIds.albumSlug(playlistId) ?: throw IllegalArgumentException("Not a khinsider album: $playlistId")
        return Khinsider.album(slug).toSongItems()
    }

    /**
     * "Up next" for a khinsider song or album: the album's tracks, starting position on the song.
     * There is no radio on khinsider, so the queue never continues past the album.
     */
    suspend fun next(endpoint: WatchEndpoint): NextResult {
        val anchorId = endpoint.videoId ?: endpoint.playlistId ?: throw IllegalArgumentException("Empty endpoint")
        val slug = KhinsiderIds.albumSlug(anchorId) ?: throw IllegalArgumentException("Not a khinsider id: $anchorId")
        val album = Khinsider.album(slug)
        val songs = album.toSongItems()
        val index =
            endpoint.videoId
                ?.let { videoId -> songs.indexOfFirst { it.id == videoId } }
                ?.takeIf { it >= 0 }
                ?: endpoint.index?.coerceIn(0, (songs.size - 1).coerceAtLeast(0))
                ?: 0
        return NextResult(
            title = album.title,
            items = songs,
            currentIndex = index,
            lyricsEndpoint = null,
            relatedEndpoint = Khinsider.relatedEndpoint(album.slug),
            continuation = null,
            endpoint = endpoint,
        )
    }

    suspend fun related(endpoint: BrowseEndpoint): RelatedPage {
        val slug = KhinsiderIds.albumSlug(endpoint.browseId) ?: throw IllegalArgumentException(endpoint.browseId)
        val album = Khinsider.album(slug)
        return RelatedPage(
            songs = emptyList(),
            albums = album.related.map { it.toAlbumItem() },
            artists = emptyList(),
            playlists = emptyList(),
        )
    }

    /** Song metadata for a list of khinsider song ids (used to restore queues). */
    suspend fun songs(ids: List<String>): List<SongItem> {
        val bySlug = ids.groupBy { KhinsiderIds.albumSlug(it) }
        val found = HashMap<String, SongItem>()
        bySlug.forEach { (slug, _) ->
            if (slug == null) return@forEach
            runCatching { Khinsider.album(slug).toSongItems() }
                .getOrNull()
                ?.forEach { found[it.id] = it }
        }
        return ids.mapNotNull { found[it] }
    }
}
