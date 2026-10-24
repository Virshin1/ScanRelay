package com.scanrelay.app.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import com.scanrelay.app.ScanRelayApp
import com.scanrelay.app.data.model.ScanHistoryItem
import com.scanrelay.app.data.remote.RelayConnectionState
import com.scanrelay.app.util.NotificationHelper
import com.scanrelay.app.util.UpiUriParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class ReceiverForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.scanrelay.app.action.START_SERVICE"
        const val ACTION_STOP = "com.scanrelay.app.action.STOP_SERVICE"

        private val _serviceRunning = MutableStateFlow(false)
        val serviceRunning = _serviceRunning.asStateFlow()

        private val _connectionState = MutableStateFlow<RelayConnectionState>(RelayConnectionState.Disconnected)
        val connectionState = _connectionState.asStateFlow()

        private val _pairingConfirmedEvent = kotlinx.coroutines.flow.MutableSharedFlow<Long>(extraBufferCapacity = 1)
        val pairingConfirmedEvent = _pairingConfirmedEvent.asSharedFlow()

        fun start(context: Context) {
            val intent = Intent(context, ReceiverForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ReceiverForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var streamJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _serviceRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopListening()
            return START_NOT_STICKY
        }

        startListeningAsForeground()
        return START_STICKY
    }

    private fun startListeningAsForeground() {
        val notification = NotificationHelper.buildServiceNotification(this, "Connecting to relay...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notification)
        }

        val app = application as ScanRelayApp

        streamJob?.cancel()
        streamJob = serviceScope.launch {
            val config = app.preferences.relayConfigFlow.first()
            if (!config.isConfigured) {
                updateNotificationStatus("Topic not configured. Open app to pair.")
                _connectionState.value = RelayConnectionState.Error("Topic not configured")
                return@launch
            }

            val lastMsgId = app.preferences.lastMessageIdFlow.first()

            app.ntfyClient.subscribe(
                config = config,
                initialSinceId = lastMsgId,
                onStateChanged = { state ->
                    _connectionState.value = state
                    when (state) {
                        is RelayConnectionState.Connected -> updateNotificationStatus("Listening for scanned payments...")
                        is RelayConnectionState.Connecting -> updateNotificationStatus("Connecting to relay server...")
                        is RelayConnectionState.Disconnected -> updateNotificationStatus("Disconnected. Reconnecting...")
                        is RelayConnectionState.Error -> updateNotificationStatus("Connection error. Retrying...")
                    }
                }
            ).collect { ntfyMessage ->
                handleIncomingMessage(ntfyMessage.message, ntfyMessage.time, ntfyMessage.id)
            }
        }
    }

    private suspend fun handleIncomingMessage(rawPayload: String?, messageTimestampSeconds: Long?, messageId: String?) {
        if (rawPayload.isNullOrBlank()) return

        val app = application as ScanRelayApp

        if (!messageId.isNullOrBlank()) {
            app.preferences.setLastMessageId(messageId)
        }

        // Check for pairing handshake
        if (rawPayload == "scanrelay://paired") {
            app.haptics.performSuccessHaptic()
            NotificationHelper.showPairSuccessNotification(this)
            _pairingConfirmedEvent.tryEmit(System.currentTimeMillis())
            return
        }

        // 1. Stale message verification: Ignore messages older than 2 minutes (120 seconds)
        val nowSeconds = System.currentTimeMillis() / 1000
        if (messageTimestampSeconds != null && messageTimestampSeconds > 0) {
            val ageSeconds = nowSeconds - messageTimestampSeconds
            if (ageSeconds > 120) {
                // Ignore stale message
                return
            }
        }

        // 2. Strict allowlist validation: NEVER trust the relay server
        val parseResult = UpiUriParser.parse(rawPayload)
        if (parseResult !is UpiUriParser.ParseResult.Success) {
            // Discard unverified or malformed payloads
            return
        }

        val paymentData = parseResult.data

        // Record to local scan history
        val historyItem = ScanHistoryItem(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            payeeName = paymentData.payeeName,
            payeeVpa = paymentData.payeeVpa,
            amount = paymentData.amount,
            status = "Received",
            rawUri = paymentData.rawUri
        )
        app.historyRepository.addScan(historyItem)
        app.preferences.setLastScanTimestamp(System.currentTimeMillis())

        // 3. Auto-open logic
        val autoOpenEnabled = app.preferences.autoOpenFlow.first()
        val preferredPkg = app.preferences.preferredPaymentAppFlow.first()
        val canDrawOverlay = Settings.canDrawOverlays(this)

        if (autoOpenEnabled && canDrawOverlay) {
            try {
                val launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(paymentData.rawUri)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (!preferredPkg.isNullOrBlank()) {
                        setPackage(preferredPkg)
                    }
                }
                startActivity(launchIntent)
            } catch (e: Exception) {
                // If direct startActivity fails, fallback to high-priority notification
                NotificationHelper.showPaymentNotification(this, paymentData)
            }
        } else {
            // Notification mode (default)
            NotificationHelper.showPaymentNotification(this, paymentData)
        }
    }

    private var lastStatusText: String? = null
    private fun updateNotificationStatus(text: String) {
        if (text == lastStatusText) return
        lastStatusText = text
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val updated = NotificationHelper.buildServiceNotification(this, text)
        manager.notify(NotificationHelper.SERVICE_NOTIFICATION_ID, updated)
    }

    private fun stopListening() {
        streamJob?.cancel()
        serviceScope.cancel()
        _serviceRunning.value = false
        _connectionState.value = RelayConnectionState.Disconnected
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }
}
