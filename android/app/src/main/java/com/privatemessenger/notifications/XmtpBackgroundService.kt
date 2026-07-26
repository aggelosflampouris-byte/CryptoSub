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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.privatemessenger.utils.TypingManager

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

        startListeningLoop()
        startUpdateCheckLoop()

        // If the system kills the service, recreate it
        return START_STICKY
    }

    private fun startUpdateCheckLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val info = com.privatemessenger.utils.AppUpdater.checkForUpdate()
                    if (info.isUpdateAvailable && info.downloadUrl != null) {
                        NotificationHelper.showUpdateNotification(this@XmtpBackgroundService, info.latestVersion)
                    }
                } catch (e: Exception) {
                    Log.e("XmtpBackgroundService", "Error checking for updates in background", e)
                }
                // Check every 1 hour
                delay(60L * 60 * 1000)
            }
        }
    }

    /**
     * Runs in an infinite loop so that if the XMTP stream disconnects
     * (network hiccup, server restart, etc.) the service automatically
     * reconnects after a short delay instead of going silent forever.
     */
    private fun startListeningLoop() {
        serviceScope.launch {
            val app = application as? PrivateMessengerApp ?: return@launch
            val client = app.xmtpClient

            if (client == null) {
                Log.e("XmtpBackgroundService", "XMTP Client not initialized. Service stopping.")
                stopSelf()
                return@launch
            }

            var retryDelay = 3_000L  // start at 3 s, back-off to 30 s max

            while (isActive) {
                try {
                    // Always sync first so we don't miss messages received
                    // while the stream was down.
                    Log.d("XmtpBackgroundService", "Syncing conversations before streaming...")
                    client.conversations.sync()

                    Log.d("XmtpBackgroundService", "Listening for incoming XMTP messages...")
                    retryDelay = 3_000L   // reset back-off on successful connect

                    client.conversations.streamAllMessages().collect { message ->
                        try {
                            // Ignore XMTP system messages (group updates / member additions)
                            // These typically start with '@' and contain long hex strings (Inbox IDs).
                            val trimmedBody = message.body.trim()
                            if (trimmedBody.startsWith("@") && trimmedBody.replace(Regex("[\\s@]"), "").length >= 60) {
                                Log.d("XmtpBackgroundService", "Ignoring system message: ${message.body}")
                                return@collect
                            }

                            val convId = message.conversationId
                            var finalContent = message.body
                            var finalReplyToId: String? = null
                            var isStructuralPayload = false

                            if (trimmedBody.startsWith("{") && trimmedBody.endsWith("}")) {
                                try {
                                    val json = JsonParser.parseString(trimmedBody).asJsonObject
                                    val type = json.get("type")?.asString
                                    
                                    if (type == "reaction") {
                                        val targetMessageId = json.get("messageId")?.asString
                                        val emoji = json.get("emoji")?.asString
                                        if (targetMessageId != null && emoji != null) {
                                            val msg = app.database.messageDao().findById(targetMessageId)
                                            if (msg != null) {
                                                val reactionsType = object : TypeToken<MutableMap<String, String>>() {}.type
                                                val reactionsMap: MutableMap<String, String> = if (msg.reactionsJson != null) {
                                                    Gson().fromJson(msg.reactionsJson, reactionsType) ?: mutableMapOf()
                                                } else {
                                                    mutableMapOf()
                                                }
                                                if (emoji.isEmpty()) {
                                                    reactionsMap.remove(message.senderInboxId)
                                                } else {
                                                    reactionsMap[message.senderInboxId] = emoji
                                                }
                                                val newJson = if (reactionsMap.isEmpty()) null else Gson().toJson(reactionsMap)
                                                app.database.messageDao().updateReactions(targetMessageId, newJson)
                                            }
                                        }
                                        isStructuralPayload = true
                                    } else if (type == "read") {
                                        val timestamp = json.get("timestamp")?.asLong ?: message.sentAt.time
                                        app.database.messageDao().updateStatusForSenderBefore(convId, client.inboxId, timestamp, MessageStatus.READ)
                                        isStructuralPayload = true
                                    } else if (type == "typing") {
                                        val isTyping = json.get("isTyping")?.asBoolean ?: false
                                        TypingManager.setTyping(convId, message.senderInboxId, isTyping)
                                        isStructuralPayload = true
                                    } else if (type == "reply") {
                                        finalContent = json.get("content")?.asString ?: message.body
                                        finalReplyToId = json.get("replyToId")?.asString
                                    }
                                } catch (e: Exception) {
                                    // Not a valid structural JSON, treat as normal text
                                }
                            }

                            if (isStructuralPayload) {
                                return@collect
                            }

                            val conversationExists = app.database.conversationDao().getConversation(convId) != null

                            if (!conversationExists) {
                                val xmtpConv = client.conversations.findConversation(convId)
                                val isGroup = xmtpConv is org.xmtp.android.library.Conversation.Group
                                val label = if (isGroup) {
                                    "Group Chat"
                                } else {
                                    val dm = (xmtpConv as? org.xmtp.android.library.Conversation.Dm)?.dm
                                    val peerId = dm?.peerInboxId ?: message.senderInboxId
                                    if (peerId == client.inboxId) "Me" else "${peerId.take(6)}...${peerId.takeLast(4)}"
                                }

                                val contact = ConversationEntity(
                                    id = convId,
                                    recipientUserId = (xmtpConv as? org.xmtp.android.library.Conversation.Dm)?.dm?.peerInboxId ?: message.senderInboxId,
                                    deviceId = 1,
                                    displayName = label,
                                    isGroup = isGroup,
                                    lastMessage = finalContent,
                                    lastMessageTimestamp = message.sentAt.time,
                                    unreadCount = if (message.senderInboxId != client.inboxId) 1 else 0
                                )
                                app.database.conversationDao().upsert(contact)
                                if (message.senderInboxId != client.inboxId) {
                                    NotificationHelper.showNewContactNotification(app, label)
                                }
                            } else {
                                val conv = app.database.conversationDao().getConversation(convId)
                                val senderLabel = conv?.displayName ?: "${message.senderInboxId.take(6)}..."
                                if (message.senderInboxId != client.inboxId) {
                                    NotificationHelper.showNewMessageNotification(app, senderLabel, finalContent, convId)
                                }
                            }

                            val msgEntity = MessageEntity(
                                id = message.id,
                                conversationId = convId,
                                senderUserId = message.senderInboxId,
                                content = finalContent,
                                replyToMessageId = finalReplyToId,
                                timestamp = message.sentAt.time,
                                status = MessageStatus.DELIVERED
                            )
                            app.database.messageDao().insert(msgEntity)
                            if (message.senderInboxId != client.inboxId) {
                                app.database.conversationDao().updateLastMessageAndIncrementUnread(convId, finalContent, message.sentAt.time)
                            } else {
                                app.database.conversationDao().updateLastMessage(convId, finalContent, message.sentAt.time)
                            }
                        } catch (e: Exception) {
                            Log.e("XmtpBackgroundService", "Failed to process incoming message", e)
                        }
                    }

                    // If collect() returns normally the stream closed cleanly — reconnect
                    Log.w("XmtpBackgroundService", "XMTP stream ended cleanly. Reconnecting in ${retryDelay / 1000}s...")

                } catch (e: Exception) {
                    Log.e("XmtpBackgroundService", "XMTP Stream error. Reconnecting in ${retryDelay / 1000}s...", e)
                }

                // Wait before reconnecting, then back off (max 30 s)
                delay(retryDelay)
                retryDelay = minOf(retryDelay * 2, 30_000L)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("XmtpBackgroundService", "Service destroyed")
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
