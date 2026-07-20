package com.privatemessenger.platform

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * Android actual for push notification helpers.
 * Notification channels are created once by the Android-specific XmtpBackgroundService.
 */

private const val CHANNEL_MESSAGES = "new_messages"
private const val CHANNEL_CONTACTS = "new_contacts"

// The app context is injected at initialization via [NotificationDispatcher].
// We use a module-level holder so the top-level functions can access it without
// requiring a class instance (matching the expect signature).
internal lateinit var notificationContext: Context

actual fun showMessageNotification(title: String, body: String, conversationId: String) {
    val ctx = notificationContext
    val intent = ctx.packageManager
        .getLaunchIntentForPackage(ctx.packageName)
        ?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_conversation", conversationId)
        }
    val pi = if (intent != null) {
        PendingIntent.getActivity(
            ctx, conversationId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    } else null

    val notification = NotificationCompat.Builder(ctx, CHANNEL_MESSAGES)
        .setSmallIcon(android.R.drawable.ic_dialog_email)
        .setContentTitle(title)
        .setContentText(body)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .apply { if (pi != null) setContentIntent(pi) }
        .build()

    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.notify(conversationId.hashCode(), notification)
}

actual fun showContactNotification(contactLabel: String) {
    val ctx = notificationContext
    val notification = NotificationCompat.Builder(ctx, CHANNEL_CONTACTS)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("New Contact")
        .setContentText("$contactLabel added you on CryptoSub")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.notify(contactLabel.hashCode(), notification)
}
