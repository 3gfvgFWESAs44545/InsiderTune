/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.library

import moe.rukamori.archivetune.utils.LibraryLoginRequiredException
import moe.rukamori.archivetune.utils.LibrarySyncDisabledException
import moe.rukamori.archivetune.utils.SyncUtils
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

enum class LibrarySyncTarget {
    All,
    LikedSongs,
    Songs,
    Artists,
    Albums,
    Playlists,
    Podcasts,
}

enum class LibrarySyncFailure {
    LoginRequired,
    SyncDisabled,
    RequestFailed,
}

sealed interface RefreshLibraryResult {
    data object Success : RefreshLibraryResult

    data class Failure(val reason: LibrarySyncFailure) : RefreshLibraryResult
}

class RefreshLibraryUseCase
    @Inject
    constructor(
        private val repository: SyncUtils,
        private val khinsiderSync: moe.rukamori.archivetune.khinsider.KhinsiderSyncManager,
    ) {
        suspend operator fun invoke(target: LibrarySyncTarget): RefreshLibraryResult {
            // Pixel Notes: there is no YouTube Music account. Refreshing syncs with khinsider when
            // signed in; otherwise there is nothing to sync and the refresh simply succeeds.
            if (moe.rukamori.archivetune.innertube.YouTube.khinsiderMode) {
                if (!moe.rukamori.archivetune.innertube.khinsider.KhinsiderAccount.isSignedIn) return RefreshLibraryResult.Success
                return try {
                    val result = khinsiderSync.syncNow()
                    if (result.errors.isEmpty()) {
                        RefreshLibraryResult.Success
                    } else {
                        RefreshLibraryResult.Failure(LibrarySyncFailure.RequestFailed)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Timber.e(e, "khinsider sync failed")
                    RefreshLibraryResult.Failure(LibrarySyncFailure.RequestFailed)
                }
            }
            return youTubeRefresh(target)
        }

        private suspend fun youTubeRefresh(target: LibrarySyncTarget): RefreshLibraryResult =
            try {
                repository.requireLibrarySyncEnabled()
                when (target) {
                    LibrarySyncTarget.All -> repository.performFullSync(propagateFailures = true)
                    LibrarySyncTarget.LikedSongs -> repository.syncLikedSongs(propagateFailures = true)
                    LibrarySyncTarget.Songs -> repository.syncLibrarySongs(propagateFailures = true)
                    LibrarySyncTarget.Artists -> repository.syncArtistsSubscriptions(propagateFailures = true)
                    LibrarySyncTarget.Albums -> repository.syncLikedAlbums(propagateFailures = true)
                    LibrarySyncTarget.Playlists -> {
                        repository.syncSavedPlaylists(propagateFailures = true)
                        repository.syncAutoSyncPlaylists(propagateFailures = true)
                    }
                    LibrarySyncTarget.Podcasts -> repository.syncSavedPodcasts(propagateFailures = true)
                }
                RefreshLibraryResult.Success
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Library refresh failed for %s", target)
                RefreshLibraryResult.Failure(
                    when (e) {
                        is LibraryLoginRequiredException -> LibrarySyncFailure.LoginRequired
                        is LibrarySyncDisabledException -> LibrarySyncFailure.SyncDisabled
                        else -> LibrarySyncFailure.RequestFailed
                    },
                )
            }
    }
