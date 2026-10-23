/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.mediainfo

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.google.common.collect.ImmutableList

@Immutable
sealed interface MediaInfoState<out T> {
    data object Loading : MediaInfoState<Nothing>

    @Immutable
    data class Success<T>(val data: T) : MediaInfoState<T>

    data object Empty : MediaInfoState<Nothing>

    @Immutable
    data class Error(val reason: MediaInfoError) : MediaInfoState<Nothing>
}

enum class MediaInfoError {
    Network,
    Unavailable,
}

/** Pixel Notes: everything shown comes from the phone; nothing is looked up online. */
@Immutable
data class LocalMediaInfo(
    val title: String?,
    val artists: String?,
    val artwork: String?,
    val albumTitle: String?,
    val year: Int?,
    val isLocal: Boolean,
    val format: MediaInfoFormat?,
)

@Immutable
data class MediaInfoFormat(
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val sampleRate: Int?,
    val contentLength: Long,
)

@Immutable
data class MediaInfoDetail(@param:StringRes val labelRes: Int, val value: String?)

@Immutable
data class MediaInfoUiModel(
    val videoId: String,
    val title: String,
    val subtitle: String?,
    val artwork: String?,
    val overview: ImmutableList<MediaInfoDetail>,
    val audio: ImmutableList<MediaInfoDetail>,
    /** The khinsider album page, when this is a khinsider song. */
    val webUrl: String?,
)

sealed interface MediaInfoEvent {
    data class Copy(val value: String) : MediaInfoEvent
    data class Share(val text: String) : MediaInfoEvent
    data class Open(val url: String) : MediaInfoEvent
    data object Close : MediaInfoEvent
}
