package com.scanrelay.app.ui.scanner

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.scanrelay.app.ui.theme.DangerRed
import com.scanrelay.app.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onNavigateToPairing: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ScannerViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ScannerUiEvent.ShowToast -> snackbarHostState.showSnackbar(event.message)
                is ScannerUiEvent.SendSuccess -> snackbarHostState.showSnackbar(event.message)
                is ScannerUiEvent.SendError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ScanRelay",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (uiState.relayConfig.isConfigured) "Relay Ready" else "Relay Unpaired",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.relayConfig.isConfigured) Color(0xFF81C784) else Color(0xFFFFB74D)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                actions = {
                    // Lens toggle: 0.5x Ultra-Wide vs 1x Standard
                    IconButton(onClick = { viewModel.onToggleUltraWide(!uiState.useUltraWide) }) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (uiState.useUltraWide) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f),
                                    shape = CircleShape
                                )
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.useUltraWide) "0.5x" else "1x",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.useUltraWide) MaterialTheme.colorScheme.onPrimary else Color.White
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.onTorchToggle(!uiState.isTorchOn) }) {
                        Icon(
                            imageVector = if (uiState.isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Toggle Torch",
                            tint = if (uiState.isTorchOn) Color(0xFFFFD54F) else Color.White
                        )
                    }
                    IconButton(onClick = onNavigateToPairing) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Pair with Receiver",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
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
            if (hasCameraPermission) {
                CameraPreview(
                    isTorchOn = uiState.isTorchOn,
                    useUltraWide = uiState.useUltraWide,
                    onBarcodeScanned = { raw ->
                        viewModel.onBarcodeDetected(raw)
                    }
                )

                // Bottom banner instruction
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp, start = 20.dp, end = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Retry queue banner if a payment failed and is queued
                    uiState.queuedPayment?.let { queued ->
                        if (!queued.isExpired) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = DangerRed)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Sending Failed (Queued)",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "₹${queued.data.amount ?: "0"} to ${queued.data.displayName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                    }
                                    Row {
                                        Button(
                                            onClick = { viewModel.retrySendingQueued() },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Retry")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Point camera at any UPI QR code",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Camera Permission Denied Screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Camera Permission Required",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ScanRelay requires camera access on Phone A to scan merchant UPI QR codes. Barcode recognition runs 100% offline on your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Open App Settings")
                    }
                }
            }

            // Loading indicator when sending
            if (uiState.isSending) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Relaying payment to main phone...")
                        }
                    }
                }
            }

            // Confirmation Dialog on QR detection
            uiState.pendingConfirmation?.let { paymentData ->
                ScanConfirmationDialog(
                    paymentData = paymentData,
                    warnings = uiState.riskWarnings,
                    isRelayConfigured = uiState.relayConfig.isConfigured,
                    onOpenDirectly = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paymentData.rawUri)).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                            viewModel.dismissConfirmation()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "No UPI app found on this device", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onConfirmSend = { viewModel.sendToMainPhone() },
                    onCancel = { viewModel.dismissConfirmation() }
                )
            }
        }
    }
}
