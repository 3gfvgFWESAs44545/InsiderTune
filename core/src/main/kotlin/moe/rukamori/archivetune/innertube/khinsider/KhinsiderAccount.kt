/*
 * Pixel Notes — optional khinsider account sync
 * GPL-3.0 License
 *
 * Sign-in happens in a WebView on khinsider's own login page; the app keeps only the
 * resulting session cookie. Everything here is optional: with no cookie, every call is a no-op
 * and the rest of the app works exactly the same.
 *
 * khinsider's account pages are discovered at runtime from the links on its control panel
 * ("Favorites", "History", "Playlists") so small changes to the site don't break sync.
 */

package moe.rukamori.archivetune.innertube.khinsider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.innertube.khinsider.Khinsider.content
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException

object KhinsiderAccount {
    /** Session cookie header value for downloads.khinsider.com, or null when signed out. */
    @Volatile
    var cookie: String? = null

    /** Per-feature switches, set from the app's settings. */
    @Volatile
    var syncFavorites: Boolean = true

    @Volatile
    var syncHistory: Boolean = true

    val isSignedIn: Boolean
        get() = !cookie.isNullOrBlank()

    /** Page the WebView opens for sign-in. It shows khinsider's login form when signed out. */
    const val SIGN_IN_URL = "${Khinsider.BASE_URL}/cp/favorites"

    private const val DEFAULT_FAVORITES_PATH = "/cp/favorites"
    private const val DEFAULT_HISTORY_PATH = "/cp/history"
    private const val DEFAULT_PLAYLISTS_PATH = "/cp/playlists"

    data class Profile(
        val username: String,
    )

    data class AccountLinks(
        val favorites: String,
        val history: String?,
        val playlists: String?,
    )

    data class RemotePlaylist(
        val name: String,
        val url: String,
    )

    data class RemoteTrack(
        val songId: String,
        val albumSlug: String,
        val title: String,
    )

    @Volatile
    private var cachedLinks: AccountLinks? = null

    private fun requireCookie(cookie: String? = this.cookie): String =
        cookie?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("Not signed in to khinsider")

    private fun Document.looksSignedOut(): Boolean {
        val title = title()
        if (title.contains("log in", ignoreCase = true) || title.contains("login", ignoreCase = true)) return true
        val passwordField = selectFirst("input[type=password]")
        val logoutLink = selectFirst("a[href*=logout], a[href*=log-out], a[href*=signout]")
        return passwordField != null && logoutLink == null
    }

    private fun Document.findUsername(): String? {
        val selectors =
            listOf(
                "a[href*=/members/] .username",
                "a.p-navgroup-link--user .p-navgroup-linkText",
                "a[href*=/members/]",
                ".username",
                "#loginInfo b",
                ".loggedInAs b",
            )
        for (selector in selectors) {
            val text = Khinsider.clean(selectFirst(selector)?.text())
            if (text.isNotEmpty() && text.length <= 40) return text
        }
        Regex("(?:Logged in as|Welcome,?|Hi,?)\\s+([A-Za-z0-9_.\\- ]{2,40})", RegexOption.IGNORE_CASE)
            .find(body().text())
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.let { return it }
        return null
    }

    /**
     * Checks a cookie against khinsider. Returns the profile if it's a working session, or null.
     * Used after the WebView sign-in and on "Sync now".
     */
    suspend fun verify(cookie: String): Profile? {
        if (cookie.isBlank()) return null
        val document = runCatching { Khinsider.fetchDocument(SIGN_IN_URL, cookie = cookie) }.getOrNull() ?: return null
        if (document.looksSignedOut()) return null
        cachedLinks = discoverLinks(document)
        return Profile(username = document.findUsername() ?: "khinsider user")
    }

    private fun discoverLinks(document: Document): AccountLinks {
        fun link(vararg words: String): String? =
            document
                .select("a[href]")
                .firstOrNull { anchor ->
                    val text = anchor.text()
                    val href = anchor.attr("href")
                    words.any { word ->
                        text.contains(word, ignoreCase = true) && (href.contains("/cp") || href.contains(word, ignoreCase = true))
                    }
                }?.absUrl("href")
                ?.takeIf { it.startsWith("http") }

        return AccountLinks(
            favorites = link("favorite", "favourite") ?: "${Khinsider.BASE_URL}$DEFAULT_FAVORITES_PATH",
            history = link("history") ?: "${Khinsider.BASE_URL}$DEFAULT_HISTORY_PATH",
            playlists = link("playlist") ?: "${Khinsider.BASE_URL}$DEFAULT_PLAYLISTS_PATH",
        )
    }

    private suspend fun links(): AccountLinks {
        cachedLinks?.let { return it }
        val document = Khinsider.fetchDocument(SIGN_IN_URL, cookie = requireCookie())
        if (document.looksSignedOut()) throw SignedOutException()
        return discoverLinks(document).also { cachedLinks = it }
    }

    class SignedOutException : IOException("khinsider session expired")

    // ---------------------------------------------------------------- favourites

    /** All albums in the account's favourites, following pagination. */
    suspend fun favorites(): List<Khinsider.KhAlbumSummary> {
        val first = links().favorites
        val albums = LinkedHashMap<String, Khinsider.KhAlbumSummary>()
        var page = 1
        while (page <= MAX_PAGES) {
            val url = if (page == 1) first else "$first${if ('?' in first) '&' else '?'}page=$page"
            val document = Khinsider.fetchDocument(url, cookie = requireCookie())
            if (document.looksSignedOut()) throw SignedOutException()
            val root = document.content()
            val before = albums.size
            Khinsider.parseAlbumRows(root).forEach { albums.putIfAbsent(it.slug, it) }
            if (albums.size == before || !Khinsider.parseHasNextPage(root, page)) break
            page++
        }
        return albums.values.toList()
    }

    /**
     * Adds or removes an album from khinsider favourites by using the album page's own
     * favourite control (link, form or script URL), whichever the page provides.
     */
    /**
     * Set by the app: performs the favourite action in a signed-in WebView by pressing khinsider's
     * own button, which works however the site implements it (link, form or script).
     */
    @Volatile
    var browserFavoriteHandler: (suspend (albumSlug: String, favorite: Boolean) -> Unit)? = null

    suspend fun setFavorite(
        albumSlug: String,
        favorite: Boolean,
    ) {
        requireCookie()
        val handler = browserFavoriteHandler
        if (handler != null) {
            handler(albumSlug, favorite)
            return
        }
        setFavoriteHttp(albumSlug, favorite)
    }

    /** Fallback without a WebView: finds the favourite control in the HTML and requests it directly. */
    suspend fun setFavoriteHttp(
        albumSlug: String,
        favorite: Boolean,
    ) {
        val cookie = requireCookie()
        val albumUrl = "${Khinsider.BASE_URL}/game-soundtracks/album/$albumSlug"
        val document = Khinsider.fetchDocument(albumUrl, cookie = cookie)
        if (document.looksSignedOut()) throw SignedOutException()

        val controls = favoriteControls(document)
        if (controls.isEmpty()) throw IOException("No favourite button found on $albumSlug")
        val control =
            controls.firstOrNull { it.adds == favorite }
                ?: controls.firstOrNull { it.adds == null }
                ?: return // Already in the requested state.
        when (control) {
            is FavoriteControl.Link -> {
                execute(Request.Builder().url(control.url).get(), cookie, albumUrl)
            }

            is FavoriteControl.Form -> {
                val body = FormBody.Builder().apply { control.fields.forEach { (name, value) -> add(name, value) } }.build()
                val builder = Request.Builder().url(control.url)
                if (control.method.equals("post", ignoreCase = true)) builder.post(body) else builder.get()
                execute(builder, cookie, albumUrl)
            }
        }
    }

    private sealed interface FavoriteControl {
        /** true = adds, false = removes, null = toggles/unknown. */
        val adds: Boolean?

        data class Link(
            val url: String,
            override val adds: Boolean?,
        ) : FavoriteControl

        data class Form(
            val url: String,
            val method: String,
            val fields: List<Pair<String, String>>,
            override val adds: Boolean?,
        ) : FavoriteControl
    }

    private val favoriteWord = Regex("favou?rite", RegexOption.IGNORE_CASE)
    private val scriptUrl = Regex("""['"]((?:https?://[^'"]+)?/[^'"\s]*favou?rite[^'"\s]*)['"]""", RegexOption.IGNORE_CASE)

    private fun intent(element: Element): Boolean? {
        val label = (element.text() + " " + element.attr("title") + " " + element.attr("value") + " " + element.attr("href")).lowercase()
        return when {
            listOf("remove", "unfav", "delete", "un-fav").any { it in label } -> false
            listOf("add", "+").any { it in label } -> true
            else -> null
        }
    }

    private fun favoriteControls(document: Document): List<FavoriteControl> {
        val root = document.content()
        val controls = mutableListOf<FavoriteControl>()

        root.select("form").forEach { form ->
            val action = form.absUrl("action")
            val mentions = favoriteWord.containsMatchIn(action) || favoriteWord.containsMatchIn(form.outerHtml())
            if (!mentions || action.isBlank()) return@forEach
            val fields =
                form.select("input[name], button[name]").map { input ->
                    input.attr("name") to input.attr("value")
                }
            controls += FavoriteControl.Form(action, form.attr("method").ifBlank { "post" }, fields, intent(form))
        }

        root.select("a[href]").forEach { anchor ->
            val href = anchor.absUrl("href")
            val mentions =
                favoriteWord.containsMatchIn(href) ||
                    (favoriteWord.containsMatchIn(anchor.text() + anchor.attr("title")) && !href.endsWith("#"))
            if (mentions && href.startsWith("http") && !href.contains("/cp/")) {
                controls += FavoriteControl.Link(href, intent(anchor))
            }
        }

        // Script-driven buttons: pull the request URL out of onclick / data attributes / inline scripts.
        root.select("[onclick], [data-url], [data-href]").forEach { element ->
            val source = element.attr("onclick") + " " + element.attr("data-url") + " " + element.attr("data-href")
            scriptUrl.find(source)?.groupValues?.get(1)?.let { url ->
                controls += FavoriteControl.Link(absolute(url), intent(element))
            }
        }
        if (controls.isEmpty()) {
            document.select("script").forEach { script ->
                scriptUrl.findAll(script.data()).forEach { match ->
                    controls += FavoriteControl.Link(absolute(match.groupValues[1]), null)
                }
            }
        }
        return controls.distinct()
    }

    private fun absolute(url: String): String = if (url.startsWith("http")) url else "${Khinsider.BASE_URL}${if (url.startsWith("/")) "" else "/"}$url"

    private suspend fun execute(
        builder: Request.Builder,
        cookie: String,
        referer: String,
    ) = withContext(Dispatchers.IO) {
        val request =
            builder
                .header("User-Agent", Khinsider.USER_AGENT)
                .header("Cookie", cookie)
                .header("Referer", referer)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
        Khinsider.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code !in 300..399) {
                throw IOException("khinsider favourite request failed: HTTP ${response.code}")
            }
        }
    }

    // ---------------------------------------------------------------- history

    private val recentlyRecorded = HashMap<String, Long>()

    /**
     * khinsider records history when a signed-in user opens an album page, so playing an album
     * "views" it once (at most hourly per album).
     */
    suspend fun recordPlay(albumSlug: String) {
        if (!isSignedIn || !syncHistory) return
        val now = System.currentTimeMillis()
        synchronized(recentlyRecorded) {
            val last = recentlyRecorded[albumSlug]
            if (last != null && now - last < HISTORY_INTERVAL_MS) return
            recentlyRecorded[albumSlug] = now
        }
        runCatching {
            Khinsider.fetchDocument("${Khinsider.BASE_URL}/game-soundtracks/album/$albumSlug", cookie = requireCookie())
        }
    }

    // ---------------------------------------------------------------- playlists

    suspend fun playlists(): List<RemotePlaylist> {
        val url = links().playlists ?: return emptyList()
        val document = Khinsider.fetchDocument(url, cookie = requireCookie())
        if (document.looksSignedOut()) throw SignedOutException()
        val root = document.content()
        return root
            .select("a[href]")
            .filter { anchor ->
                val href = anchor.attr("href")
                href.contains("playlist", ignoreCase = true) &&
                    !href.contains("/game-soundtracks/album/") &&
                    anchor.absUrl("href") != url &&
                    Khinsider.clean(anchor.text()).isNotEmpty() &&
                    !Regex("delete|remove|edit|create|new playlist", RegexOption.IGNORE_CASE).containsMatchIn(anchor.text() + href)
            }.map { RemotePlaylist(name = Khinsider.clean(it.text()), url = it.absUrl("href")) }
            .distinctBy { it.url }
    }

    /** Tracks of a playlist, as khinsider song ids (album slug + track file). */
    suspend fun playlistTracks(playlist: RemotePlaylist): List<RemoteTrack> {
        val document = Khinsider.fetchDocument(playlist.url, cookie = requireCookie())
        val albumPath = "/game-soundtracks/album/"
        return document
            .content()
            .select("a[href*=$albumPath]")
            .mapNotNull { anchor ->
                val href = anchor.attr("href")
                val trackPath = href.substring(href.indexOf(albumPath) + albumPath.length).substringBefore('?')
                if (!trackPath.contains('/') || trackPath.endsWith("/")) return@mapNotNull null
                val title = Khinsider.clean(anchor.text())
                if (title.isEmpty() || Khinsider.parseDuration(title) != null) return@mapNotNull null
                RemoteTrack(
                    songId = KhinsiderIds.songId(trackPath),
                    albumSlug = trackPath.substringBefore('/'),
                    title = title,
                )
            }.distinctBy { it.songId }
    }

    fun signOut() {
        cookie = null
        cachedLinks = null
        synchronized(recentlyRecorded) { recentlyRecorded.clear() }
    }

    private const val MAX_PAGES = 30
    private const val HISTORY_INTERVAL_MS = 60 * 60 * 1000L
}
