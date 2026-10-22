package com.scanrelay.app.ui.pairing

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scanrelay.app.ScanRelayApp
import com.scanrelay.app.data.local.AppPreferences
import com.scanrelay.app.data.model.AppMode
import com.scanrelay.app.data.model.RelayConfig
import com.scanrelay.app.service.ReceiverForegroundService
import com.scanrelay.app.ui.scanner.CameraPreview
import com.scanrelay.app.ui.theme.SuccessGreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as ScanRelayApp
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val currentMode by app.preferences.appModeFlow.collectAsState(initial = AppMode.UNSET)
    val relayConfig by app.preferences.relayConfigFlow.collectAsState(initial = RelayConfig())

    var selectedScannerTab by remember { mutableIntStateOf(0) } // 0: Scan QR, 1: Manual
    var isReceiverPairConfirmed by remember { mutableStateOf(false) }

    // On Receiver: ensure topic is generated on first run
    LaunchedEffect(currentMode, relayConfig.topic) {
        if (currentMode == AppMode.RECEIVER && relayConfig.topic.isBlank()) {
            val randomTopic = AppPreferences.generateRandomTopic()
            app.preferences.setRelayConfig(
                relayConfig.copy(topic = randomTopic)
            )
        }
    }

    // On Receiver: actively listen for pairing confirmation handshake from Scanner phone
    LaunchedEffect(currentMode, relayConfig.topic) {
        if (currentMode == AppMode.RECEIVER && relayConfig.isConfigured) {
            launch {
                ReceiverForegroundService.pairingConfirmedEvent.collect {
                    isReceiverPairConfirmed = true
                    app.haptics.performSuccessHaptic()
                }
            }
            launch {
                try {
                    app.ntfyClient.subscribe(relayConfig).collect { message ->
                        if (message.message == "scanrelay://paired") {
                            isReceiverPairConfirmed = true
                            app.haptics.performSuccessHaptic()
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (currentMode == AppMode.RECEIVER) "Pair Receiver" else "Pair with Receiver") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (currentMode == AppMode.RECEIVER) {
                // Receiver Pair Screen
                if (isReceiverPairConfirmed) {
                    ReceiverPairSuccessContent(
                        onDone = onNavigateBack,
                        onShowQrAgain = { isReceiverPairConfirmed = false }
                    )
                } else {
                    ReceiverPairContent(
                        config = relayConfig,
                        onRegenerateTopic = {
                            coroutineScope.launch {
                                val newTopic = AppPreferences.generateRandomTopic()
                                app.preferences.setRelayConfig(relayConfig.copy(topic = newTopic))
                                Toast.makeText(context, "New topic generated", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCopyJson = { json ->
                            clipboardManager.setText(AnnotatedString(json))
                            Toast.makeText(context, "Pairing config copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            } else {
                // Scanner Pair Screen: Scan QR or Manual Entry
                Column(modifier = Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = selectedScannerTab) {
                        Tab(
                            selected = selectedScannerTab == 0,
                            onClick = { selectedScannerTab = 0 },
                            text = { Text("Scan QR") },
                            icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) }
                        )
                        Tab(
                            selected = selectedScannerTab == 1,
                            onClick = { selectedScannerTab = 1 },
                            text = { Text("Manual Entry") },
                            icon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                    }

                    if (selectedScannerTab == 0) {
                        // Scan QR from Receiver
                        Box(modifier = Modifier.fillMaxSize()) {
                            CameraPreview(
                                isTorchOn = false,
                                onBarcodeScanned = { raw ->
                                    try {
                                        val json = JSONObject(raw)
                                        val topic = json.optString("topic", "")
                                        val server = json.optString("server", RelayConfig.DEFAULT_SERVER)
                                        val token = if (json.has("token") && !json.isNull("token")) json.optString("token") else null

                                        if (topic.isNotBlank()) {
                                            val newConfig = RelayConfig(
                                                server = server,
                                                topic = topic,
                                                token = token
                                            )
                                            coroutineScope.launch {
                                                app.preferences.setRelayConfig(newConfig)
                                                // Send handshake ping so the Main Phone immediately gets confirmation!
                                                app.ntfyClient.sendPairHandshake(newConfig)
                                                app.haptics.performSuccessHaptic()
                                                Toast.makeText(context, "Paired! Handshake sent to main phone.", Toast.LENGTH_SHORT).show()
                                                onNavigateBack()
                                            }
                                        }
                                    } catch (_: Exception) {
                                        // Not a pairing QR
                                    }
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(24.dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Point camera at Main Phone's pairing QR code",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // Manual Entry Fallback
                        ScannerManualEntryContent(
                            initialConfig = relayConfig,
                            onSave = { updated ->
                                coroutineScope.launch {
                                    app.preferences.setRelayConfig(updated)
                                    // Send handshake ping to the receiver
                                    app.ntfyClient.sendPairHandshake(updated)
                                    app.haptics.performSuccessHaptic()
                                    Toast.makeText(context, "Settings saved and handshake sent to main phone!", Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiverPairSuccessContent(
    onDone: () -> Unit,
    onShowQrAgain: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn()
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(SuccessGreen.copy(alpha = 0.15f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Scanner Phone Connected! 🎉",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = SuccessGreen,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Phone A has successfully scanned the pairing QR code and established a secure connection.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Done, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Done (Open Receiver)")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onShowQrAgain,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Show Pairing QR Again")
        }
    }
}

@Composable
private fun ReceiverPairContent(
    config: RelayConfig,
    onRegenerateTopic: () -> Unit,
    onCopyJson: (String) -> Unit
) {
    val pairingJson = remember(config) {
        val obj = JSONObject()
        obj.put("server", config.server)
        obj.put("topic", config.topic)
        if (!config.token.isNullOrBlank()) {
            obj.put("token", config.token)
        }
        obj.toString()
    }

    val qrBitmap = remember(pairingJson) {
        QrCodeGenerator.generateQrBitmap(pairingJson, 512)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Scan with Phone A",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Open ScanRelay on Phone A and tap 'Pair with receiver' to scan this code. This screen will automatically confirm when scanned.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
        )

        // QR Code Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier.padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = "Pairing QR Code",
                        modifier = Modifier.size(260.dp)
                    )
                } else {
                    Text("Generating QR Code...", color = Color.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Topic details card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "SERVER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = config.server,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "TOPIC (RANDOM 24+ CHARACTERS)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = config.topic.ifEmpty { "Generating..." },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRegenerateTopic,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Topic")
            }

            Button(
                onClick = { onCopyJson(pairingJson) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Config")
            }
        }
    }
}

@Composable
private fun ScannerManualEntryContent(
    initialConfig: RelayConfig,
    onSave: (RelayConfig) -> Unit
) {
    var server by remember { mutableStateOf(initialConfig.server) }
    var topic by remember { mutableStateOf(initialConfig.topic) }
    var token by remember { mutableStateOf(initialConfig.token ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Manual Relay Configuration",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Enter the topic and server displayed on your receiver phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text("Relay Server") },
            placeholder = { Text("https://ntfy.sh") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = topic,
            onValueChange = { topic = it },
            label = { Text("Relay Topic") },
            placeholder = { Text("scanrelay_...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Access Token (Optional)") },
            placeholder = { Text("tk_...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                onSave(
                    RelayConfig(
                        server = server.trim(),
                        topic = topic.trim(),
                        token = if (token.isNotBlank()) token.trim() else null
                    )
                )
            },
            enabled = topic.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Pairing Settings")
        }
    }
}
