package com.scanrelay.app.data.model

data class NtfyMessage(
    val id: String? = null,
    val time: Long? = null,
    val event: String,
    val topic: String? = null,
    val message: String? = null,
    val title: String? = null,
    val click: String? = null
)
