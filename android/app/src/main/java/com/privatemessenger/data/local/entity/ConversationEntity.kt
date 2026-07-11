package com.privatemessenger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a conversation (1:1 or group) in the local database.
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,  // For 1:1: deterministic from sorted user IDs. For groups: group UUID.

    @ColumnInfo(name = "recipient_user_id")
    val recipientUserId: String? = null,  // null for group chats

    @ColumnInfo(name = "group_id")
    val groupId: String? = null,  // null for 1:1 chats

    @ColumnInfo(name = "display_name")
    val displayName: String? = null,

    @ColumnInfo(name = "last_message")
    val lastMessage: String? = null,

    @ColumnInfo(name = "last_message_timestamp")
    val lastMessageTimestamp: Long = 0,

    @ColumnInfo(name = "unread_count")
    val unreadCount: Int = 0,

    @ColumnInfo(name = "is_group")
    val isGroup: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
