/*
 * Pixel Notes — Browse tab: collapsible sections of khinsider categories.
 * Charts and album types as tiles, platforms grouped by brand, years grouped by decade.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.screens

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.khinsider.KhinsiderCategory
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.utils.rememberPreference

private val BrowseOpenSectionsKey = stringSetPreferencesKey("pixelNotesBrowseOpenSections")
private val DefaultOpenSections = setOf("charts")

private data class Tile(
    val category: KhinsiderCategory,
    val color: Color,
    @DrawableRes val icon: Int,
)

private data class SubGroup(
    val id: String,
    val title: String,
    val countLabel: String,
    val color: Color,
    val tiles: List<Tile>,
)

// ------------------------------------------------------------------ data (styles in KhinsiderCategoryStyles)

private fun tileOf(category: KhinsiderCategory): Tile {
    val style = KhinsiderCategoryStyles.of(category)
    return Tile(category, style.color, style.icon)
}

private val YearGold = KhinsiderCategoryStyles.YearGold

private val charts: List<Tile> by lazy { KhinsiderCategory.charts.map(::tileOf) }

private val types: List<Tile> by lazy { KhinsiderCategory.types.map(::tileOf) }

private val platformGroups: List<SubGroup> by lazy {
    val byGroup = KhinsiderCategory.platforms.groupBy { it.group ?: "other" }
    KhinsiderCategoryStyles.platformGroups.mapNotNull { group ->
        val members = byGroup[group.id].orEmpty()
        if (members.isEmpty()) return@mapNotNull null
        SubGroup(
            id = "platform-${group.id}",
            title = group.title,
            countLabel = "${members.size} platforms",
            color = group.color,
            tiles = members.map(::tileOf),
        )
    }
}

private val decades: List<SubGroup> by lazy {
    KhinsiderCategory.years
        .groupBy { (it.title.toIntOrNull() ?: 0) / 10 * 10 }
        .toSortedMap(compareByDescending { it })
        .map { (decade, years) ->
            SubGroup(
                id = "decade-$decade",
                title = "${decade}s",
                countLabel = "${years.size} years",
                color = YearGold,
                tiles = years.map { Tile(it, YearGold, R.drawable.kh_ic_calendar_month) },
            )
        }
}

// ------------------------------------------------------------------ screen

@Composable
fun KhinsiderBrowsePage(
    navController: NavController,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    var openSections by rememberPreference(BrowseOpenSectionsKey, DefaultOpenSections)
    val toggle: (String) -> Unit = { id ->
        openSections = if (id in openSections) openSections - id else openSections + id
    }
    val open: (KhinsiderCategory) -> Unit = { category ->
        navController.navigate("browse/${Uri.encode(category.browseId)}")
    }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "title") {
            NavigationTitle(title = stringResource(R.string.mood_and_genres))
        }

        section("charts", "Charts", "${charts.size} charts", openSections, toggle) {
            tileRows("charts", charts, open)
        }
        section("types", "Album types", "${types.size} types", openSections, toggle) {
            tileRows("types", types, open)
        }
        section("platforms", "Platforms", "${KhinsiderCategory.platforms.size} platforms", openSections, toggle) {
            platformGroups.forEach { group ->
                subGroup(group, openSections, toggle) { tileRows(group.id, group.tiles, open) }
            }
        }
        val yearRange =
            KhinsiderCategory.years.let { years -> "${years.lastOrNull()?.title} – ${years.firstOrNull()?.title}" }
        section("years", "Years", yearRange, openSections, toggle) {
            decades.forEach { group ->
                subGroup(group, openSections, toggle) { yearRows(group, open) }
            }
        }
    }
}

private fun LazyListScope.section(
    id: String,
    title: String,
    subtitle: String,
    openSections: Set<String>,
    toggle: (String) -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val isOpen = id in openSections
    item(key = "section-$id", contentType = "section") {
        SectionHeader(title = title, subtitle = subtitle, open = isOpen, onClick = { toggle(id) })
    }
    if (isOpen) content()
}

private fun LazyListScope.subGroup(
    group: SubGroup,
    openSections: Set<String>,
    toggle: (String) -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val isOpen = group.id in openSections
    item(key = "sub-${group.id}", contentType = "subgroup") {
        SubGroupHeader(group = group, open = isOpen, onClick = { toggle(group.id) })
    }
    if (isOpen) {
        item(key = "sub-${group.id}-top", contentType = "spacer") { Spacer(Modifier.height(10.dp)) }
        content()
        item(key = "sub-${group.id}-bottom", contentType = "spacer") { Spacer(Modifier.height(4.dp)) }
    }
}

private fun LazyListScope.tileRows(
    keyPrefix: String,
    tiles: List<Tile>,
    open: (KhinsiderCategory) -> Unit,
) {
    tiles.chunked(2).forEachIndexed { index, row ->
        item(key = "$keyPrefix-row-$index", contentType = "tile_row") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                row.forEach { tile ->
                    CategoryTile(tile = tile, onClick = { open(tile.category) }, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun LazyListScope.yearRows(
    group: SubGroup,
    open: (KhinsiderCategory) -> Unit,
) {
    group.tiles.chunked(4).forEachIndexed { index, row ->
        item(key = "${group.id}-row-$index", contentType = "year_row") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                row.forEach { tile ->
                    YearChip(tile = tile, onClick = { open(tile.category) }, modifier = Modifier.weight(1f))
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    open: Boolean,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(if (open) 0f else -90f, label = "sectionChevron")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            painter = painterResource(R.drawable.expand_more),
            contentDescription = if (open) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.rotate(rotation),
        )
    }
}

@Composable
private fun SubGroupHeader(
    group: SubGroup,
    open: Boolean,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(if (open) 0f else -90f, label = "subChevron")
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(group.color),
            )
            Spacer(Modifier.width(12.dp))
            Text(group.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(
                group.countLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.expand_more),
                contentDescription = if (open) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.rotate(rotation),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

private fun tileBrush(color: Color): Brush =
    Brush.linearGradient(
        colors = listOf(lerp(color, Color.Black, 0.45f), color),
        start = Offset.Zero,
        end = Offset(700f, 420f),
    )

/** The Browse tile for [category], for use on other screens (e.g. Search). */
@Composable
fun KhinsiderCategoryTile(
    category: KhinsiderCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CategoryTile(tile = tileOf(category), onClick = onClick, modifier = modifier)
}

@Composable
private fun CategoryTile(
    tile: Tile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brush = remember(tile.color) { tileBrush(tile.color) }
    Box(
        modifier =
            modifier
                .height(104.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(brush)
                .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(tile.icon),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.22f),
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 12.dp)
                    .size(44.dp),
        )
        Text(
            text = tile.category.title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            lineHeight = 20.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun YearChip(
    tile: Tile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brush = remember(tile.color) { tileBrush(tile.color) }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(brush)
                .clickable(onClick = onClick),
    ) {
        Text(tile.category.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
