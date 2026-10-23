/*
 * Pixel Notes — khinsider source for ArchiveTune
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.innertube.khinsider

/**
 * A browsable khinsider list (chart, album type, platform or year).
 *
 * @param key stable key used in ids, e.g. "top40" or "platform-nintendo-switch".
 * @param path site path, e.g. "/top40".
 * @param paged whether the list continues over ?page=N.
 */
data class KhinsiderCategory(
    val key: String,
    val title: String,
    val path: String,
    val paged: Boolean,
    val color: Long,
    /** Platform brand group: nintendo, playstation, xbox, sega, computers or other. */
    val group: String? = null,
) {
    val browseId: String
        get() = KhinsiderIds.categoryId(key)

    data class Group(
        val title: String,
        val categories: List<KhinsiderCategory>,
    )

    companion object {
        private val CHART_COLOR = 0xFF6366F1
        private val TYPE_COLOR = 0xFF0EA5E9
        private val PLATFORM_COLOR = 0xFF10B981
        private val YEAR_COLOR = 0xFFF59E0B

        val TOP_40 = KhinsiderCategory("top40", "Top 40", "/top40", paged = false, color = 0xFFEF4444)
        val MOST_FAVORITES =
            KhinsiderCategory("most_favorites", "Most Favorites", "/most-favorites", paged = false, color = 0xFFEC4899)
        val TOP_1000_ALL_TIME =
            KhinsiderCategory("top1000", "Top 1000 All Time", "/all-time-top-1000", paged = true, color = CHART_COLOR)
        val TOP_100_LAST_6_MONTHS =
            KhinsiderCategory("top100_6m", "Top 100 Last 6 Months", "/last-6-months-top-100", paged = false, color = 0xFF8B5CF6)
        val TOP_100_NEWLY_ADDED =
            KhinsiderCategory("newly_added", "Top 100 Newly Added", "/top-100-newly-added", paged = false, color = 0xFF14B8A6)
        val CURRENTLY_VIEWED =
            KhinsiderCategory("currently_viewed", "Currently Viewed", "/currently-viewed", paged = false, color = 0xFFF97316)
        val BROWSE_ALL =
            KhinsiderCategory("browse_all", "Browse All (A–Z)", "/game-soundtracks", paged = true, color = 0xFF64748B)

        val charts =
            listOf(
                TOP_40,
                MOST_FAVORITES,
                TOP_100_NEWLY_ADDED,
                TOP_100_LAST_6_MONTHS,
                TOP_1000_ALL_TIME,
                CURRENTLY_VIEWED,
                BROWSE_ALL,
            )

        val types =
            listOf(
                "gamerips" to "Gamerips",
                "ost" to "Soundtracks",
                "singles" to "Singles",
                "arrangements" to "Arrangements",
                "remixes" to "Remixes",
                "compilations" to "Compilations",
                "inspired-by" to "Inspired By",
            ).map { (slug, title) ->
                KhinsiderCategory("type-$slug", title, "/game-soundtracks/$slug", paged = true, color = TYPE_COLOR)
            }

        /** Every platform on downloads.khinsider.com/console-list (69), grouped by brand. */
        val platforms =
            listOf(
                Triple("switch-2", "Switch 2", "nintendo"),
                Triple("nintendo-switch", "Switch", "nintendo"),
                Triple("nintendo-wii-u", "Wii U", "nintendo"),
                Triple("nintendo-wii", "Wii", "nintendo"),
                Triple("nintendo-gamecube", "GameCube", "nintendo"),
                Triple("nintendo-64", "Nintendo 64", "nintendo"),
                Triple("nintendo-snes", "SNES", "nintendo"),
                Triple("nintendo-nes", "NES", "nintendo"),
                Triple("family-computer", "Famicom", "nintendo"),
                Triple("famicom-disk-system", "Famicom Disk System", "nintendo"),
                Triple("virtual-boy", "Virtual Boy", "nintendo"),
                Triple("nintendo-3ds", "3DS", "nintendo"),
                Triple("nintendo-ds", "DS", "nintendo"),
                Triple("gameboy-advance", "Game Boy Advance", "nintendo"),
                Triple("gameboy", "Game Boy", "nintendo"),
                Triple("playstation-5", "PlayStation 5", "playstation"),
                Triple("playstation-4", "PlayStation 4", "playstation"),
                Triple("playstation-3", "PlayStation 3", "playstation"),
                Triple("playstation-2", "PlayStation 2", "playstation"),
                Triple("playstation", "PlayStation", "playstation"),
                Triple("playstation-vita", "PlayStation Vita", "playstation"),
                Triple("playstation-portable-psp", "PlayStation Portable", "playstation"),
                Triple("xbox-series-x", "Xbox Series X/S", "xbox"),
                Triple("xbox-one", "Xbox One", "xbox"),
                Triple("xbox-360", "Xbox 360", "xbox"),
                Triple("xbox", "Xbox", "xbox"),
                Triple("windows", "Windows", "computers"),
                Triple("steam", "Steam", "computers"),
                Triple("mac-os", "macOS", "computers"),
                Triple("linux", "Linux", "computers"),
                Triple("ms-dos", "MS-DOS", "computers"),
                Triple("ibm-pc", "IBM PC", "computers"),
                Triple("ibm-pc-at", "IBM PC/AT", "computers"),
                Triple("amiga", "Amiga", "computers"),
                Triple("atari-st", "Atari ST", "computers"),
                Triple("atari-8bit", "Atari 8-bit", "computers"),
                Triple("commodore-64", "Commodore 64", "computers"),
                Triple("spectrum", "ZX Spectrum", "computers"),
                Triple("msx", "MSX", "computers"),
                Triple("msx2", "MSX2", "computers"),
                Triple("pc-8801", "NEC PC-88", "computers"),
                Triple("pc-9801", "NEC PC-98", "computers"),
                Triple("pc-9821", "NEC PC-9821", "computers"),
                Triple("sharp-x1", "Sharp X1", "computers"),
                Triple("x68000", "Sharp X68000", "computers"),
                Triple("fm-towns", "FM Towns", "computers"),
                Triple("fujitsu-fm77av", "Fujitsu FM77AV", "computers"),
                Triple("mobile", "Mobile", "computers"),
                Triple("ios", "iOS", "computers"),
                Triple("android", "Android", "computers"),
                Triple("sega-dreamcast", "Dreamcast", "sega"),
                Triple("sega-saturn", "Saturn", "sega"),
                Triple("sega-mega-drive-genesis", "Genesis / Mega Drive", "sega"),
                Triple("sega-master-system", "Master System", "sega"),
                Triple("sega-game-gear", "Game Gear", "sega"),
                Triple("arcade", "Arcade", "other"),
                Triple("neo-geo", "Neo Geo", "other"),
                Triple("turbografx-16", "TurboGrafx-16", "other"),
                Triple("pc-fx", "PC-FX", "other"),
                Triple("3do", "3DO", "other"),
                Triple("atari-jaguar", "Atari Jaguar", "other"),
                Triple("cd-i", "Philips CD-i", "other"),
                Triple("stadia", "Stadia", "other"),
                Triple("virtual-reality", "Virtual Reality", "other"),
                Triple("online", "Online", "other"),
                Triple("fortnite", "Fortnite", "other"),
                Triple("roblox", "Roblox", "other"),
                Triple("anime", "Anime", "other"),
                Triple("movie", "Movie", "other"),
            ).map { (slug, title, group) ->
                KhinsiderCategory("platform-$slug", title, "/game-soundtracks/$slug", paged = true, color = PLATFORM_COLOR, group = group)
            }

        val years =
            (2026 downTo 1980).map { year ->
                KhinsiderCategory("year-$year", year.toString(), "/game-soundtracks/year/$year", paged = true, color = YEAR_COLOR)
            }

        val groups =
            listOf(
                Group("Charts", charts),
                Group("Album types", types),
                Group("Platforms", platforms),
                Group("Years", years),
            )

        private val byKey: Map<String, KhinsiderCategory> by lazy {
            (charts + types + platforms + years).associateBy { it.key }
        }

        fun fromKey(key: String): KhinsiderCategory? = byKey[key]

        fun fromBrowseId(browseId: String): KhinsiderCategory? = fromKey(KhinsiderIds.categoryKey(browseId))
    }
}
