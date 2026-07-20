package com.privatemessenger.platform

/**
 * Platform-agnostic push notification dispatcher.
 *
 * Android actual: NotificationCompat (NotificationManager)
 * iOS actual:     UNUserNotificationCenter
 */
expect fun showMessageNotification(
    title: String,
    body: String,
    conversationId: String,
)

expect fun showContactNotification(
    contactLabel: String,
)
