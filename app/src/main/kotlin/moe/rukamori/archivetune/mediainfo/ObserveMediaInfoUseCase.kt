/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.mediainfo

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

class ObserveMediaInfoUseCase @Inject constructor(private val repository: MediaInfoRepository) {
    operator fun invoke(videoId: String): Flow<LocalMediaInfo> = repository.observeLocal(videoId)
}

internal fun Throwable.asMediaInfoError(): MediaInfoState.Error {
    if (this is CancellationException) throw this
    return MediaInfoState.Error(
        if (this is IOException) MediaInfoError.Network else MediaInfoError.Unavailable,
    )
}
