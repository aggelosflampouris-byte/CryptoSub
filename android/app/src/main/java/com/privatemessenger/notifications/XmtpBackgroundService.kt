package com.privatemessenger.notifications

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.privatemessenger.PrivateMessengerApp
import com.privatemessenger.data.local.entity.ConversationEntity
import com.privatemessenger.data.local.entity.MessageEntity
import com.privatemessenger.data.local.entity.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class XmtpBackgroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate() {
        super.onCreate()
        Log.d("XmtpBackgroundService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("XmtpBackgroundService", "Service started")
        
        NotificationHelper.createChannels(this)
        val notification = NotificationHelper.buildForegroundNotification(this)
        startForeground(1001, notification)

        startListening()

        // If the system kills the service, recreate it
        return START_STICKY
    }

    private fun startListening() {
        serviceScope.launch {
            val app = application as? PrivateMessengerApp ?: return@launch
            val client = app.xmtpClient

            if (client == null) {
                Log.e("XmtpBackgroundService", "XMTP Client not initialized. Service stopping.")
                stopSelf()
                return@launch
            }

            try {
                Log.d("XmtpBackgroundService", "Listening for incoming XMTP messages...")
                client.conversations.streamAllMessages().collect { message ->
                    try {
                        val convId = message.conversationId
                        val conversationExists = app.database.conversationDao().getConversation(convId) != null
                        
                        if (!conversationExists) {
                            val xmtpConv = client.conversations.findConversation(convId)
                            val isGroup = xmtpConv is org.xmtp.android.library.Conversation.Group
                            val label = if (isGroup) "Group Chat" else "${message.senderInboxId.take(6)}...${message.senderInboxId.takeLast(4)}"
                            
                            val contact = ConversationEntity(
                                id = convId,
                                deviceId = 1,
                                displayName = label,
                                isGroup = isGroup,
                                lastMessage = message.body,
                                lastMessageTimestamp = message.sentAt.time,
                                unreadCount = 1
                            )
                            app.database.conversationDao().upsert(contact)
                            // 🔔 Push notification: new contact
                            if (message.senderInboxId != client.inboxId) {
                                NotificationHelper.showNewContactNotification(app, label)
                            }
                        } else {
                            // 🔔 Push notification: new message in existing conversation
                            val conv = app.database.conversationDao().getConversation(convId)
                            val senderLabel = conv?.displayName ?: "${message.senderInboxId.take(6)}..."
                            if (message.senderInboxId != client.inboxId) {
                                NotificationHelper.showNewMessageNotification(app, senderLabel, message.body, convId)
                            }
                        }

                        val msgEntity = MessageEntity(
                            id = message.id,
                            conversationId = convId,
                            senderUserId = message.senderInboxId,
                            content = message.body,
                            timestamp = message.sentAt.time,
                            status = MessageStatus.DELIVERED
                        )
                        app.database.messageDao().insert(msgEntity)
                        app.database.conversationDao().updateLastMessage(convId, message.body, message.sentAt.time)
                    } catch (e: Exception) {
                        Log.e("XmtpBackgroundService", "Failed to process incoming message", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("XmtpBackgroundService", "XMTP Stream disconnected or failed", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("XmtpBackgroundService", "Service destroyed")
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        // We don't support binding
        return null
    }
}
