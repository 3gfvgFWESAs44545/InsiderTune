/*
 * Pixel Notes — counts what the app downloads, to show how hard it hits khinsider.
 *
 * Every HTTP client the app uses for khinsider gets [interceptor], which counts requests and the
 * bytes actually read. Cache hits are reported separately with [recordFromPhone] by the code that
 * serves them. Nothing leaves the phone; the numbers are only shown in Developer options.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.innertube.khinsider

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.time.LocalDate

enum class TrafficCategory(
    val label: String,
) {
    PAGES("Pages"),
    COVER_THUMB("Covers · 117 px"),
    COVER_LARGE("Covers · 200 px"),
    COVER_FULL("Covers · full size"),
    LIKED_COVERS("Liked cover saves"),
    WEB_VIEW("Favourite sync (web view)"),
    AUDIO("Audio"),
    OTHER("Other"),
}

data class TrafficCounter(
    val requests: Long = 0,
    val bytes: Long = 0,
    val fromPhone: Long = 0,
)

data class TrafficTotals(
    val byCategory: Map<TrafficCategory, TrafficCounter> = emptyMap(),
) {
    operator fun get(category: TrafficCategory): TrafficCounter = byCategory[category] ?: TrafficCounter()

    fun plus(
        category: TrafficCategory,
        requests: Long,
        bytes: Long,
        fromPhone: Long,
    ): TrafficTotals {
        val current = this[category]
        val updated =
            current.copy(
                requests = current.requests + requests,
                bytes = current.bytes + bytes,
                fromPhone = current.fromPhone + fromPhone,
            )
        return TrafficTotals(byCategory + (category to updated))
    }
}

data class TrafficLogEntry(
    val timeMillis: Long,
    val category: TrafficCategory,
    val bytes: Long,
    val fromPhone: Boolean,
    val description: String,
)

data class TrafficSnapshot(
    val session: TrafficTotals = TrafficTotals(),
    val today: TrafficTotals = TrafficTotals(),
    val todayDate: String = LocalDate.now().toString(),
    val sinceReset: TrafficTotals = TrafficTotals(),
    val resetAtMillis: Long = System.currentTimeMillis(),
    val log: List<TrafficLogEntry> = emptyList(),
)

object KhinsiderTraffic {
    private const val LOG_SIZE = 200

    /** Turned off from Developer options; when off nothing is counted. */
    @Volatile
    var enabled: Boolean = true

    private val lock = Any()
    private val _snapshot = MutableStateFlow(TrafficSnapshot())
    val snapshot: StateFlow<TrafficSnapshot> = _snapshot.asStateFlow()

    /** Restores saved "today" and "since reset" totals when the app starts. */
    fun restore(
        today: TrafficTotals,
        todayDate: String,
        sinceReset: TrafficTotals,
        resetAtMillis: Long,
    ) = synchronized(lock) {
        val keepToday = todayDate == LocalDate.now().toString()
        _snapshot.value =
            _snapshot.value.copy(
                today = if (keepToday) today else TrafficTotals(),
                todayDate = LocalDate.now().toString(),
                sinceReset = sinceReset,
                resetAtMillis = resetAtMillis,
            )
    }

    fun reset() =
        synchronized(lock) {
            _snapshot.value = TrafficSnapshot()
        }

    /** A download that reached the network (bytes as actually read). */
    fun recordNetwork(
        category: TrafficCategory,
        bytes: Long,
        description: String,
    ) = record(category, requests = 1, bytes = bytes, fromPhone = 0, description = description)

    /** Something shown from the phone (cache or saved copy) instead of downloading it. */
    fun recordFromPhone(
        category: TrafficCategory,
        description: String,
    ) = record(category, requests = 0, bytes = 0, fromPhone = 1, description = description)

    private fun record(
        category: TrafficCategory,
        requests: Long,
        bytes: Long,
        fromPhone: Long,
        description: String,
    ) {
        if (!enabled) return
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val current = _snapshot.value
            val date = LocalDate.now().toString()
            val today = if (current.todayDate == date) current.today else TrafficTotals()
            val entry = TrafficLogEntry(now, category, bytes, fromPhone > 0, description)
            _snapshot.value =
                current.copy(
                    session = current.session.plus(category, requests, bytes, fromPhone),
                    today = today.plus(category, requests, bytes, fromPhone),
                    todayDate = date,
                    sinceReset = current.sinceReset.plus(category, requests, bytes, fromPhone),
                    log = (listOf(entry) + current.log).take(LOG_SIZE),
                )
        }
    }

    /** Sorts a URL into a category. */
    fun categorize(url: HttpUrl): TrafficCategory {
        val host = url.host
        val path = url.encodedPath.lowercase()
        if (host.endsWith("khinsider.com")) {
            return if (path.endsWith(".mp3") || path.endsWith(".flac")) TrafficCategory.AUDIO else TrafficCategory.PAGES
        }
        if (host.endsWith("vgmtreasurechest.com") || host.endsWith("vgmsite.com")) {
            val audio = listOf(".mp3", ".flac", ".m4a", ".ogg", ".wav")
            if (audio.any(path::endsWith)) return TrafficCategory.AUDIO
            val folder = url.pathSegments.getOrNull(url.pathSegments.size - 2)
            return when (folder) {
                "thumbs", "thumbs_small" -> TrafficCategory.COVER_THUMB
                "thumbs_large" -> TrafficCategory.COVER_LARGE
                else -> TrafficCategory.COVER_FULL
            }
        }
        return TrafficCategory.OTHER
    }

    private fun shortName(url: HttpUrl): String {
        val segments = url.pathSegments.filter(String::isNotBlank)
        val tail = segments.takeLast(2).joinToString("/")
        return (url.host.substringBefore('.') + " · " + tail).take(90)
    }

    /**
     * Counts requests and the bytes actually read from each response.
     * [categoryOverride] forces a category, e.g. for liked cover saves.
     */
    fun interceptor(categoryOverride: TrafficCategory? = null): Interceptor =
        Interceptor { chain ->
            val response = chain.proceed(chain.request())
            if (!enabled) return@Interceptor response
            val url = chain.request().url
            val category = categoryOverride ?: categorize(url)
            countBody(response, category, shortName(url))
        }

    private fun countBody(
        response: Response,
        category: TrafficCategory,
        description: String,
    ): Response {
        val body = response.body
        var counted = 0L
        var reported = false
        val report = {
            if (!reported) {
                reported = true
                recordNetwork(category, counted, description)
            }
        }
        val source =
            object : ForwardingSource(body.source()) {
                override fun read(
                    sink: Buffer,
                    byteCount: Long,
                ): Long {
                    val read = super.read(sink, byteCount)
                    if (read > 0) counted += read else if (read == -1L) report()
                    return read
                }

                override fun close() {
                    report()
                    super.close()
                }
            }
        return response
            .newBuilder()
            .body(source.buffer().asResponseBody(body.contentType(), body.contentLength()))
            .build()
    }
}
