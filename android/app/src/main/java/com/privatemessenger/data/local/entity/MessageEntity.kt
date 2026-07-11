package com.privatemessenger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores decrypted message content locally. This plaintext exists ONLY
 * in the SQLCipher-encrypted database on the device — never on the server,
 * never in plaintext on disk.
 */
@Entity(
    tableName = "messages",
    indices = [
        Index("conversation_id", "timestamp"),
    ],
)
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,  // locally generated UUID

    @ColumnInfo(name = "conversation_id")
    val conversationId: String,

    @ColumnInfo(name = "sender_user_id")
    val senderUserId: String,

    @ColumnInfo(name = "content")
    val content: String,  // plaintext — encrypted at rest by SQLCipher

    @ColumnInfo(name = "attachment_uri")
    val attachmentUri: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "status")
    val status: MessageStatus = MessageStatus.SENDING,

    @ColumnInfo(name = "type")
    val type: MessageType = MessageType.TEXT,

    /** Server-assigned message ID, used for ACK confirmation */
    @ColumnInfo(name = "server_message_id")
    val serverMessageId: String? = null,
)

enum class MessageStatus {
    SENDING,    // queued locally, not yet sent
    SENT,       // server accepted the ciphertext
    DELIVERED,  // recipient's device ACKed receipt
    READ,       // recipient opened the message
    FAILED,     // send failed after retries
}

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    FILE,
    VOICE,
}
