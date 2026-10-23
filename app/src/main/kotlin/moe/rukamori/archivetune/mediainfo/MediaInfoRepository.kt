/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.mediainfo

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import moe.rukamori.archivetune.db.MusicDatabase

class MediaInfoRepository @Inject constructor(private val database: MusicDatabase) {
    fun observeLocal(videoId: String): Flow<LocalMediaInfo> =
        combine(database.song(videoId), database.format(videoId)) { song, format ->
            LocalMediaInfo(
                title = song?.title,
                artists = song?.artists?.takeIf { it.isNotEmpty() }?.joinToString { it.name },
                artwork = song?.thumbnailUrl,
                albumTitle = song?.album?.title ?: song?.song?.albumName,
                year = song?.album?.year ?: song?.song?.year,
                isLocal = song?.song?.isLocal == true,
                format = format?.let {
                    MediaInfoFormat(
                        mimeType = it.mimeType,
                        codecs = it.codecs,
                        bitrate = it.bitrate,
                        sampleRate = it.sampleRate,
                        contentLength = it.contentLength,
                    )
                },
            )
        }.flowOn(Dispatchers.IO)
}
