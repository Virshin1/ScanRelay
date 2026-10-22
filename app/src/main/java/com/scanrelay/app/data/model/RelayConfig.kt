package com.scanrelay.app.data.model

data class RelayConfig(
    val server: String = DEFAULT_SERVER,
    val topic: String = "",
    val token: String? = null
) {
    companion object {
        const val DEFAULT_SERVER = "https://ntfy.sh"
    }

    val isConfigured: Boolean
        get() = topic.isNotBlank() && server.isNotBlank()

    fun normalizedServer(): String {
        var s = server.trim()
        if (s.endsWith("/")) {
            s = s.substring(0, s.length - 1)
        }
        return s
    }
}
