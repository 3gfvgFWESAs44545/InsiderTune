/*
 * Pixel Notes — optional khinsider account
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.khinsider.Khinsider
import moe.rukamori.archivetune.innertube.khinsider.KhinsiderAccount
import moe.rukamori.archivetune.viewmodels.KhinsiderAccountViewModel
import moe.rukamori.archivetune.viewmodels.KhinsiderSignInState
import java.text.DateFormat
import java.util.Date

const val KhinsiderAccountRoute = "settings/khinsider_account"
const val KhinsiderSignInRoute = "settings/khinsider_sign_in"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhinsiderAccountScreen(
    navController: NavController,
    viewModel: KhinsiderAccountViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()

    // First sync right after signing in (sign-out clears the last-sync time).
    LaunchedEffect(state.signedIn, state.lastSync) {
        if (state.signedIn && state.lastSync == null) viewModel.syncNow()
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
    ) {
        TopAppBar(
            title = { Text("khinsider account") },
            navigationIcon = {
                IconButton(onClick = navController::navigateUp) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
        )
        if (isSyncing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.account), contentDescription = null, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (state.signedIn) state.username.ifBlank { "Signed in" } else "Not signed in",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text =
                            if (state.signedIn) {
                                "Your khinsider account is connected."
                            } else {
                                "Signing in is optional. Pixel Notes works fully without an account."
                            },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.lastSync?.takeIf { state.signedIn }?.let { time ->
                        Text(
                            text = "Last synced " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (!state.signedIn) {
                Text(
                    text =
                        "Sign in with your khinsider account to:\n" +
                            "• keep your liked albums in sync with My Favourites\n" +
                            "• add albums you play to My History\n" +
                            "• import your khinsider playlists",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = { navController.navigate(KhinsiderSignInRoute) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sign in to khinsider")
                }
                Text(
                    text = "You'll sign in on khinsider's own page. Pixel Notes never sees or stores your password, only the session that keeps you signed in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                SyncSwitch(
                    title = "Favourites",
                    description = "Liked albums ⇄ khinsider My Favourites",
                    checked = state.syncFavorites,
                    onCheckedChange = viewModel::setSyncFavorites,
                )
                SyncSwitch(
                    title = "History",
                    description = "Albums you play are added to My History",
                    checked = state.syncHistory,
                    onCheckedChange = viewModel::setSyncHistory,
                )
                SyncSwitch(
                    title = "Playlists",
                    description = "Import My Playlists into your library",
                    checked = state.syncPlaylists,
                    onCheckedChange = viewModel::setSyncPlaylists,
                )
                HorizontalDivider()
                Button(
                    onClick = viewModel::syncNow,
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isSyncing) "Syncing…" else "Sync now")
                }
                OutlinedButton(
                    onClick = viewModel::signOut,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sign out")
                }
            }
        }
    }
}

@Composable
private fun SyncSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhinsiderSignInScreen(
    navController: NavController,
    viewModel: KhinsiderAccountViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val signInState by viewModel.signInState.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageLoading by remember { mutableStateOf(true) }

    LaunchedEffect(signInState) {
        val success = signInState as? KhinsiderSignInState.Success ?: return@LaunchedEffect
        Toast.makeText(context, "Signed in as ${success.username}", Toast.LENGTH_SHORT).show()
        navController.navigateUp()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
    ) {
        TopAppBar(
            title = { Text("Sign in to khinsider") },
            navigationIcon = {
                IconButton(onClick = navController::navigateUp) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
            actions = {
                if (signInState == KhinsiderSignInState.Checking) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 4.dp))
                    Spacer(Modifier.width(12.dp))
                }
            },
        )
        if (pageLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient =
                            object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: android.graphics.Bitmap?,
                                ) {
                                    pageLoading = true
                                }

                                override fun onPageFinished(
                                    view: WebView?,
                                    url: String?,
                                ) {
                                    pageLoading = false
                                    cookieManager.flush()
                                    viewModel.onCookies(cookieManager.getCookie(Khinsider.BASE_URL))
                                }
                            }
                        webView = this
                        loadUrl(KhinsiderAccount.SIGN_IN_URL)
                    }
                },
                onRelease = { released ->
                    released.stopLoading()
                    released.destroy()
                    if (webView === released) webView = null
                },
            )
        }
    }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}
