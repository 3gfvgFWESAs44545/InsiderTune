/*
 * Pixel Notes — a khinsider category page (platform, chart, album type, year):
 * Top albums, then the full list with sort and filters.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.viewmodels

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.innertube.khinsider.KhCategoryListing
import moe.rukamori.archivetune.innertube.khinsider.KhSort
import moe.rukamori.archivetune.innertube.khinsider.KhSortField
import moe.rukamori.archivetune.innertube.khinsider.Khinsider
import moe.rukamori.archivetune.innertube.khinsider.KhinsiderCategory
import moe.rukamori.archivetune.innertube.khinsider.KhinsiderIds
import moe.rukamori.archivetune.innertube.khinsider.KhinsiderListing
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

data class KhCategoryFilters(
    val types: Set<String> = emptySet(),
    val years: Set<Int> = emptySet(),
) {
    val count: Int get() = types.size + years.size
    val isEmpty: Boolean get() = count == 0

    fun matches(album: Khinsider.KhAlbumSummary): Boolean {
        if (types.isNotEmpty()) {
            val type = album.albumType.orEmpty()
            if (types.none { selected -> type.contains(selected, ignoreCase = true) }) return false
        }
        if (years.isNotEmpty() && album.year?.let { it in years } != true) return false
        return true
    }
}

data class KhCategoryUiState(
    val category: KhinsiderCategory? = null,
    val loading: Boolean = true,
    val error: Boolean = false,
    val top: List<Khinsider.KhAlbumSummary> = emptyList(),
    val albums: List<Khinsider.KhAlbumSummary> = emptyList(),
    val total: Int? = null,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    /** null = khinsider's own order (A–Z for lists, ranking for charts). */
    val sort: KhSort? = null,
    /** False when khinsider had no link for the chosen order and only loaded albums were sorted. */
    val sortOnServer: Boolean = true,
    val filters: KhCategoryFilters = KhCategoryFilters(),
) {
    /** Albums after filters (and local sorting when khinsider couldn't sort). */
    val visible: List<Khinsider.KhAlbumSummary> by lazy {
        val filtered = if (filters.isEmpty) albums else albums.filter(filters::matches)
        if (sortOnServer || sort == null) filtered else sortLocally(filtered, sort)
    }

    /** Type options: khinsider's types, plus any others seen in the list. */
    val typeOptions: List<String> by lazy {
        val known = listOf("Gamerip", "Soundtrack", "Single", "Arrangement", "Remix", "Compilation", "Inspired By")
        val seen = albums.mapNotNull { it.albumType?.trim()?.takeIf(String::isNotEmpty) }.distinct()
        known + seen.filter { s -> known.none { it.equals(s, ignoreCase = true) } }.sorted()
    }

    /** Years seen in the loaded albums, newest first. */
    val yearOptions: List<Int> by lazy {
        (albums.mapNotNull { it.year } + filters.years).distinct().sortedDescending()
    }
}

private fun sortLocally(
    albums: List<Khinsider.KhAlbumSummary>,
    sort: KhSort,
): List<Khinsider.KhAlbumSummary> =
    when (sort.field) {
        KhSortField.NAME ->
            if (sort.descending) albums.sortedByDescending { it.title.lowercase() } else albums.sortedBy { it.title.lowercase() }

        KhSortField.YEAR -> {
            val withYear = albums.filter { it.year != null }
            val noYear = albums.filter { it.year == null }
            (if (sort.descending) withYear.sortedByDescending { it.year } else withYear.sortedBy { it.year }) + noYear
        }

        // khinsider rows carry no added date, so this order can only come from the site.
        KhSortField.ADDED -> albums
    }

@HiltViewModel
class KhinsiderCategoryViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val browseId = Uri.decode(savedStateHandle.get<String>("browseId").orEmpty()).trim()
        private val category = KhinsiderCategory.fromBrowseId(browseId)

        private val _state = MutableStateFlow(KhCategoryUiState(category = category))
        val state = _state.asStateFlow()

        private var lastListing: KhCategoryListing? = null
        private var loadJob: Job? = null
        private var moreJob: Job? = null
        private var autoLoads = 0

        init {
            load()
        }

        fun retry() = load()

        fun setSort(sort: KhSort?) {
            val normalized = if (category?.paged == true && sort == KhSort.Default) null else sort
            if (normalized == _state.value.sort) return
            _state.update { it.copy(sort = normalized) }
            load()
        }

        fun setFilters(filters: KhCategoryFilters) {
            _state.update { it.copy(filters = filters) }
            autoLoads = 0
            fillFiltered()
        }

        fun loadMore() {
            if (moreJob?.isActive == true || loadJob?.isActive == true) return
            val listing = lastListing ?: return
            val cat = category ?: return
            if (listing.nextPageUrl == null) return
            _state.update { it.copy(loadingMore = true) }
            moreJob =
                viewModelScope.launch {
                    try {
                        val next = KhinsiderListing.next(cat, listing)
                        lastListing = next
                        _state.update { state ->
                            val known = state.albums.mapTo(HashSet()) { it.slug }
                            state.copy(
                                albums = state.albums + next?.albums.orEmpty().filterNot { it.slug in known },
                                hasMore = next?.nextPageUrl != null,
                            )
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        reportException(e)
                        _state.update { it.copy(hasMore = false) }
                    } finally {
                        _state.update { it.copy(loadingMore = false) }
                    }
                }
            moreJob?.invokeOnCompletion { cause -> if (cause == null) fillFiltered() }
        }

        /** With filters on, keep fetching pages until there's a screenful of matches (or the list ends). */
        private fun fillFiltered() {
            val state = _state.value
            if (state.filters.isEmpty || !state.hasMore) return
            if (state.visible.size >= MIN_FILTERED_RESULTS || autoLoads >= MAX_AUTO_PAGES) return
            autoLoads++
            loadMore()
        }

        private fun load() {
            val cat = category
            if (cat == null) {
                _state.update { it.copy(loading = false, error = true) }
                return
            }
            moreJob?.cancel()
            loadJob?.cancel()
            autoLoads = 0
            _state.update { it.copy(loading = true, error = false) }
            loadJob =
                viewModelScope.launch {
                    try {
                        val listing = KhinsiderListing.first(cat, _state.value.sort)
                        lastListing = listing
                        _state.update {
                            it.copy(
                                loading = false,
                                top = listing.top,
                                albums = listing.albums,
                                total = listing.total,
                                hasMore = listing.nextPageUrl != null,
                                sortOnServer = listing.sortApplied,
                            )
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        reportException(e)
                        _state.update { it.copy(loading = false, error = it.albums.isEmpty()) }
                    }
                }
            loadJob?.invokeOnCompletion { cause -> if (cause == null) fillFiltered() }
        }

        companion object {
            fun isCategory(browseId: String?): Boolean =
                KhinsiderIds.isCategory(Uri.decode(browseId.orEmpty())) &&
                    KhinsiderCategory.fromBrowseId(Uri.decode(browseId.orEmpty())) != null

            private const val MIN_FILTERED_RESULTS = 24
            private const val MAX_AUTO_PAGES = 10
        }
    }
