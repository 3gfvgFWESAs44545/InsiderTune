/*
 * Pixel Notes — optional khinsider account
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.KhinsiderCookieKey
import moe.rukamori.archivetune.constants.KhinsiderLastSyncKey
import moe.rukamori.archivetune.constants.KhinsiderSyncFavoritesKey
import moe.rukamori.archivetune.constants.KhinsiderSyncHistoryKey
import moe.rukamori.archivetune.constants.KhinsiderSyncPlaylistsKey
import moe.rukamori.archivetune.constants.KhinsiderUsernameKey
import moe.rukamori.archivetune.khinsider.KhinsiderSyncManager
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject

data class KhinsiderAccountUiState(
    val signedIn: Boolean = false,
    val username: String = "",
    val syncFavorites: Boolean = true,
    val syncHistory: Boolean = true,
    val syncPlaylists: Boolean = true,
    val lastSync: Long? = null,
)

sealed interface KhinsiderSignInState {
    data object Idle : KhinsiderSignInState

    data object Checking : KhinsiderSignInState

    data class Success(
        val username: String,
    ) : KhinsiderSignInState
}

@HiltViewModel
class KhinsiderAccountViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val syncManager: KhinsiderSyncManager,
    ) : ViewModel() {
        val uiState: StateFlow<KhinsiderAccountUiState> =
            context.dataStore.data
                .map { prefs ->
                    KhinsiderAccountUiState(
                        signedIn = !prefs[KhinsiderCookieKey].isNullOrBlank(),
                        username = prefs[KhinsiderUsernameKey].orEmpty(),
                        syncFavorites = prefs[KhinsiderSyncFavoritesKey] ?: true,
                        syncHistory = prefs[KhinsiderSyncHistoryKey] ?: true,
                        syncPlaylists = prefs[KhinsiderSyncPlaylistsKey] ?: true,
                        lastSync = prefs[KhinsiderLastSyncKey],
                    )
                }.stateIn(viewModelScope, SharingStarted.Eagerly, KhinsiderAccountUiState())

        private val _isSyncing = MutableStateFlow(false)
        val isSyncing = _isSyncing.asStateFlow()

        private val _messages = MutableStateFlow<String?>(null)
        val messages = _messages.asStateFlow()

        private val _signInState = MutableStateFlow<KhinsiderSignInState>(KhinsiderSignInState.Idle)
        val signInState = _signInState.asStateFlow()
        private var lastTriedCookie: String? = null

        fun consumeMessage() {
            _messages.value = null
        }

        /** Called by the sign-in WebView after each page load with khinsider's cookies. */
        fun onCookies(cookie: String?) {
            val value = cookie?.trim().orEmpty()
            if (value.isEmpty() || value == lastTriedCookie) return
            if (_signInState.value is KhinsiderSignInState.Success || _signInState.value == KhinsiderSignInState.Checking) return
            lastTriedCookie = value
            _signInState.value = KhinsiderSignInState.Checking
            viewModelScope.launch {
                val username = runCatching { syncManager.signIn(value) }.getOrNull()
                if (username != null) {
                    _signInState.value = KhinsiderSignInState.Success(username)
                } else {
                    _signInState.value = KhinsiderSignInState.Idle
                }
            }
        }

        fun syncNow() {
            if (_isSyncing.value) return
            viewModelScope.launch {
                _isSyncing.value = true
                try {
                    val result = syncManager.syncNow()
                    _messages.value =
                        if (result.errors.isNotEmpty()) {
                            result.errors.joinToString("\n")
                        } else {
                            buildString {
                                append("Synced with khinsider")
                                val parts =
                                    listOfNotNull(
                                        result.favoritesImported.takeIf { it > 0 }?.let { "$it favourites added" },
                                        result.favoritesUploaded.takeIf { it > 0 }?.let { "$it likes sent" },
                                        result.playlistsImported.takeIf { it > 0 }?.let { "$it playlists imported" },
                                    )
                                if (parts.isNotEmpty()) append(": ").append(parts.joinToString(", "))
                            }
                        }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _messages.value = "Sync failed: ${e.message ?: "unknown error"}"
                } finally {
                    _isSyncing.value = false
                }
            }
        }

        fun signOut() {
            viewModelScope.launch {
                syncManager.signOut()
                android.webkit.CookieManager.getInstance().apply {
                    removeAllCookies(null)
                    flush()
                }
                lastTriedCookie = null
                _signInState.value = KhinsiderSignInState.Idle
                _messages.value = "Signed out of khinsider"
            }
        }

        fun setSyncFavorites(enabled: Boolean) = setPreference(KhinsiderSyncFavoritesKey, enabled)

        fun setSyncHistory(enabled: Boolean) = setPreference(KhinsiderSyncHistoryKey, enabled)

        fun setSyncPlaylists(enabled: Boolean) = setPreference(KhinsiderSyncPlaylistsKey, enabled)

        private fun setPreference(
            key: Preferences.Key<Boolean>,
            value: Boolean,
        ) {
            viewModelScope.launch {
                context.dataStore.updateData { prefs ->
                    prefs.toMutablePreferences().apply { this[key] = value }
                }
            }
        }
    }
