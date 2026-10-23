/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import android.text.format.Formatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.khinsider.KhinsiderIds
import moe.rukamori.archivetune.mediainfo.LocalMediaInfo
import moe.rukamori.archivetune.mediainfo.MediaInfoDetail
import moe.rukamori.archivetune.mediainfo.MediaInfoEvent
import moe.rukamori.archivetune.mediainfo.MediaInfoState
import moe.rukamori.archivetune.mediainfo.MediaInfoUiModel
import moe.rukamori.archivetune.mediainfo.ObserveMediaInfoUseCase
import moe.rukamori.archivetune.mediainfo.asMediaInfoError

/** Pixel Notes: song information from the phone only (no YouTube or third-party lookups). */
@HiltViewModel
class MediaInfoViewModel @Inject constructor(
    private val observeMediaInfo: ObserveMediaInfoUseCase,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val mutableState = MutableStateFlow<MediaInfoState<MediaInfoUiModel>>(MediaInfoState.Loading)
    val state = mutableState.asStateFlow()
    private val eventChannel = Channel<MediaInfoEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var loadJob: Job? = null
    private var currentVideoId: String? = null
    private var currentModel: MediaInfoUiModel? = null

    fun open(videoId: String) {
        if (currentVideoId == videoId && loadJob?.isActive == true) return
        loadJob?.cancel()
        currentVideoId = videoId
        mutableState.value = MediaInfoState.Loading
        if (videoId.isBlank()) {
            mutableState.value = MediaInfoState.Empty
            return
        }
        loadJob = viewModelScope.launch {
            observeMediaInfo(videoId)
                .map<LocalMediaInfo, MediaInfoState<MediaInfoUiModel>> { info ->
                    MediaInfoState.Success(mapData(videoId, info).also { currentModel = it })
                }
                .catch { failure -> emit(failure.asMediaInfoError()) }
                .collect { mutableState.value = it }
        }
    }

    fun release(videoId: String) {
        if (currentVideoId != videoId) return
        loadJob?.cancel()
        loadJob = null
        currentVideoId = null
        currentModel = null
        mutableState.value = MediaInfoState.Loading
    }

    fun copy(value: String) {
        eventChannel.trySend(MediaInfoEvent.Copy(value))
    }

    fun share() {
        val model = currentModel ?: return
        val albumTitle = model.overview.firstOrNull { it.labelRes == R.string.media_info_album }?.value
        val text = KhinsiderIds.songShareText(model.videoId, model.title, albumTitle) ?: model.webUrl ?: return
        eventChannel.trySend(MediaInfoEvent.Share(text))
    }

    fun openOnWeb() {
        currentModel?.webUrl?.let { eventChannel.trySend(MediaInfoEvent.Open(it)) }
    }

    fun close() {
        eventChannel.trySend(MediaInfoEvent.Close)
    }

    private fun mapData(
        videoId: String,
        info: LocalMediaInfo,
    ): MediaInfoUiModel {
        val format = info.format
        val fileSize = format?.contentLength?.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
        val bitrate = format?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" }
        val formatName =
            when {
                format?.mimeType?.contains("flac", ignoreCase = true) == true -> "FLAC"
                format?.mimeType?.contains("mpeg", ignoreCase = true) == true -> "MP3"
                else ->
                    KhinsiderIds
                        .songPath(videoId)
                        ?.substringAfterLast('.', "")
                        ?.takeIf { it.length in 2..4 }
                        ?.uppercase()
            }
        val trackNumber = KhinsiderIds.trackNumberFromFileName(videoId)
        val overview =
            buildList {
                add(MediaInfoDetail(R.string.song_title, info.title))
                add(MediaInfoDetail(R.string.song_artists, info.artists))
                info.albumTitle?.let { add(MediaInfoDetail(R.string.media_info_album, it)) }
                trackNumber?.let { add(MediaInfoDetail(R.string.media_info_track, it.toString())) }
                info.year?.let { add(MediaInfoDetail(R.string.media_info_year, it.toString())) }
            }
        val audio =
            buildList {
                formatName?.let { add(MediaInfoDetail(R.string.media_info_format, it)) }
                bitrate?.let { add(MediaInfoDetail(R.string.bitrate, it)) }
                format?.sampleRate?.takeIf { it > 0 }?.let { add(MediaInfoDetail(R.string.sample_rate, "$it Hz")) }
                fileSize?.let { add(MediaInfoDetail(R.string.file_size, it)) }
            }
        return MediaInfoUiModel(
            videoId = videoId,
            title = info.title ?: videoId,
            subtitle = info.artists,
            artwork = info.artwork,
            overview = ImmutableList.copyOf(overview),
            audio = ImmutableList.copyOf(audio),
            webUrl = KhinsiderIds.webUrl(videoId),
        )
    }
}
