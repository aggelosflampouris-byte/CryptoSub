package com.privatemessenger.data.local.dao

import androidx.room.*
import com.privatemessenger.data.local.entity.MessageEntity
import com.privatemessenger.data.local.entity.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Update
    suspend fun update(message: MessageEntity)

    /**
     * Returns all messages in a conversation, ordered chronologically.
     * Returns a Flow so the UI observes changes reactively.
     */
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    /**
     * Returns recent messages for a conversation (for initial load / pagination).
     */
    @Query("""
        SELECT * FROM messages 
        WHERE conversation_id = :conversationId 
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    suspend fun getRecentMessages(conversationId: String, limit: Int = 50): List<MessageEntity>

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: String, status: MessageStatus)

    @Query("UPDATE messages SET status = :status WHERE conversation_id = :conversationId AND sender_user_id = :senderUserId AND timestamp <= :timestamp AND status != :status")
    suspend fun updateStatusForSenderBefore(conversationId: String, senderUserId: String, timestamp: Long, status: MessageStatus)

    @Query("UPDATE messages SET reactions_json = :reactionsJson WHERE id = :messageId")
    suspend fun updateReactions(messageId: String, reactionsJson: String?)

    @Query("UPDATE messages SET server_message_id = :serverMessageId WHERE id = :localMessageId")
    suspend fun setServerMessageId(localMessageId: String, serverMessageId: String)

    @Query("SELECT * FROM messages WHERE server_message_id = :serverMessageId LIMIT 1")
    suspend fun findByServerMessageId(serverMessageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun findById(messageId: String): MessageEntity?

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun delete(messageId: String)

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteAllInConversation(conversationId: String)

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId AND timestamp <= :timestamp")
    suspend fun deleteAllInConversationBefore(conversationId: String, timestamp: Long)
}
