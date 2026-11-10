package org.kcmcelia.bookofmormon.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kcmcelia.bookofmormon.BuildConfig
import org.kcmcelia.bookofmormon.MainViewModel
import org.kcmcelia.bookofmormon.R
import org.kcmcelia.bookofmormon.util.formatBytes
import org.kcmcelia.bookofmormon.util.isIgnoringBatteryOptimizations
import org.kcmcelia.bookofmormon.util.manufacturerGuideRes
import org.kcmcelia.bookofmormon.util.openAppSettings
import org.kcmcelia.bookofmormon.util.requestIgnoreBatteryOptimizations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    newVersion: String?,
    onBack: () -> Unit,
    onOpenBattery: () -> Unit,
) {
    val downloaded by viewModel.downloadedCount.collectAsStateWithLifecycle()
    val summary by viewModel.downloadSummary.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val total = viewModel.manifest.counts.items

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // ------------------------------------------------------------ downloads
            Text(
                text = stringResource(R.string.settings_downloads),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = stringResource(R.string.download_progress, downloaded, total),
                style = MaterialTheme.typography.bodyLarge,
            )

            if (summary.active > 0) {
                LinearProgressIndicator(
                    progress = { summary.fraction },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                )
                Text(
                    text = stringResource(R.string.download_active, (summary.fraction * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = stringResource(R.string.settings_storage, formatBytes(viewModel.content.downloadedBytes())),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextButton(onClick = viewModel::downloadAll) {
                Text(stringResource(R.string.download_all))
            }
            Text(
                text = stringResource(R.string.download_wifi_only) + "  ·  " +
                    stringResource(R.string.download_need_space, formatBytes(viewModel.estimatedDownloadSize())),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ------------------------------------------------------------ battery
            SettingsRow(
                title = stringResource(R.string.settings_battery),
                subtitle = if (isIgnoringBatteryOptimizations(context)) {
                    stringResource(R.string.battery_allowed)
                } else {
                    stringResource(R.string.battery_explain)
                },
                onClick = onOpenBattery,
            )

            // ------------------------------------------------------------ update
            if (newVersion != null) {
                SettingsRow(
                    title = stringResource(R.string.new_version) + "  $newVersion",
                    subtitle = stringResource(R.string.new_version_action),
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.RELEASES_URL)))
                        }
                    },
                )
            }

            // ------------------------------------------------------------ about
            Text(
                text = stringResource(R.string.settings_about),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.about_author),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.about_dedication),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.about_rights),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val guide = stringResource(manufacturerGuideRes())
    val ignoring = isIgnoringBatteryOptimizations(context)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.battery_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.battery_explain),
                style = MaterialTheme.typography.bodyLarge,
            )

            if (ignoring) {
                Text(
                    text = stringResource(R.string.battery_allowed),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                TextButton(onClick = { requestIgnoreBatteryOptimizations(context) }) {
                    Text(stringResource(R.string.battery_allow))
                }
            }

            Text(
                text = stringResource(R.string.battery_guide_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = guide, style = MaterialTheme.typography.bodyLarge)

            TextButton(onClick = { openAppSettings(context) }) {
                Text(stringResource(R.string.settings))
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}
