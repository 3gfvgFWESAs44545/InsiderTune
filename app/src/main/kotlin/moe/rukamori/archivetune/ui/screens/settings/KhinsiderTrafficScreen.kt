/*
 * Pixel Notes — Developer options › khinsider traffic.
 * What the app downloaded from khinsider, and how often the phone answered instead.
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.khinsider.KhinsiderTraffic
import moe.rukamori.archivetune.innertube.khinsider.TrafficCategory
import moe.rukamori.archivetune.innertube.khinsider.TrafficTotals
import moe.rukamori.archivetune.utils.TrafficStatsDefaultOn
import moe.rukamori.archivetune.utils.TrafficStatsEnabledKey
import moe.rukamori.archivetune.utils.TrafficStatsStore
import moe.rukamori.archivetune.utils.rememberPreference
import java.text.DateFormat
import java.util.Date
import java.util.Locale

const val KhinsiderTrafficRoute = "settings/khinsider_traffic"

private fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhinsiderTrafficScreen(navController: NavController) {
    val context = LocalContext.current
    val snapshot by KhinsiderTraffic.snapshot.collectAsStateWithLifecycle()
    var enabled by rememberPreference(TrafficStatsEnabledKey, TrafficStatsDefaultOn)
    var period by rememberSaveable { mutableIntStateOf(0) }
    val totals =
        when (period) {
            0 -> snapshot.session
            1 -> snapshot.today
            else -> snapshot.sinceReset
        }
    val timeFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
    ) {
        TopAppBar(
            title = { Text("khinsider traffic") },
            navigationIcon = {
                IconButton(onClick = navController::navigateUp) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "switch") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Record traffic", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Counts what the app downloads. Stays on this phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }

            item(key = "period") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    listOf("This session", "Today", "Since reset").forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = period == index,
                            onClick = { period = index },
                            shape = SegmentedButtonDefaults.itemShape(index, 3),
                        ) { Text(label) }
                    }
                }
                if (period == 2) {
                    Text(
                        "Since " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(snapshot.resetAtMillis)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }

            item(key = "summary") { SummaryCard(totals) }
            item(key = "table") { CategoryTable(totals) }

            item(key = "reset") {
                OutlinedButton(
                    onClick = {
                        KhinsiderTraffic.reset()
                        TrafficStatsStore.saveNow(context)
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) { Text("Reset all counts") }
            }

            item(key = "log_title") {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text("Recent requests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Last ${snapshot.log.size} this session, newest first",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(snapshot.log) { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                ) {
                    Text(
                        timeFormat.format(Date(entry.timeMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.category.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            entry.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        if (entry.fromPhone) "phone" else formatBytes(entry.bytes),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (entry.fromPhone) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item(key = "footer") {
                Text(
                    "Audio is shown separately so you can see browsing on its own. The favourite-sync web view " +
                        "can only be counted, not measured. Pages kept in memory for a few minutes count as \"from phone\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(totals: TrafficTotals) {
    val browsing = TrafficCategory.entries.filter { it != TrafficCategory.AUDIO }
    val bytes = browsing.sumOf { totals[it].bytes }
    val requests = browsing.sumOf { totals[it].requests }
    val fromPhone = browsing.sumOf { totals[it].fromPhone }
    val share = if (requests + fromPhone > 0) (fromPhone * 100 / (requests + fromPhone)) else 0
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Browsing (everything except audio)", style = MaterialTheme.typography.labelLarge)
            Text(formatBytes(bytes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("$requests requests to khinsider · $fromPhone answered by the phone ($share%)", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Audio: " + formatBytes(totals[TrafficCategory.AUDIO].bytes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryTable(totals: TrafficTotals) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row {
            Text("", modifier = Modifier.weight(1.6f))
            listOf("Requests", "Data", "Phone").forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        TrafficCategory.entries.forEach { category ->
            val counter = totals[category]
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(category.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.6f))
                Text(
                    if (category == TrafficCategory.AUDIO) "—" else counter.requests.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (category == TrafficCategory.WEB_VIEW) "—" else formatBytes(counter.bytes),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(counter.fromPhone.toString(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}
