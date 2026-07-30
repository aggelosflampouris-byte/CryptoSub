package com.privatemessenger.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.privatemessenger.PrivateMessengerApp
import com.privatemessenger.data.local.entity.MessageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker that runs in the background when the network is connected to retry
 * sending messages that are stuck in the "SENDING" state.
 */
class SendMessageWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? PrivateMessengerApp ?: return@withContext Result.failure()
        val database = app.database
        val client = app.xmtpClient ?: return@withContext Result.retry()

        try {
            // Because there's no direct "get all by status" in the DAO right now,
            // we'll have to fetch recent conversations and their recent messages,
            // or just rely on a new query. Wait, let's implement a query in MessageDao 
            // if it doesn't exist, or just use a custom flow if we can.
            // Actually, we can fetch all conversations, then their messages, but that's expensive.
            // Let's assume we'll add `getMessagesByStatus(status: MessageStatus)` to MessageDao.
            
            val pendingMessages = database.messageDao().getMessagesByStatus(MessageStatus.SENDING)
            
            if (pendingMessages.isEmpty()) {
                return@withContext Result.success()
            }

            var allSuccess = true

            for (msg in pendingMessages) {
                // If it's an attachment/audio that hasn't been uploaded, we need to upload it.
                // For now, let's focus on TEXT messages (or replies).
                // Attachments are handled in the ChatScreen but if they failed *before* upload,
                // they are tricky to retry without the original bits. The file is saved locally though!
                
                try {
                    val xmtpConversation = client.conversations.findConversation(msg.conversationId)
                    if (xmtpConversation == null) {
                        allSuccess = false
                        continue
                    }
                    
                    val payload = if (msg.replyToMessageId != null) {
                        Gson().toJson(mapOf(
                            "type" to "reply",
                            "replyToId" to msg.replyToMessageId,
                            "content" to msg.content
                        ))
                    } else if (msg.attachmentUri != null || msg.audioUri != null) {
                        // Skip attachments in the simple retry queue for now to prevent double-upload issues
                        // unless we implement full background upload.
                        allSuccess = false
                        continue
                    } else {
                        msg.content
                    }
                    
                    val sentMessageId = when (xmtpConversation) {
                        is org.xmtp.android.library.Conversation.Dm -> xmtpConversation.dm.send(payload)
                        is org.xmtp.android.library.Conversation.Group -> xmtpConversation.group.send(payload)
                        else -> error("Unknown conversation type")
                    }
                    
                    database.messageDao().delete(msg.id)
                    database.messageDao().insert(msg.copy(id = sentMessageId, status = MessageStatus.SENT))
                    
                } catch (e: Exception) {
                    Log.e("SendMessageWorker", "Failed to retry message ${msg.id}", e)
                    allSuccess = false
                }
            }

            return@withContext if (allSuccess) Result.success() else Result.retry()

        } catch (e: Exception) {
            Log.e("SendMessageWorker", "Error processing offline queue", e)
            return@withContext Result.retry()
        }
    }
}
