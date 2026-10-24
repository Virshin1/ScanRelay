package com.scanrelay.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object UpiAppLauncher {

    /**
     * Attempts to open the scanner screen / main activity of the specified UPI package.
     * - For super.money (money.super.payments), the default launch activity is its scan-first camera viewfinder.
     * - For Paytm (net.one97.paytm), attempts deep-link scanandpay before falling back to main launcher.
     * - For PhonePe, Google Pay, etc., launches the main activity where the scanner is immediately accessible.
     */
    fun openAppScanner(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false

        // 1. Paytm specific scanner action
        if (packageName == "net.one97.paytm") {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("paytmmp://cash_wallet?featuretype=scanandpay")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    setPackage(packageName)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }

        // 2. Launch main activity (super.money opens straight to its built-in QR camera view)
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
