/*
 * Pixel Notes — category pages with khinsider's own sorting.
 *
 * A category page has an optional "Top 12" cover grid and a paged album table. The table header
 * links (Album Name, Added, Year) re-sort the list on the server; we read those links from the
 * page instead of guessing their format, and follow them until the order matches what was asked.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.innertube.khinsider

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Element
import java.util.Collections

enum class KhSortField { NAME, ADDED, YEAR }

data class KhSort(
    val field: KhSortField,
    val descending: Boolean,
) {
    companion object {
        /** khinsider's default list order. */
        val Default = KhSort(KhSortField.NAME, descending = false)
    }
}

data class KhCategoryListing(
    val title: String,
    /** khinsider's "Top 12" for this category (first page only, may be empty). */
    val top: List<Khinsider.KhAlbumSummary>,
    val albums: List<Khinsider.KhAlbumSummary>,
    /** Next page of this same list (keeps the sort), or null on the last page. */
    val nextPageUrl: String?,
    /** "Found 220 albums!" when the page shows it. */
    val total: Int?,
    /** False when khinsider had no sort link for the requested order, so the list is in its default order. */
    val sortApplied: Boolean = true,
    internal val sortLinks: Map<KhSortField, String> = emptyMap(),
    internal val pageUrl: String = "",
)

object KhinsiderListing {
    private val cache = Collections.synchronizedMap(LinkedHashMap<String, KhCategoryListing>())

    /** First page of [category] in the requested order; null keeps khinsider's own order. */
    suspend fun first(
        category: KhinsiderCategory,
        sort: KhSort? = null,
    ): KhCategoryListing {
        val base = load(category, "${Khinsider.BASE_URL}${category.path}", includeTop = true)
        if (sort == null || (category.paged && sort == KhSort.Default)) return base
        // Ranked charts are one fixed page with no sort links: sort what's there.
        if (!category.paged) return base.copy(sortApplied = false)
        val link = base.sortLinks[sort.field] ?: return base.copy(sortApplied = false)

        var page = load(category, link, includeTop = false)
        if (isDescending(page, sort.field, link) != sort.descending) {
            // The header link toggles direction when the list is already sorted by that column.
            val toggle = page.sortLinks[sort.field]
            if (toggle != null && toggle != link) {
                page = load(category, toggle, includeTop = false)
            }
        }
        return page.copy(top = base.top, total = page.total ?: base.total)
    }

    /** The page after [listing], or null when there is none. */
    suspend fun next(
        category: KhinsiderCategory,
        listing: KhCategoryListing,
    ): KhCategoryListing? {
        val url = listing.nextPageUrl ?: return null
        return load(category, url, includeTop = false)
    }

    private suspend fun load(
        category: KhinsiderCategory,
        url: String,
        includeTop: Boolean,
    ): KhCategoryListing {
        val cacheKey = "$url|$includeTop"
        cache[cacheKey]?.let {
            KhinsiderTraffic.recordFromPhone(TrafficCategory.PAGES, "memory · ${category.title}")
            return it
        }
        val document = Khinsider.fetchDocument(url, diskTtlMs = Khinsider.LIST_DISK_TTL_MS)
        val pageUrl = document.location().ifBlank { url }
        val root = with(Khinsider) { document.content() }
        val listing =
            KhCategoryListing(
                title = category.title,
                top = if (includeTop && category.paged) Khinsider.parseTopGrid(root) else emptyList(),
                albums = Khinsider.parseAlbumRows(root),
                nextPageUrl = if (category.paged) nextPageUrl(root, pageUrl) else null,
                total = parseTotal(root),
                sortLinks = parseSortLinks(root),
                pageUrl = pageUrl,
            )
        if (cache.size > 64) cache.keys.firstOrNull()?.let(cache::remove)
        cache[cacheKey] = listing
        return listing
    }

    private fun parseSortLinks(root: Element): Map<KhSortField, String> {
        val links = LinkedHashMap<KhSortField, String>()
        val header =
            root.select("table.albumList tr").firstOrNull { it.select("th").isNotEmpty() }
                ?: root.select("tr").firstOrNull { row -> row.select("th a[href]").isNotEmpty() }
                ?: return links
        header.select("a[href]").forEach { link ->
            val text = Khinsider.clean(link.text()).lowercase()
            val href = link.absUrl("href").ifBlank { return@forEach }
            val field =
                when {
                    "name" in text || "album" in text -> KhSortField.NAME
                    "added" in text || "date" in text -> KhSortField.ADDED
                    "year" in text -> KhSortField.YEAR
                    else -> null
                } ?: return@forEach
            links.putIfAbsent(field, href)
        }
        return links
    }

    private fun parseTotal(root: Element): Int? {
        val text = Khinsider.clean(root.text())
        val match = Regex("Found\\s+([\\d,]+)\\s+(?:matching\\s+)?albums", RegexOption.IGNORE_CASE).find(text) ?: return null
        return match.groupValues[1].replace(",", "").toIntOrNull()
    }

    private fun pageNumber(url: String): Int = url.toHttpUrlOrNull()?.queryParameter("page")?.toIntOrNull() ?: 1

    private fun nextPageUrl(
        root: Element,
        pageUrl: String,
    ): String? {
        val current = pageNumber(pageUrl)
        val path = pageUrl.toHttpUrlOrNull()?.encodedPath
        // Prefer the site's own link to the next page: it carries the sort parameters.
        val linked =
            root.select("a[href*=page=]").map { it.absUrl("href") }.firstOrNull { href ->
                val url = href.toHttpUrlOrNull() ?: return@firstOrNull false
                url.queryParameter("page")?.toIntOrNull() == current + 1 && (path == null || url.encodedPath == path)
            }
        if (linked != null) return linked
        if (!Khinsider.parseHasNextPage(root, current)) return null
        return pageUrl
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.setQueryParameter("page", (current + 1).toString())
            ?.build()
            ?.toString()
    }

    /** Which way [page] is sorted: from the link's own parameters, else from the rows themselves. */
    private fun isDescending(
        page: KhCategoryListing,
        field: KhSortField,
        link: String,
    ): Boolean {
        val query = link.toHttpUrlOrNull()?.query.orEmpty().lowercase()
        if ("desc" in query) return true
        if ("asc" in query) return false
        val albums = page.albums
        return when (field) {
            KhSortField.NAME -> {
                val first = albums.firstOrNull()?.title ?: return false
                val last = albums.lastOrNull()?.title ?: return false
                first.compareTo(last, ignoreCase = true) > 0
            }

            KhSortField.YEAR -> {
                val years = albums.mapNotNull { it.year }
                if (years.size < 2) false else years.first() > years.last()
            }

            // No added date in the rows; khinsider's first click on "Added" shows the newest.
            KhSortField.ADDED -> true
        }
    }
}
