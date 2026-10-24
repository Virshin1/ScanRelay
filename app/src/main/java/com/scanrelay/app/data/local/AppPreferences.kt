package com.scanrelay.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scanrelay.app.data.model.AppMode
import com.scanrelay.app.data.model.RelayConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.security.SecureRandom

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scanrelay_preferences")

class AppPreferences(private val context: Context) {

    companion object {
        private val KEY_APP_MODE = stringPreferencesKey("app_mode")
        private val KEY_RELAY_SERVER = stringPreferencesKey("relay_server")
        private val KEY_RELAY_TOPIC = stringPreferencesKey("relay_topic")
        private val KEY_RELAY_TOKEN_ENCRYPTED = stringPreferencesKey("relay_token_encrypted")
        private val KEY_LAST_MESSAGE_ID = stringPreferencesKey("last_message_id")
        private val KEY_AUTO_OPEN = booleanPreferencesKey("auto_open_on_arrival")
        private val KEY_START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        private val KEY_SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        private val KEY_LAST_SCAN_TIMESTAMP = longPreferencesKey("last_scan_timestamp")
        private val KEY_USE_ULTRA_WIDE_CAMERA = booleanPreferencesKey("use_ultra_wide_camera")
        private val KEY_SCANNER_AUTO_OPEN = booleanPreferencesKey("scanner_auto_open")
        private val KEY_PREFERRED_PAYMENT_APP = stringPreferencesKey("preferred_payment_app")

        /**
         * Generates a cryptographically random, URL-safe topic string of at least 24 characters.
         */
        fun generateRandomTopic(): String {
            val random = SecureRandom()
            val bytes = ByteArray(18) // 18 bytes -> 24 Base64 chars
            random.nextBytes(bytes)
            val base64 = android.util.Base64.encodeToString(
                bytes,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            )
            return "scanrelay_$base64".replace("-", "").replace("_", "")
        }
    }

    private val dataStore = context.dataStore

    val appModeFlow: Flow<AppMode> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            when (preferences[KEY_APP_MODE]) {
                AppMode.SCANNER.name -> AppMode.SCANNER
                AppMode.RECEIVER.name -> AppMode.RECEIVER
                else -> AppMode.UNSET
            }
        }

    val relayConfigFlow: Flow<RelayConfig> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val server = preferences[KEY_RELAY_SERVER] ?: RelayConfig.DEFAULT_SERVER
            val topic = preferences[KEY_RELAY_TOPIC] ?: ""
            val encryptedToken = preferences[KEY_RELAY_TOKEN_ENCRYPTED]
            val token = KeystoreHelper.decrypt(encryptedToken)
            RelayConfig(server = server, topic = topic, token = token)
        }

    val autoOpenFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[KEY_AUTO_OPEN] ?: false }

    val startOnBootFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[KEY_START_ON_BOOT] ?: false }

    val serviceEnabledFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[KEY_SERVICE_ENABLED] ?: false }

    val lastScanTimestampFlow: Flow<Long> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[KEY_LAST_SCAN_TIMESTAMP] ?: 0L }

    val lastMessageIdFlow: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[KEY_LAST_MESSAGE_ID] }

    val useUltraWideCameraFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[KEY_USE_ULTRA_WIDE_CAMERA] ?: false }

    suspend fun setUseUltraWideCamera(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_USE_ULTRA_WIDE_CAMERA] = enabled
        }
    }

    val scannerAutoOpenFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[KEY_SCANNER_AUTO_OPEN] ?: true }

    suspend fun setScannerAutoOpen(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_SCANNER_AUTO_OPEN] = enabled
        }
    }

    val preferredPaymentAppFlow: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[KEY_PREFERRED_PAYMENT_APP] }

    suspend fun setPreferredPaymentApp(packageName: String?) {
        dataStore.edit { preferences ->
            if (packageName.isNullOrBlank()) {
                preferences.remove(KEY_PREFERRED_PAYMENT_APP)
            } else {
                preferences[KEY_PREFERRED_PAYMENT_APP] = packageName
            }
        }
    }

    suspend fun setAppMode(mode: AppMode) {
        dataStore.edit { preferences ->
            preferences[KEY_APP_MODE] = mode.name
        }
    }

    suspend fun setRelayConfig(config: RelayConfig) {
        dataStore.edit { preferences ->
            preferences[KEY_RELAY_SERVER] = config.normalizedServer()
            preferences[KEY_RELAY_TOPIC] = config.topic.trim()
            if (config.token.isNullOrBlank()) {
                preferences.remove(KEY_RELAY_TOKEN_ENCRYPTED)
            } else {
                val encrypted = KeystoreHelper.encrypt(config.token.trim())
                if (encrypted != null) {
                    preferences[KEY_RELAY_TOKEN_ENCRYPTED] = encrypted
                }
            }
        }
    }

    suspend fun setAutoOpen(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_AUTO_OPEN] = enabled
        }
    }

    suspend fun setStartOnBoot(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_START_ON_BOOT] = enabled
        }
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_SERVICE_ENABLED] = enabled
        }
    }

    suspend fun setLastScanTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[KEY_LAST_SCAN_TIMESTAMP] = timestamp
        }
    }

    suspend fun setLastMessageId(messageId: String) {
        dataStore.edit { preferences ->
            preferences[KEY_LAST_MESSAGE_ID] = messageId
        }
    }
}
