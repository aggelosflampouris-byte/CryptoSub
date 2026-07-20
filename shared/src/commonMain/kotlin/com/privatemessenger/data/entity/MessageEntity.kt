package com.privatemessenger.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores decrypted message content locally.
 * Plaintext exists ONLY inside the platform-encrypted local database — never on the server.
 * Defined in commonMain — Room 3.0 annotations are KMP-compatible.
 */
@Entity(
    tableName = "messages",
    indices = [Index("conversation_id", "timestamp")],
)
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "conversation_id")
    val conversationId: String,

    @ColumnInfo(name = "sender_user_id")
    val senderUserId: String,

    /** Decrypted plaintext — encrypted at rest by the platform DB layer. */
    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "attachment_uri")
    val attachmentUri: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "status")
    val status: MessageStatus = MessageStatus.SENDING,

    @ColumnInfo(name = "type")
    val type: MessageType = MessageType.TEXT,

    @ColumnInfo(name = "server_message_id")
    val serverMessageId: String? = null,
)

enum class MessageStatus {
    SENDING,   // queued locally, not yet sent
    SENT,      // server accepted the ciphertext
    DELIVERED, // recipient device ACKed receipt
    READ,      // recipient opened the message
    FAILED,    // send failed after retries
}

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    FILE,
    VOICE,
}
