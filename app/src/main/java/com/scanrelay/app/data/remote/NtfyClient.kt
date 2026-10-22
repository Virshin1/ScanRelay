package com.scanrelay.app.data.remote

import com.scanrelay.app.data.model.NtfyMessage
import com.scanrelay.app.data.model.RelayConfig
import com.scanrelay.app.data.model.UpiPaymentData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.math.min

sealed class RelayConnectionState {
    object Disconnected : RelayConnectionState()
    object Connecting : RelayConnectionState()
    object Connected : RelayConnectionState()
    data class Error(val message: String) : RelayConnectionState()
}

class NtfyClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // Infinite read timeout for long streaming
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS) // HTTP/2 ping every 30s to prevent silent drops
        .retryOnConnectionFailure(true)
        .build()
) {

    private val postClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Publishes a raw UPI payment link to the ntfy relay server.
     * Sender: HTTP POST of the raw link as the body to https://<server>/<topic>,
     * with a "Title" header and a "Click" header containing the link.
     */
    suspend fun publishUpiLink(
        config: RelayConfig,
        paymentData: UpiPaymentData
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Relay server or topic not configured"))
        }

        try {
            val url = "${config.normalizedServer()}/${config.topic}"
            val body = paymentData.rawUri.toRequestBody("text/plain; charset=utf-8".toMediaType())

            val title = if (paymentData.amount != null) {
                "UPI Payment: ₹${paymentData.amount} to ${paymentData.displayName}"
            } else {
                "UPI Payment: ${paymentData.displayName}"
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Title", title)
                .addHeader("Click", paymentData.rawUri)

            if (!config.token.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${config.token}")
            }

            val response = postClient.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Relay server error: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends an instant pairing handshake message to the relay topic.
     * When Phone A (Scanner) scans the QR of Phone B (Receiver), this confirms
     * to Phone B that the pair was successful.
     */
    suspend fun sendPairHandshake(
        config: RelayConfig
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!config.isConfigured) {
            return@withContext Result.failure(IllegalStateException("Relay server or topic not configured"))
        }

        try {
            val url = "${config.normalizedServer()}/${config.topic}"
            val body = "scanrelay://paired".toRequestBody("text/plain; charset=utf-8".toMediaType())

            val requestBuilder = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Title", "ScanRelay: Scanner Paired")
                .addHeader("Tags", "white_check_mark,tada")

            if (!config.token.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${config.token}")
            }

            val response = postClient.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Relay server error: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Subscribes to https://<server>/<topic>/json (streaming), parses each line,
     * ignores "open" and "keepalive" events, and emits "message" events.
     * Implements exponential backoff (capping at 60s) and resumes with the "since" parameter.
     */
    fun subscribe(
        config: RelayConfig,
        initialSinceId: String? = null,
        onStateChanged: (RelayConnectionState) -> Unit = {}
    ): Flow<NtfyMessage> = flow {
        var backoffMs = 2000L
        val maxBackoffMs = 60000L
        var lastReceivedId = initialSinceId

        while (true) {
            onStateChanged(RelayConnectionState.Connecting)

            var reader: BufferedReader? = null
            var responseCall: okhttp3.Call? = null

            try {
                var url = "${config.normalizedServer()}/${config.topic}/json"
                if (!lastReceivedId.isNullOrBlank()) {
                    url += "?since=$lastReceivedId"
                }

                val requestBuilder = Request.Builder().url(url)
                if (!config.token.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer ${config.token}")
                }

                val call = okHttpClient.newCall(requestBuilder.build())
                responseCall = call
                val response = call.execute()

                if (!response.isSuccessful) {
                    val code = response.code
                    onStateChanged(RelayConnectionState.Error("HTTP error $code"))
                    response.close()
                    delay(backoffMs)
                    backoffMs = min(backoffMs * 2, maxBackoffMs)
                    continue
                }

                val inputStream = response.body?.byteStream()
                if (inputStream == null) {
                    response.close()
                    onStateChanged(RelayConnectionState.Error("Empty stream"))
                    delay(backoffMs)
                    backoffMs = min(backoffMs * 2, maxBackoffMs)
                    continue
                }

                // Connected successfully
                onStateChanged(RelayConnectionState.Connected)
                backoffMs = 2000L // Reset backoff on successful connection

                reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line?.trim()
                    if (currentLine.isNullOrEmpty()) continue

                    val message = parseNtfyJsonLine(currentLine)
                    if (message != null) {
                        // Only track message IDs for actual messages to avoid restarting stream on open event IDs
                        if (message.event == "message" && !message.id.isNullOrBlank()) {
                            lastReceivedId = message.id
                        }

                        // Ignore "open" and "keepalive" events, handle only "message"
                        if (message.event == "message" && !message.message.isNullOrBlank()) {
                            emit(message)
                        }
                    }
                }
            } catch (ce: CancellationException) {
                onStateChanged(RelayConnectionState.Disconnected)
                throw ce
            } catch (e: Exception) {
                onStateChanged(RelayConnectionState.Error(e.localizedMessage ?: "Connection error"))
            } finally {
                try {
                    reader?.close()
                } catch (_: Exception) {}
                try {
                    responseCall?.cancel()
                } catch (_: Exception) {}
            }

            // Exponential backoff before reconnect
            onStateChanged(RelayConnectionState.Disconnected)
            delay(backoffMs)
            backoffMs = min(backoffMs * 2, maxBackoffMs)
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        fun parseNtfyJsonLine(line: String): NtfyMessage? {
            return try {
                val json = JSONObject(line)
                val event = json.optString("event", "")
                val id = if (json.has("id")) json.getString("id") else null
                val time = if (json.has("time")) json.getLong("time") else null
                val topic = if (json.has("topic")) json.getString("topic") else null
                val message = if (json.has("message")) json.getString("message") else null
                val title = if (json.has("title")) json.getString("title") else null
                val click = if (json.has("click")) json.getString("click") else null

                NtfyMessage(
                    id = id,
                    time = time,
                    event = event,
                    topic = topic,
                    message = message,
                    title = title,
                    click = click
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
