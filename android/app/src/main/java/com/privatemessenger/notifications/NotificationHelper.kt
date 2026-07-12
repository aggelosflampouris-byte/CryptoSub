package com.privatemessenger.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.privatemessenger.ui.MainActivity

object NotificationHelper {
    private const val CHANNEL_CONTACTS = "new_contacts"
    private const val CHANNEL_MESSAGES = "new_messages"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CONTACTS,
                "New Contacts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alerts when someone adds you on XMTP" }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGES,
                "New Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alerts for incoming encrypted messages" }
        )
    }

    fun showNewContactNotification(context: Context, senderLabel: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CONTACTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Contact")
            .setContentText("$senderLabel added you on CryptoSub")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(senderLabel.hashCode(), notification)
    }

    fun showNewMessageNotification(context: Context, senderLabel: String, preview: String, conversationId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_conversation", conversationId)
        }
        val pi = PendingIntent.getActivity(
            context, conversationId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(senderLabel)
            .setContentText(preview)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(conversationId.hashCode(), notification)
    }
}
