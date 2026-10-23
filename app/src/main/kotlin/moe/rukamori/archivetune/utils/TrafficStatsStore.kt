/*
 * Pixel Notes — keeps the khinsider traffic counter's "today" and "since reset" totals between
 * launches, and follows the on/off switch in Developer options.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.innertube.khinsider.KhinsiderTraffic
import moe.rukamori.archivetune.innertube.khinsider.TrafficCategory
import moe.rukamori.archivetune.innertube.khinsider.TrafficCounter
import moe.rukamori.archivetune.innertube.khinsider.TrafficTotals
import org.json.JSONObject

/** On for now while testing; flip the default to false later. */
const val TrafficStatsDefaultOn = true
val TrafficStatsEnabledKey = booleanPreferencesKey("pixelNotesTrafficStats")

object TrafficStatsStore {
    private const val PREFS = "pixel_notes_traffic"
    private const val KEY = "totals"
    private const val SAVE_INTERVAL_MS = 15_000L

    fun start(
        context: Context,
        scope: CoroutineScope,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        runCatching { load(prefs.getString(KEY, null)) }

        scope.launch {
            context.dataStore.data
                .map { it[TrafficStatsEnabledKey] ?: TrafficStatsDefaultOn }
                .distinctUntilChanged()
                .collect { KhinsiderTraffic.enabled = it }
        }
        scope.launch(Dispatchers.IO) {
            var lastSaved: String? = null
            while (true) {
                delay(SAVE_INTERVAL_MS)
                val encoded = runCatching { encode() }.getOrNull() ?: continue
                if (encoded != lastSaved) {
                    prefs.edit().putString(KEY, encoded).apply()
                    lastSaved = encoded
                }
            }
        }
    }

    /** Saves straight away (after a reset, so it isn't undone by an older save). */
    fun saveNow(context: Context) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, encode())
            .apply()
    }

    private fun encode(): String {
        val snapshot = KhinsiderTraffic.snapshot.value
        return JSONObject()
            .put("todayDate", snapshot.todayDate)
            .put("today", encodeTotals(snapshot.today))
            .put("sinceReset", encodeTotals(snapshot.sinceReset))
            .put("resetAt", snapshot.resetAtMillis)
            .toString()
    }

    private fun load(json: String?) {
        if (json.isNullOrBlank()) return
        val root = JSONObject(json)
        KhinsiderTraffic.restore(
            today = decodeTotals(root.optJSONObject("today")),
            todayDate = root.optString("todayDate"),
            sinceReset = decodeTotals(root.optJSONObject("sinceReset")),
            resetAtMillis = root.optLong("resetAt", System.currentTimeMillis()),
        )
    }

    private fun encodeTotals(totals: TrafficTotals): JSONObject =
        JSONObject().apply {
            totals.byCategory.forEach { (category, counter) ->
                put(
                    category.name,
                    JSONObject()
                        .put("requests", counter.requests)
                        .put("bytes", counter.bytes)
                        .put("fromPhone", counter.fromPhone),
                )
            }
        }

    private fun decodeTotals(json: JSONObject?): TrafficTotals {
        if (json == null) return TrafficTotals()
        val map =
            TrafficCategory.entries.mapNotNull { category ->
                val item = json.optJSONObject(category.name) ?: return@mapNotNull null
                category to
                    TrafficCounter(
                        requests = item.optLong("requests"),
                        bytes = item.optLong("bytes"),
                        fromPhone = item.optLong("fromPhone"),
                    )
            }
        return TrafficTotals(map.toMap())
    }
}
