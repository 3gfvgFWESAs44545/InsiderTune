/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.awaitCancellation
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.mediainfo.MediaInfoDetail
import moe.rukamori.archivetune.mediainfo.MediaInfoEvent
import moe.rukamori.archivetune.mediainfo.MediaInfoState
import moe.rukamori.archivetune.mediainfo.MediaInfoUiModel
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.viewmodels.MediaInfoViewModel

@Composable
fun ShowMediaInfo(videoId: String) {
    val viewModel: MediaInfoViewModel = hiltViewModel()
    val context = LocalContext.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentState = state

    LaunchedEffect(videoId, viewModel) {
        viewModel.open(videoId)
        try {
            awaitCancellation()
        } finally {
            viewModel.release(videoId)
        }
    }
    LaunchedEffect(viewModel, lifecycleOwner, context, bottomSheetPageState) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is MediaInfoEvent.Copy -> copyToClipboard(context, event.value)
                    is MediaInfoEvent.Share -> shareMediaLink(context, event.text)
                    is MediaInfoEvent.Open -> openInBrowser(context, event.url)
                    MediaInfoEvent.Close -> bottomSheetPageState.dismiss()
                }
            }
        }
    }
    MediaInfoContent(
        state = if (currentState is MediaInfoState.Success && currentState.data.videoId != videoId) {
            MediaInfoState.Loading
        } else {
            currentState
        },
        onCopy = remember(viewModel) { viewModel::copy },
        onShare = remember(viewModel) { viewModel::share },
        onOpen = remember(viewModel) { viewModel::openOnWeb },
        onClose = remember(viewModel) { viewModel::close },
    )
}

@Composable
private fun MediaInfoContent(
    state: MediaInfoState<MediaInfoUiModel>,
    onCopy: (String) -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    val model = (state as? MediaInfoState.Success)?.data
    if (model == null) {
        MediaInfoStatusCard(title = stringResource(R.string.information), state = state)
        return
    }
    val unknownText = stringResource(R.string.unknown)
    val copyText = stringResource(R.string.copy)
    val shareText = stringResource(R.string.share)
    val closeText = stringResource(R.string.close)
    val informationLabel = stringResource(R.string.information)
    val audioLabel = stringResource(R.string.media_info_audio)

    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "Hero", contentType = "Hero") {
            MediaInfoHeroCard(
                title = model.title,
                subtitle = model.subtitle ?: unknownText,
                artworkModel = model.artwork,
                sectionLabel = informationLabel,
                closeText = closeText,
                onClose = onClose,
            )
        }

        item(key = "Actions", contentType = "Actions") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilledTonalButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = shareText)
                }

                if (model.webUrl != null) {
                    OutlinedButton(
                        onClick = onOpen,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.kh_ic_open_in_new),
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.open_on_khinsider),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        item(key = "Overview", contentType = "Details") {
            Column {
                MediaInfoSectionTitle(informationLabel)
                MediaInfoDetailCard(
                    items = model.overview,
                    copyContentDescription = copyText,
                    onCopy = onCopy,
                )
            }
        }

        if (model.audio.isNotEmpty()) {
            item(key = "Audio", contentType = "Details") {
                Column {
                    MediaInfoSectionTitle(audioLabel)
                    MediaInfoDetailCard(
                        items = model.audio,
                        copyContentDescription = copyText,
                        onCopy = onCopy,
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaInfoSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun MediaInfoHeroCard(
    title: String,
    subtitle: String,
    artworkModel: String?,
    sectionLabel: String,
    closeText: String,
    onClose: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(88.dp),
            ) {
                if (artworkModel != null) {
                    AsyncImage(
                        model = artworkModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.large),
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.music_note),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = sectionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

            }

            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = closeText,
                )
            }
        }
    }
}

@Composable
private fun MediaInfoDetailCard(
    items: ImmutableList<MediaInfoDetail>,
    copyContentDescription: String,
    onCopy: (String) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                ListItem(
                    overlineContent = {
                        Text(text = stringResource(item.labelRes))
                    },
                    headlineContent = {
                        Text(
                            text = item.value ?: stringResource(R.string.unknown),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        Icon(
                            painter = painterResource(R.drawable.copy),
                            contentDescription = copyContentDescription,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(
                        enabled = item.value != null,
                        onClick = remember(onCopy, item.value) { { item.value?.let(onCopy) } },
                    ),
                )

                if (index != items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaInfoStatusCard(
    title: String,
    state: MediaInfoState<*>,
) {
    val message = stringResource(
        when (state) {
            MediaInfoState.Loading -> R.string.please_wait
            is MediaInfoState.Error -> R.string.media_info_load_error
            MediaInfoState.Empty, is MediaInfoState.Success -> R.string.media_info_empty
        },
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            if (state is MediaInfoState.Loading) {
                LoadingIndicator(modifier = Modifier.size(40.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun copyToClipboard(
    context: Context,
    value: String,
) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(ClipData.newPlainText("text", value))
    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
}

private fun openInBrowser(
    context: Context,
    url: String,
) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun shareMediaLink(
    context: Context,
    mediaUrl: String,
) {
    val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, mediaUrl)
        }
    context.startActivity(Intent.createChooser(shareIntent, null))
}
