package com.scanrelay.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.scanrelay.app.ScanRelayApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val app = context.applicationContext as? ScanRelayApp ?: return
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val startOnBoot = app.preferences.startOnBootFlow.first()
                    val serviceEnabled = app.preferences.serviceEnabledFlow.first()

                    if (startOnBoot && serviceEnabled) {
                        ReceiverForegroundService.start(context)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
