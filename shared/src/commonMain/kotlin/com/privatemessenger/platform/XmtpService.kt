package com.privatemessenger.platform

import kotlinx.coroutines.flow.Flow

/**
 * A lightweight handle wrapping the opaque platform XMTP client object.
 * Passed between [XmtpService] calls so neither platform leaks SDK types into commonMain.
 */
class XmtpClientHandle(val raw: Any)

/**
 * Data class for an incoming message delivered from the XMTP stream.
 * Only contains information that can be expressed in commonMain types.
 */
data class IncomingMessage(
    val id: String,
    val conversationId: String,
    val senderInboxId: String,
    val body: String,
    val sentAtMs: Long,
)

/**
 * Platform-agnostic interface for all XMTP operations.
 *
 * Android actual: org.xmtp:android SDK (Kotlin)
 * iOS actual:     xmtp-ios Swift SDK called via Kotlin/Native ObjC interop
 */
expect class XmtpService {
    /**
     * Creates and returns an authenticated XMTP client for the given private key.
     * Connects to the XMTP production network (MLS/V3).
     */
    suspend fun createClient(privateKeyHex: String, dbEncryptionKey: ByteArray): XmtpClientHandle

    /** Returns the local XMTP inbox ID for the given client. */
    fun getInboxId(client: XmtpClientHandle): String

    /** Returns the Ethereum public address for the given client. */
    fun getPublicAddress(client: XmtpClientHandle): String

    /** Returns true if the given Ethereum address is registered on XMTP. */
    suspend fun canMessage(client: XmtpClientHandle, ethereumAddress: String): Boolean

    /**
     * Finds or creates a deterministic 1:1 DM conversation with the given address.
     * Returns the stable XMTP conversation ID (used as the local DB key).
     */
    suspend fun findOrCreateDm(client: XmtpClientHandle, ethereumAddress: String): String

    /** Creates a new group conversation. Returns the group conversation ID. */
    suspend fun newGroup(client: XmtpClientHandle, memberInboxIds: List<String>): String

    /**
     * Sends a text message to a conversation.
     * Returns the server-assigned message ID.
     */
    suspend fun sendMessage(client: XmtpClientHandle, conversationId: String, text: String): String

    /** Syncs all conversations from the XMTP network (fetches welcome messages etc.). */
    suspend fun syncConversations(client: XmtpClientHandle)

    /**
     * Returns a cold Flow that emits every incoming message across all conversations.
     * The flow runs until cancelled.
     */
    fun streamAllMessages(client: XmtpClientHandle): Flow<IncomingMessage>
}
