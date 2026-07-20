package com.privatemessenger.platform

import platform.UserNotifications.*

/**
 * iOS actual for push notification helpers.
 * Uses UNUserNotificationCenter — fires local notifications.
 * Remote push (APNs) is configured in the Swift app host layer.
 */

actual fun showMessageNotification(title: String, body: String, conversationId: String) {
    val content = UNMutableNotificationContent().apply {
        setTitle(title)
        setBody(body)
        setUserInfo(mapOf("conversationId" to conversationId))
        setSound(UNNotificationSound.defaultSound())
    }
    val request = UNNotificationRequest.requestWithIdentifier(
        identifier = "msg_$conversationId",
        content = content,
        trigger = null, // Deliver immediately
    )
    UNUserNotificationCenter.currentNotificationCenter()
        .addNotificationRequest(request, withCompletionHandler = null)
}

actual fun showContactNotification(contactLabel: String) {
    val content = UNMutableNotificationContent().apply {
        setTitle("New Contact")
        setBody("$contactLabel added you on CryptoSub")
        setSound(UNNotificationSound.defaultSound())
    }
    val request = UNNotificationRequest.requestWithIdentifier(
        identifier = "contact_${contactLabel.hashCode()}",
        content = content,
        trigger = null,
    )
    UNUserNotificationCenter.currentNotificationCenter()
        .addNotificationRequest(request, withCompletionHandler = null)
}
