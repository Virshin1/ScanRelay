package com.scanrelay.app.ui.receiver

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scanrelay.app.ScanRelayApp
import com.scanrelay.app.data.model.RelayConfig
import com.scanrelay.app.data.model.ScanHistoryItem
import com.scanrelay.app.data.remote.RelayConnectionState
import com.scanrelay.app.service.ReceiverForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReceiverUiState(
    val isServiceRunning: Boolean = false,
    val connectionState: RelayConnectionState = RelayConnectionState.Disconnected,
    val relayConfig: RelayConfig = RelayConfig(),
    val historyItems: List<ScanHistoryItem> = emptyList(),
    val lastScanTimestamp: Long = 0L
)

class ReceiverViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ScanRelayApp

    private val _uiState = MutableStateFlow(ReceiverUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ReceiverForegroundService.serviceRunning.collect { isRunning ->
                _uiState.value = _uiState.value.copy(isServiceRunning = isRunning)
            }
        }

        viewModelScope.launch {
            ReceiverForegroundService.connectionState.collect { connState ->
                _uiState.value = _uiState.value.copy(connectionState = connState)
            }
        }

        viewModelScope.launch {
            app.preferences.relayConfigFlow.collect { config ->
                _uiState.value = _uiState.value.copy(relayConfig = config)
            }
        }

        viewModelScope.launch {
            app.historyRepository.historyFlow.collect { history ->
                _uiState.value = _uiState.value.copy(historyItems = history)
            }
        }

        viewModelScope.launch {
            app.preferences.lastScanTimestampFlow.collect { ts ->
                _uiState.value = _uiState.value.copy(lastScanTimestamp = ts)
            }
        }
    }

    fun toggleService(enable: Boolean) {
        viewModelScope.launch {
            app.preferences.setServiceEnabled(enable)
            if (enable) {
                ReceiverForegroundService.start(app)
            } else {
                ReceiverForegroundService.stop(app)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            app.historyRepository.clearHistory()
        }
    }
}
