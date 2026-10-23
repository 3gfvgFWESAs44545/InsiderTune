/*
 * Pixel Notes — khinsider category page (platform, chart, album type or year).
 * Themed header, "Top albums" carousel, then the full list with Sort, Filters and a grid/list toggle.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.layout
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.khinsider.KhSort
import moe.rukamori.archivetune.innertube.khinsider.KhSortField
import moe.rukamori.archivetune.innertube.khinsider.Khinsider
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.utils.navigateToAlbum
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.KhCategoryFilters
import moe.rukamori.archivetune.viewmodels.KhCategoryUiState
import moe.rukamori.archivetune.viewmodels.KhinsiderCategoryViewModel

private val ListViewKey = booleanPreferencesKey("pixelNotesCategoryListView")

private fun sortLabel(
    sort: KhSort?,
    ranked: Boolean,
): String =
    when {
        sort == null -> if (ranked) "Chart order" else "Name A–Z"
        else -> sort.label()
    }

private fun KhSort.label(): String =
    when (field) {
        KhSortField.NAME -> if (descending) "Name Z–A" else "Name A–Z"
        KhSortField.YEAR -> if (descending) "Year, newest" else "Year, oldest"
        KhSortField.ADDED -> if (descending) "Added, newest" else "Added, oldest"
    }

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun KhinsiderCategoryScreen(
    navController: NavController,
    viewModel: KhinsiderCategoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val category = state.category
    val style = remember(category) { category?.let(KhinsiderCategoryStyles::of) }
    val color = style?.color ?: MaterialTheme.colorScheme.primary
    var listView by rememberPreference(ListViewKey, false)
    var showSort by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val menuState = LocalMenuState.current
    val bottomPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val title = category?.title.orEmpty()

    val openAlbum: (Khinsider.KhAlbumSummary) -> Unit = { navController.navigateToAlbum(moe.rukamori.archivetune.innertube.khinsider.KhinsiderIds.albumId(it.slug)) }
    val albumMenu: (Khinsider.KhAlbumSummary) -> Unit = { album ->
        val item = with(Khinsider) { album.toAlbumItem() }
        menuState.show { YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss) }
    }

    // Infinite scroll.
    LaunchedEffect(gridState, state.hasMore) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }.distinctUntilChanged().collect { nearEnd -> if (nearEnd && state.hasMore) viewModel.loadMore() }
    }

    val sidePad = if (listView) 0.dp else 12.dp
    val fullWidth = Modifier.bleed(sidePad)

    LazyVerticalGrid(
        columns = if (listView) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 160.dp),
        state = gridState,
        contentPadding = PaddingValues(start = sidePad, end = sidePad, bottom = bottomPadding + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (listView) 0.dp else 12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            CategoryHeader(
                modifier = fullWidth,
                title = title,
                subtitle =
                    listOfNotNull(
                        style?.groupLabel?.takeIf(String::isNotBlank),
                        state.total?.let { "$it albums" },
                    ).joinToString(" · "),
                color = color,
                icon = style?.icon ?: R.drawable.kh_ic_album,
                onBack = navController::navigateUp,
            )
        }

        if (state.top.isNotEmpty()) {
            item(key = "top", span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = fullWidth) {
                    SectionTitle("Top albums for $title")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.top, key = { "top-${it.slug}" }) { album ->
                            AlbumCard(
                                album = album,
                                showMeta = false,
                                onClick = { openAlbum(album) },
                                onLongClick = { albumMenu(album) },
                                modifier = Modifier.width(150.dp),
                            )
                        }
                    }
                }
            }
        }

        item(key = "toolbar", span = { GridItemSpan(maxLineSpan) }) {
            Column(modifier = fullWidth.padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    SectionTitle("All $title albums", modifier = Modifier.weight(1f, fill = false))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    ToolbarButton(
                        icon = R.drawable.kh_ic_sort,
                        text = sortLabel(state.sort, ranked = category?.paged == false),
                        active = false,
                        accent = color,
                        onClick = { showSort = true },
                    )
                    ToolbarButton(
                        icon = R.drawable.kh_ic_filter_list,
                        text = if (state.filters.isEmpty) "Filters" else "Filters · ${state.filters.count}",
                        active = !state.filters.isEmpty,
                        accent = color,
                        onClick = { showFilters = true },
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { listView = !listView }) {
                        Icon(
                            painter = painterResource(if (listView) R.drawable.kh_ic_grid_view else R.drawable.kh_ic_view_list),
                            contentDescription = if (listView) "Show as grid" else "Show as list",
                        )
                    }
                }
                if (!state.sortOnServer) {
                    Text(
                        "khinsider can't sort this list that way, so only the albums loaded so far are sorted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }

        when {
            state.loading && state.albums.isEmpty() -> {
                item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = color)
                    }
                }
            }

            state.error -> {
                item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    ) {
                        Text("Couldn't load this list from khinsider.", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = viewModel::retry) { Text("Try again") }
                    }
                }
            }

            state.visible.isEmpty() && !state.hasMore && !state.loadingMore -> {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (state.filters.isEmpty) "No albums here yet." else "No albums match these filters.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            else -> {
                if (listView) {
                    items(state.visible, key = { "row-${it.slug}" }) { album ->
                        AlbumRow(album = album, onClick = { openAlbum(album) }, onLongClick = { albumMenu(album) })
                    }
                } else {
                    items(state.visible, key = { "card-${it.slug}" }) { album ->
                        AlbumCard(
                            album = album,
                            showMeta = true,
                            onClick = { openAlbum(album) },
                            onLongClick = { albumMenu(album) },
                            modifier = Modifier.padding(bottom = 14.dp),
                        )
                    }
                }
            }
        }

        if (state.loadingMore || (state.loading && state.albums.isNotEmpty())) {
            item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = color)
                }
            }
        }
    }

    if (showSort) {
        SortSheet(
            current = state.sort,
            ranked = category?.paged == false,
            accent = color,
            onPick = {
                showSort = false
                viewModel.setSort(it)
            },
            onDismiss = { showSort = false },
        )
    }
    if (showFilters) {
        FilterSheet(
            state = state,
            accent = color,
            onApply = {
                showFilters = false
                viewModel.setFilters(it)
            },
            onDismiss = { showFilters = false },
        )
    }
}

// ------------------------------------------------------------------ header and toolbar

/** Lets a full-width grid row ignore the grid's side padding. */
private fun Modifier.bleed(horizontal: Dp): Modifier =
    layout { measurable, constraints ->
        val extra = horizontal.roundToPx()
        val placeable =
            measurable.measure(
                constraints.copy(
                    minWidth = constraints.minWidth + extra * 2,
                    maxWidth = constraints.maxWidth + extra * 2,
                ),
            )
        layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
    }

@Composable
private fun CategoryHeader(
    modifier: Modifier,
    title: String,
    subtitle: String,
    color: Color,
    icon: Int,
    onBack: () -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val brush =
        remember(color, background) {
            Brush.verticalGradient(
                0f to lerp(color, Color.Black, 0.3f),
                0.7f to lerp(color, background, 0.75f),
                1f to background,
            )
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(brush)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(bottom = 8.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
            Icon(painterResource(R.drawable.arrow_back), contentDescription = "Back", tint = Color.White)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.18f)),
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun ToolbarButton(
    icon: Int,
    text: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .clip(shape)
                .then(
                    if (active) {
                        Modifier.background(accent.copy(alpha = 0.25f)).border(1.dp, accent, shape)
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    },
                ).clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

// ------------------------------------------------------------------ album views

private fun Khinsider.KhAlbumSummary.meta(): String = listOfNotNull(albumType?.takeIf(String::isNotBlank), year?.toString()).joinToString(" · ")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    album: Khinsider.KhAlbumSummary,
    showMeta: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = album.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Text(
            album.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (showMeta) {
            val meta = album.meta()
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumRow(
    album: Khinsider.KhAlbumSummary,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        AsyncImage(
            model = album.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                album.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = album.meta()
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

// ------------------------------------------------------------------ sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(
    current: KhSort?,
    ranked: Boolean,
    accent: Color,
    onPick: (KhSort?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("Sort by", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
            // For lists, "A → Z" is khinsider's own order (null); charts get their ranking as an extra choice.
            val nameAsc: KhSort? = if (ranked) KhSort(KhSortField.NAME, false) else null
            buildList {
                if (ranked) add("Chart" to listOf<Pair<KhSort?, String>>(null to "Chart order"))
                add("Name" to listOf<Pair<KhSort?, String>>(nameAsc to "A → Z", KhSort(KhSortField.NAME, true) to "Z → A"))
                add("Year" to listOf<Pair<KhSort?, String>>(KhSort(KhSortField.YEAR, true) to "Newest first", KhSort(KhSortField.YEAR, false) to "Oldest first"))
                // Charts have no "added" dates to sort by.
                if (!ranked) {
                    add(
                        "Date added to khinsider" to
                            listOf<Pair<KhSort?, String>>(KhSort(KhSortField.ADDED, true) to "Newest first", KhSort(KhSortField.ADDED, false) to "Oldest first"),
                    )
                }
            }.forEach { (group, options) ->
                Text(
                    group.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                )
                options.forEach { (sort, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(sort) }
                                .padding(horizontal = 4.dp, vertical = 12.dp),
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (sort == current) {
                            Icon(painterResource(R.drawable.kh_ic_check), contentDescription = "Selected", tint = accent)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    state: KhCategoryUiState,
    accent: Color,
    onApply: (KhCategoryFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var types by remember { mutableStateOf(state.filters.types) }
    var years by remember { mutableStateOf(state.filters.years) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
        ) {
            Text("Filters", style = MaterialTheme.typography.headlineSmall)
            Text(
                "TYPE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.typeOptions.forEach { type ->
                    val selected = type in types
                    FilterChip(
                        selected = selected,
                        onClick = { types = if (selected) types - type else types + type },
                        label = { Text(type) },
                        leadingIcon =
                            if (selected) {
                                { Icon(painterResource(R.drawable.kh_ic_check), null, modifier = Modifier.size(16.dp)) }
                            } else {
                                null
                            },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.35f)),
                    )
                }
            }
            if (state.yearOptions.isNotEmpty()) {
                Text(
                    "YEAR",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.yearOptions.forEach { year ->
                        val selected = year in years
                        FilterChip(
                            selected = selected,
                            onClick = { years = if (selected) years - year else years + year },
                            label = { Text(year.toString()) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.35f)),
                        )
                    }
                }
                Text(
                    "Years come from the albums loaded so far.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                TextButton(onClick = {
                    types = emptySet()
                    years = emptySet()
                }) { Text("Clear all") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onApply(KhCategoryFilters(types, years)) }) { Text("Show albums") }
            }
        }
    }
}
