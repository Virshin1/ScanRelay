package com.scanrelay.app.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.scanrelay.app.MainActivity
import com.scanrelay.app.R
import com.scanrelay.app.data.model.UpiPaymentData

object NotificationHelper {

    const val SERVICE_CHANNEL_ID = "scanrelay_service_channel"
    const val PAYMENT_CHANNEL_ID = "scanrelay_payment_channel"

    const val SERVICE_NOTIFICATION_ID = 1001

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Low-priority persistent channel for foreground listening service
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                context.getString(R.string.channel_service_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_service_description)
                setShowBadge(false)
            }

            // High-priority alert channel for incoming payment requests
            val paymentChannel = NotificationChannel(
                PAYMENT_CHANNEL_ID,
                context.getString(R.string.channel_payment_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_payment_description)
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(paymentChannel)
        }
    }

    /**
     * Builds the persistent notification for the listening foreground service.
     */
    fun buildServiceNotification(context: Context, statusText: String = "Listening for scanned payments..."): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setContentTitle("ScanRelay Receiver Active")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Posts a high-priority notification for an incoming scanned payment request.
     * Tapping it fires an ACTION_VIEW intent with the upi:// URI.
     */
    fun showPaymentNotification(context: Context, paymentData: UpiPaymentData, notificationId: Int = System.currentTimeMillis().toInt()) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to launch the UPI chooser / default UPI app
        val upiIntent = Intent(Intent.ACTION_VIEW, Uri.parse(paymentData.rawUri)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            upiIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (!paymentData.payeeName.isNullOrBlank()) {
            "Pay ${paymentData.payeeName}"
        } else {
            "UPI Payment Request"
        }

        val text = if (!paymentData.amount.isNullOrBlank()) {
            "₹${paymentData.amount} to ${paymentData.payeeVpa}"
        } else {
            "Payment to ${paymentData.payeeVpa} (Amount not set)"
        }

        val builder = NotificationCompat.Builder(context, PAYMENT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL) // High priority heads-up
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$text\n\nTap to open your UPI app and pay.")
            )

        // Check full screen intent permission (Android 14+)
        if (canUseFullScreenIntent(context, manager)) {
            builder.setFullScreenIntent(pendingIntent, true)
        }

        manager.notify(notificationId, builder.build())
    }

    /**
     * Posts an alert notification when the Scanner successfully pairs with this phone.
     */
    fun showPairSuccessNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1002,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, PAYMENT_CHANNEL_ID)
            .setContentTitle("Scanner Phone Connected 🎉")
            .setContentText("Phone A has successfully paired with this device.")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        manager.notify(1002, builder.build())
    }

    fun canUseFullScreenIntent(context: Context, manager: NotificationManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            manager.canUseFullScreenIntent()
        } else {
            true
        }
    }
}
