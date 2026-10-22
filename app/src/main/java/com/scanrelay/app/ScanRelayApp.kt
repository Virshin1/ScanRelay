package com.scanrelay.app

import android.app.Application
import com.scanrelay.app.data.local.AppPreferences
import com.scanrelay.app.data.local.HistoryRepository
import com.scanrelay.app.data.remote.NtfyClient
import com.scanrelay.app.util.HapticFeedbackHelper
import com.scanrelay.app.util.NotificationHelper

class ScanRelayApp : Application() {

    lateinit var preferences: AppPreferences
        private set

    lateinit var historyRepository: HistoryRepository
        private set

    lateinit var ntfyClient: NtfyClient
        private set

    lateinit var haptics: HapticFeedbackHelper
        private set

    lateinit var updateManager: com.scanrelay.app.util.UpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        historyRepository = HistoryRepository(this)
        ntfyClient = NtfyClient()
        haptics = HapticFeedbackHelper(this)
        updateManager = com.scanrelay.app.util.UpdateManager(this)

        NotificationHelper.createNotificationChannels(this)
    }
}
