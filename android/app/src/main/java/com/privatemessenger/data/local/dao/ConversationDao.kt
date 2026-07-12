package com.privatemessenger.data.local.dao

import androidx.room.*
import com.privatemessenger.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    /**
     * All conversations, newest-activity first. Returns a Flow so the
     * chat list UI updates reactively when a new message arrives.
     */
    @Query("SELECT * FROM conversations ORDER BY last_message_timestamp DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversation(conversationId: String): ConversationEntity?

    @Query("SELECT * FROM conversations")
    suspend fun getAllConversationsSync(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE recipient_user_id = :userId LIMIT 1")
    suspend fun getConversationByRecipient(userId: String): ConversationEntity?

    @Query("""
        UPDATE conversations 
        SET last_message = :lastMessage, 
            last_message_timestamp = :timestamp,
            unread_count = unread_count + 1
        WHERE id = :conversationId
    """)
    suspend fun updateLastMessage(conversationId: String, lastMessage: String, timestamp: Long)

    @Query("UPDATE conversations SET unread_count = 0 WHERE id = :conversationId")
    suspend fun markAsRead(conversationId: String)

    @Query("UPDATE conversations SET display_name = :displayName WHERE id = :conversationId")
    suspend fun updateDisplayName(conversationId: String, displayName: String)

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun delete(conversationId: String)
}
