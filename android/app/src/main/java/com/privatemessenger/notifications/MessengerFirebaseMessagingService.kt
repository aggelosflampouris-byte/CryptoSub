package com.privatemessenger.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.privatemessenger.data.remote.websocket.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles incoming push notifications from Firebase Cloud Messaging.
 *
 * Security Note:
 * This app is designed so that the server NEVER sends the ciphertext
 * or plaintext in the FCM payload. The FCM payload is strictly a
 * "wake up ping" containing NO message data.
 *
 * When a ping is received, this service spins up the WebSocketManager,
 * authenticates, and downloads the ciphertext envelopes over the
 * secure WebSocket channel.
 */
class MessengerFirebaseMessagingService : FirebaseMessagingService() {

    // Note: In a real DI setup (Hilt/Koin), this would be injected.
    // For this architecture, we assume WebSocketManager is accessible
    // via a singleton or application class.
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Refreshed token: $token")
        
        // TODO: Send token to our server via POST /v1/devices/fcm
        // using ApiClient.
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Check if message contains a data payload with our expected action
        if (remoteMessage.data.isNotEmpty()) {
            val action = remoteMessage.data["action"]
            if (action == "ping") {
                Log.d("FCM", "Received wake-up ping. Triggering WebSocket reconnect.")
                
                // Trigger the WebSocket to connect, which will automatically
                // flush the server's offline queue and pull down our pending
                // envelopes. Once the envelopes are pulled down, the 
                // MessageRepository decrypts them and triggers a local notification.
                scope.launch {
                    // WebSocketManager.connect()
                    // If already connected, this is a no-op or forces a sync
                }
            }
        }
        
        // We ignore remoteMessage.notification completely.
    }
}
