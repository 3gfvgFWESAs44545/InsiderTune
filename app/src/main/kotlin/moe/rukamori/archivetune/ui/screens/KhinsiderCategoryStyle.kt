/*
 * Pixel Notes — colour, icon and group for every khinsider category.
 * Shared by the Browse tiles and the category page header so they always match.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.khinsider.KhinsiderCategory

data class KhinsiderCategoryStyle(
    val color: Color,
    @DrawableRes val icon: Int,
    /** Shown under the title on the category page, e.g. "Nintendo" or "Chart". */
    val groupLabel: String,
)

data class KhinsiderPlatformGroup(
    val id: String,
    val title: String,
    val color: Color,
)

object KhinsiderCategoryStyles {
    val Nintendo = Color(0xFFE4002B)
    val PlayStation = Color(0xFF2563EB)
    val Xbox = Color(0xFF16A34A)
    val Computers = Color(0xFF475569)
    val Sega = Color(0xFF06B6D4)
    val OtherPlatforms = Color(0xFF9333EA)
    val YearGold = Color(0xFFC98A1A)

    /** Brand groups in the order they appear on the Browse tab. */
    val platformGroups =
        listOf(
            KhinsiderPlatformGroup("nintendo", "Nintendo", Nintendo),
            KhinsiderPlatformGroup("playstation", "PlayStation", PlayStation),
            KhinsiderPlatformGroup("xbox", "Xbox", Xbox),
            KhinsiderPlatformGroup("computers", "PC, Computers & Mobile", Computers),
            KhinsiderPlatformGroup("sega", "Sega", Sega),
            KhinsiderPlatformGroup("other", "Other", OtherPlatforms),
        )

    private val chartStyle: Map<String, Pair<Long, Int>> =
        mapOf(
            "top40" to (0xFFEF4444 to R.drawable.kh_ic_leaderboard),
            "most_favorites" to (0xFFEC4899 to R.drawable.kh_ic_favorite),
            "newly_added" to (0xFF14B8A6 to R.drawable.kh_ic_new_releases),
            "top100_6m" to (0xFF8B5CF6 to R.drawable.kh_ic_calendar_month),
            "top1000" to (0xFF5B5BD6 to R.drawable.kh_ic_emoji_events),
            "currently_viewed" to (0xFFF97316 to R.drawable.kh_ic_visibility),
            "browse_all" to (0xFF64748B to R.drawable.kh_ic_sort_by_alpha),
        )

    private val typeStyle: Map<String, Pair<Long, Int>> =
        mapOf(
            "type-gamerips" to (0xFF0EA5E9 to R.drawable.kh_ic_memory),
            "type-ost" to (0xFF0284C7 to R.drawable.kh_ic_album),
            "type-singles" to (0xFF06B6D4 to R.drawable.kh_ic_music_note),
            "type-arrangements" to (0xFF0891B2 to R.drawable.kh_ic_piano),
            "type-remixes" to (0xFF2563EB to R.drawable.kh_ic_instant_mix),
            "type-compilations" to (0xFF1D4ED8 to R.drawable.kh_ic_library_music),
            "type-inspired-by" to (0xFF0369A1 to R.drawable.kh_ic_auto_awesome),
        )

    private val handhelds =
        setOf(
            "nintendo-3ds", "nintendo-ds", "gameboy-advance", "gameboy", "virtual-boy",
            "playstation-vita", "playstation-portable-psp", "sega-game-gear",
        )
    private val phones = setOf("mobile", "ios", "android")

    private fun platformIcon(slug: String, group: String?): Int =
        when {
            slug in handhelds -> R.drawable.kh_ic_videogame_asset
            slug in phones -> R.drawable.kh_ic_smartphone
            group == "computers" -> R.drawable.kh_ic_computer
            slug == "arcade" -> R.drawable.kh_ic_joystick
            slug == "anime" -> R.drawable.kh_ic_animation
            slug == "movie" -> R.drawable.kh_ic_movie
            slug == "virtual-reality" -> R.drawable.kh_ic_vrpano
            slug == "online" -> R.drawable.kh_ic_public
            else -> R.drawable.kh_ic_sports_esports
        }

    fun groupOf(category: KhinsiderCategory): KhinsiderPlatformGroup? =
        platformGroups.firstOrNull { it.id == category.group }

    fun of(category: KhinsiderCategory): KhinsiderCategoryStyle {
        val key = category.key
        chartStyle[key]?.let { (color, icon) -> return KhinsiderCategoryStyle(Color(color), icon, "Chart") }
        typeStyle[key]?.let { (color, icon) -> return KhinsiderCategoryStyle(Color(color), icon, "Album type") }
        if (key.startsWith("platform-")) {
            val group = groupOf(category)
            return KhinsiderCategoryStyle(
                color = group?.color ?: OtherPlatforms,
                icon = platformIcon(key.removePrefix("platform-"), category.group),
                groupLabel = group?.title ?: "Platform",
            )
        }
        if (key.startsWith("year-")) return KhinsiderCategoryStyle(YearGold, R.drawable.kh_ic_calendar_month, "Year")
        return KhinsiderCategoryStyle(Color(category.color), R.drawable.kh_ic_album, "")
    }
}
