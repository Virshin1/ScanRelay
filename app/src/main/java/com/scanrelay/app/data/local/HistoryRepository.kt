package com.scanrelay.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scanrelay.app.data.model.ScanHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.historyDataStore by preferencesDataStore(name = "scanrelay_history")

class HistoryRepository(private val context: Context) {

    companion object {
        private const val MAX_HISTORY = 20
        private val KEY_HISTORY_JSON = stringPreferencesKey("history_items_json")
    }

    val historyFlow: Flow<List<ScanHistoryItem>> = context.historyDataStore.data.map { prefs ->
        val rawJson = prefs[KEY_HISTORY_JSON] ?: "[]"
        deserializeList(rawJson)
    }

    suspend fun addScan(item: ScanHistoryItem) {
        context.historyDataStore.edit { prefs ->
            val current = deserializeList(prefs[KEY_HISTORY_JSON] ?: "[]").toMutableList()
            current.add(0, item) // Prepend newest
            if (current.size > MAX_HISTORY) {
                current.subList(MAX_HISTORY, current.size).clear()
            }
            prefs[KEY_HISTORY_JSON] = serializeList(current)
        }
    }

    suspend fun clearHistory() {
        context.historyDataStore.edit { prefs ->
            prefs[KEY_HISTORY_JSON] = "[]"
        }
    }

    private fun serializeList(items: List<ScanHistoryItem>): String {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("timestamp", item.timestamp)
                put("payeeName", item.payeeName ?: JSONObject.NULL)
                put("payeeVpa", item.payeeVpa)
                put("amount", item.amount ?: JSONObject.NULL)
                put("status", item.status)
                put("rawUri", item.rawUri)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeList(jsonStr: String): List<ScanHistoryItem> {
        val list = mutableListOf<ScanHistoryItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ScanHistoryItem(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        payeeName = if (obj.isNull("payeeName")) null else obj.optString("payeeName"),
                        payeeVpa = obj.optString("payeeVpa"),
                        amount = if (obj.isNull("amount")) null else obj.optString("amount"),
                        status = obj.optString("status", "Received"),
                        rawUri = obj.optString("rawUri")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }
}
