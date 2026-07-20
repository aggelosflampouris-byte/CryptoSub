package com.privatemessenger.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a conversation (1:1 or group) in the local Room database.
 * Defined in commonMain — Room 3.0 annotations are KMP-compatible.
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "recipient_user_id")
    val recipientUserId: String? = null,

    @ColumnInfo(name = "recipient_device_id")
    val deviceId: Int = 1,

    @ColumnInfo(name = "profile_key")
    val profileKey: ByteArray? = null,

    @ColumnInfo(name = "group_id")
    val groupId: String? = null,

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
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConversationEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
