/*
 * Pixel Notes — Settings › Updates, and the "update available" prompt shown when the app opens
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.utils.PixelNotesUpdater
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.rememberPreference
import kotlinx.coroutines.flow.first

const val PixelNotesUpdateRoute = "settings/pixel_updates"
val PixelNotesAutoUpdateCheckKey = booleanPreferencesKey("pixelNotesAutoUpdateCheck")

/** Off for normal use: dev builds (GitHub pre-releases) are skipped unless this is on. */
val PixelNotesDevUpdatesKey = booleanPreferencesKey("pixelNotesDevUpdates")

/** Shared check → download → install flow used by the Updates page and the launch prompt. */
@Stable
class PixelNotesUpdateFlow(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    var checking by mutableStateOf(false)
        private set
    var result by mutableStateOf<PixelNotesUpdater.CheckResult?>(null)
        private set
    var downloadProgress by mutableStateOf<Float?>(null)
        private set
    var downloading by mutableStateOf(false)
        private set

    fun check(
        includeDev: Boolean,
        onDone: (PixelNotesUpdater.CheckResult) -> Unit = {},
    ) {
        if (checking) return
        checking = true
        scope.launch {
            val r = PixelNotesUpdater.check(includeDev)
            result = r
            checking = false
            onDone(r)
        }
    }

    fun downloadAndInstall(release: PixelNotesUpdater.Release) {
        if (downloading) return
        if (!PixelNotesUpdater.canInstall(context)) {
            Toast
                .makeText(
                    context,
                    "Allow Pixel Notes to install apps, then come back and tap Install again",
                    Toast.LENGTH_LONG,
                ).show()
            PixelNotesUpdater.openInstallPermissionSettings(context)
            return
        }
        downloading = true
        downloadProgress = 0f
        scope.launch {
            try {
                val apk = PixelNotesUpdater.download(context, release) { downloadProgress = it }
                PixelNotesUpdater.install(context, apk)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(context, "Update failed: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
            } finally {
                downloading = false
                downloadProgress = null
            }
        }
    }
}

@Composable
fun rememberPixelNotesUpdateFlow(): PixelNotesUpdateFlow {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) { PixelNotesUpdateFlow(context.applicationContext, scope) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelNotesUpdateScreen(navController: NavController) {
    val flow = rememberPixelNotesUpdateFlow()
    var autoCheck by rememberPreference(PixelNotesAutoUpdateCheckKey, true)
    val context = LocalContext.current
    var includeDev by rememberPreference(PixelNotesDevUpdatesKey, false)

    // Read the saved setting directly so the first check doesn't use the default before it loads.
    LaunchedEffect(Unit) {
        flow.check(context.dataStore.data.first()[PixelNotesDevUpdatesKey] ?: false)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
    ) {
        TopAppBar(
            title = { Text("Updates") },
            navigationIcon = {
                IconButton(onClick = navController::navigateUp) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
        )
        if (flow.checking) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

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
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(R.drawable.vg_logo_color),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Pixel Notes ${PixelNotesUpdater.displayVersion(PixelNotesUpdater.currentVersion)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text =
                            when (val r = flow.result) {
                                null -> if (flow.checking) "Checking for updates…" else "Tap Check for updates"
                                is PixelNotesUpdater.CheckResult.UpToDate -> "You're on the latest version."
                                is PixelNotesUpdater.CheckResult.UpdateAvailable ->
                                    "Version ${PixelNotesUpdater.displayVersion(r.release.version)} is available."
                                is PixelNotesUpdater.CheckResult.Failed -> "Couldn't check for updates: ${r.message}"
                            },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            val available = flow.result as? PixelNotesUpdater.CheckResult.UpdateAvailable
            if (available != null) {
                Button(
                    onClick = { flow.downloadAndInstall(available.release) },
                    enabled = !flow.downloading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (flow.downloading) {
                            "Downloading…"
                        } else {
                            "Download and install ${PixelNotesUpdater.displayVersion(available.release.version)}"
                        },
                    )
                }
                flow.downloadProgress?.let { progress ->
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
            }

            OutlinedButton(
                onClick = { flow.check(includeDev) },
                enabled = !flow.checking && !flow.downloading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Check for updates")
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Check when the app opens", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Offers to install new builds as soon as they're published",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = autoCheck, onCheckedChange = { autoCheck = it })
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Include dev builds", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Test versions published before a normal release. May have rough edges.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = includeDev,
                    onCheckedChange = {
                        includeDev = it
                        flow.check(it)
                    },
                )
            }

            Text(
                text =
                    "Updates come from the Pixel Notes releases on GitHub. Android will ask you to confirm " +
                        "each install; your library and settings are kept.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Shown once per app launch when a newer build exists (if "Check when the app opens" is on). */
@Composable
fun PixelNotesUpdatePrompt() {
    val flow = rememberPixelNotesUpdateFlow()
    val context = LocalContext.current
    var checkedThisLaunch by rememberSaveable { mutableStateOf(false) }
    var dismissed by rememberSaveable { mutableStateOf(false) }

    // Settings are read straight from storage so the launch check uses the saved choices.
    LaunchedEffect(Unit) {
        if (checkedThisLaunch) return@LaunchedEffect
        val prefs = context.dataStore.data.first()
        if (prefs[PixelNotesAutoUpdateCheckKey] ?: true) {
            checkedThisLaunch = true
            flow.check(prefs[PixelNotesDevUpdatesKey] ?: false)
        }
    }

    val available = flow.result as? PixelNotesUpdater.CheckResult.UpdateAvailable ?: return
    if (dismissed && !flow.downloading) return

    AlertDialog(
        onDismissRequest = { if (!flow.downloading) dismissed = true },
        icon = {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.vg_logo_color),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        },
        title = { Text("Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Pixel Notes ${PixelNotesUpdater.displayVersion(available.release.version)} is out. " +
                        "You have ${PixelNotesUpdater.displayVersion(PixelNotesUpdater.currentVersion)}.",
                )
                flow.downloadProgress?.let { progress ->
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { flow.downloadAndInstall(available.release) },
                enabled = !flow.downloading,
            ) {
                Text(if (flow.downloading) "Downloading…" else "Install")
            }
        },
        dismissButton = {
            TextButton(onClick = { dismissed = true }, enabled = !flow.downloading) {
                Text("Later")
            }
        },
    )
}
