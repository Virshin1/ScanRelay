package com.scanrelay.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scanrelay.app.ScanRelayApp
import com.scanrelay.app.data.model.AppMode
import com.scanrelay.app.data.model.RelayConfig
import com.scanrelay.app.service.ReceiverForegroundService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToOverlayGuide: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as ScanRelayApp
    val coroutineScope = rememberCoroutineScope()

    val currentMode by app.preferences.appModeFlow.collectAsState(initial = AppMode.UNSET)
    val relayConfig by app.preferences.relayConfigFlow.collectAsState(initial = RelayConfig())
    val autoOpen by app.preferences.autoOpenFlow.collectAsState(initial = false)
    val startOnBoot by app.preferences.startOnBootFlow.collectAsState(initial = false)

    var showModeDialog by remember { mutableStateOf(false) }
    var showEditRelayDialog by remember { mutableStateOf(false) }

    var isCheckingForUpdates by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<com.scanrelay.app.data.model.UpdateInfo?>(null) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Device Role
            Text(
                text = "Device Role",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showModeDialog = true },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (currentMode == AppMode.SCANNER) "Scanner Mode" else "Receiver Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (currentMode == AppMode.SCANNER) "This phone has the camera" else "This phone has the SIM",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Switch Role", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Section 2: Relay Server Settings
            Text(
                text = "Relay Server & Topic",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showEditRelayDialog = true },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Server: ${relayConfig.server}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Topic: ${if (relayConfig.topic.isNotEmpty()) relayConfig.topic else "Not set"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Token: ${if (!relayConfig.token.isNullOrBlank()) "•••••••• (Encrypted in Keystore)" else "None"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Section 3: Receiver Options (Visible only in Receiver mode)
            if (currentMode == AppMode.RECEIVER) {
                Text(
                    text = "Receiver Automation",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                // Auto-Open on Arrival Toggle
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Open on Arrival",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Instantly pops open your UPI app when a payment link arrives",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoOpen,
                                onCheckedChange = { coroutineScope.launch { app.preferences.setAutoOpen(it) } }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Guided Screen Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToOverlayGuide() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Permission & Battery Guide",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Start on Boot
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Start on Device Boot",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Automatically restart the receiver listener when phone reboots",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = startOnBoot,
                            onCheckedChange = { coroutineScope.launch { app.preferences.setStartOnBoot(it) } }
                        )
                    }
                }
            }

            // Section: App Updates
            Text(
                text = "App Updates",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val currentVer = remember {
                        try {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.3"
                        } catch (_: Exception) {
                            "1.0.3"
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ScanRelay v$currentVer",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Check GitHub for new app releases",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isCheckingForUpdates) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isCheckingForUpdates = true
                                    val result = app.updateManager.checkForUpdate(currentVer)
                                    isCheckingForUpdates = false
                                    result.onSuccess { update ->
                                        if (update != null) {
                                            availableUpdate = update
                                            showUpdateDialog = true
                                        } else {
                                            Toast.makeText(context, "ScanRelay is up to date (v$currentVer)", Toast.LENGTH_SHORT).show()
                                        }
                                    }.onFailure { err ->
                                        Toast.makeText(context, "Update check failed: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Check")
                        }
                    }
                }
            }

            // Section 4: About
            Text(
                text = "Information",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToAbout() },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "About ScanRelay",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Zero financial data guarantee & licenses",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }

        // Mode switch dialog
        if (showModeDialog) {
            AlertDialog(
                onDismissRequest = { showModeDialog = false },
                title = { Text("Switch Device Role") },
                text = { Text("Select which role this phone should perform:") },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    app.preferences.setAppMode(AppMode.SCANNER)
                                    ReceiverForegroundService.stop(context)
                                    showModeDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Scanner (This phone has the camera)")
                        }
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    app.preferences.setAppMode(AppMode.RECEIVER)
                                    showModeDialog = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Receiver (This phone has my SIM)")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showModeDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Edit relay config dialog
        if (showEditRelayDialog) {
            var tempServer by remember { mutableStateOf(relayConfig.server) }
            var tempTopic by remember { mutableStateOf(relayConfig.topic) }
            var tempToken by remember { mutableStateOf(relayConfig.token ?: "") }

            AlertDialog(
                onDismissRequest = { showEditRelayDialog = false },
                title = { Text("Configure Relay") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = tempServer,
                            onValueChange = { tempServer = it },
                            label = { Text("Server") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = tempTopic,
                            onValueChange = { tempTopic = it },
                            label = { Text("Topic") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = tempToken,
                            onValueChange = { tempToken = it },
                            label = { Text("Bearer Token (Optional)") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                app.preferences.setRelayConfig(
                                    RelayConfig(
                                        server = tempServer.trim(),
                                        topic = tempTopic.trim(),
                                        token = if (tempToken.isNotBlank()) tempToken.trim() else null
                                    )
                                )
                                showEditRelayDialog = false
                                Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditRelayDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // App Update Dialog
        if (showUpdateDialog && availableUpdate != null) {
            val update = availableUpdate!!
            var isDownloading by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = {
                    if (!isDownloading) showUpdateDialog = false
                },
                title = { Text("Update Available: v${update.versionName} 🚀") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "A new version of ScanRelay is available on GitHub.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Release Notes:\n${update.releaseNotes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (downloadProgress != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = downloadProgress!!,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Downloading: ${(downloadProgress!! * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !isDownloading,
                        onClick = {
                            isDownloading = true
                            downloadProgress = 0f
                            coroutineScope.launch {
                                val downloadResult = app.updateManager.downloadApk(update.downloadUrl) { progress ->
                                    downloadProgress = progress
                                }
                                isDownloading = false
                                downloadResult.onSuccess { apkFile ->
                                    val installResult = app.updateManager.installApk(apkFile)
                                    installResult.onFailure { err ->
                                        Toast.makeText(context, err.localizedMessage, Toast.LENGTH_LONG).show()
                                    }
                                    showUpdateDialog = false
                                }.onFailure { err ->
                                    Toast.makeText(context, "Download failed: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    ) {
                        Text(if (isDownloading) "Downloading..." else "Download & Install")
                    }
                },
                dismissButton = {
                    if (!isDownloading) {
                        TextButton(onClick = { showUpdateDialog = false }) {
                            Text("Later")
                        }
                    }
                }
            )
        }
    }
}
