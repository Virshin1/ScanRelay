package com.scanrelay.app.ui.scanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scanrelay.app.ScanRelayApp
import com.scanrelay.app.data.model.RelayConfig
import com.scanrelay.app.data.model.UpiPaymentData
import com.scanrelay.app.util.RiskWarning
import com.scanrelay.app.util.UpiRiskAnalyzer
import com.scanrelay.app.util.UpiUriParser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class QueuedPayment(
    val data: UpiPaymentData,
    val queuedTimestamp: Long = System.currentTimeMillis()
) {
    val isExpired: Boolean
        get() = (System.currentTimeMillis() - queuedTimestamp) > 120_000 // 2 minutes
}

data class ScannerUiState(
    val isTorchOn: Boolean = false,
    val isSending: Boolean = false,
    val pendingConfirmation: UpiPaymentData? = null,
    val riskWarnings: List<RiskWarning> = emptyList(),
    val queuedPayment: QueuedPayment? = null,
    val relayConfig: RelayConfig = RelayConfig()
)

sealed class ScannerUiEvent {
    data class ShowToast(val message: String) : ScannerUiEvent()
    data class SendSuccess(val message: String) : ScannerUiEvent()
    data class SendError(val message: String) : ScannerUiEvent()
}

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ScanRelayApp

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ScannerUiEvent>()
    val events = _events.asSharedFlow()

    private var lastScannedRawUri: String? = null
    private var lastScannedTimestamp: Long = 0L

    init {
        viewModelScope.launch {
            app.preferences.relayConfigFlow.collect { config ->
                _uiState.value = _uiState.value.copy(relayConfig = config)
            }
        }
    }

    fun onTorchToggle(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isTorchOn = enabled)
    }

    /**
     * Handles detected barcode string with 5-second debounce and strict validation.
     */
    fun onBarcodeDetected(raw: String) {
        // Debounce: ignore duplicate scans within 5 seconds
        val now = System.currentTimeMillis()
        if (raw == lastScannedRawUri && (now - lastScannedTimestamp) < 5000L) {
            return
        }

        lastScannedRawUri = raw
        lastScannedTimestamp = now

        // Parse UPI QR (supports standard upi://pay, BharatQR 000201, and web UPI links)
        val parseResult = UpiUriParser.parse(raw)
        if (parseResult !is UpiUriParser.ParseResult.Success) {
            app.haptics.performErrorHaptic()
            val snippet = if (raw.length > 25) raw.take(25) + "..." else raw
            viewModelScope.launch {
                _events.emit(ScannerUiEvent.ShowToast("Unsupported QR code: \"$snippet\""))
            }
            return
        }

        // Valid UPI QR detected!
        app.haptics.performScanHaptic()
        val data = parseResult.data
        val warnings = UpiRiskAnalyzer.analyze(data)

        _uiState.value = _uiState.value.copy(
            pendingConfirmation = data,
            riskWarnings = warnings
        )
    }

    fun dismissConfirmation() {
        _uiState.value = _uiState.value.copy(
            pendingConfirmation = null,
            riskWarnings = emptyList()
        )
    }

    fun sendToMainPhone() {
        val payment = _uiState.value.pendingConfirmation ?: return
        dismissConfirmation()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)

            val config = app.preferences.relayConfigFlow.first()
            if (!config.isConfigured) {
                _uiState.value = _uiState.value.copy(isSending = false)
                app.haptics.performErrorHaptic()
                _events.emit(ScannerUiEvent.SendError("Relay not configured. Tap Pair to configure topic."))
                return@launch
            }

            val result = app.ntfyClient.publishUpiLink(config, payment)
            _uiState.value = _uiState.value.copy(isSending = false)

            result.onSuccess {
                app.haptics.performSuccessHaptic()
                _uiState.value = _uiState.value.copy(queuedPayment = null)
                _events.emit(ScannerUiEvent.SendSuccess("Payment link sent to receiver phone!"))
            }.onFailure { error ->
                app.haptics.performErrorHaptic()
                // Queue message for retry if offline (max age 2 minutes)
                val queued = QueuedPayment(payment)
                _uiState.value = _uiState.value.copy(queuedPayment = queued)
                _events.emit(ScannerUiEvent.SendError("Failed to send: ${error.localizedMessage ?: "Network error"}. Tap Retry to resend."))
            }
        }
    }

    fun retrySendingQueued() {
        val queued = _uiState.value.queuedPayment ?: return
        if (queued.isExpired) {
            _uiState.value = _uiState.value.copy(queuedPayment = null)
            viewModelScope.launch {
                _events.emit(ScannerUiEvent.ShowToast("Payment request expired (> 2 minutes). Please rescan."))
            }
            return
        }

        _uiState.value = _uiState.value.copy(pendingConfirmation = queued.data)
        sendToMainPhone()
    }

    fun cancelQueued() {
        _uiState.value = _uiState.value.copy(queuedPayment = null)
    }
}
