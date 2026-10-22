package com.scanrelay.app.data.model

data class ScanHistoryItem(
    val id: String,
    val timestamp: Long,
    val payeeName: String?,
    val payeeVpa: String,
    val amount: String?,
    val status: String,
    val rawUri: String
)
