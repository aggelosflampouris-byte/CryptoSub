package com.privatemessenger.platform

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.xmtp.android.library.Client
import org.xmtp.android.library.ClientOptions
import org.xmtp.android.library.Conversation
import org.xmtp.android.library.XMTPEnvironment
import org.xmtp.android.library.libxmtp.IdentityKind
import org.xmtp.android.library.libxmtp.PublicIdentity
import org.xmtp.android.library.messages.PrivateKeyBuilder

/**
 * Android actual for [XmtpService].
 * Delegates entirely to the org.xmtp:android Kotlin SDK.
 */
actual class XmtpService(private val context: Context) {

    actual suspend fun createClient(
        privateKeyHex: String,
        dbEncryptionKey: ByteArray,
    ): XmtpClientHandle {
        val keyBytes = privateKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val privateKey = PrivateKeyBuilder.buildFromPrivateKeyData(keyBytes)
        val account = PrivateKeyBuilder(privateKey)

        val client = Client.create(
            account = account,
            options = ClientOptions(
                api = ClientOptions.Api(
                    env = XMTPEnvironment.PRODUCTION,
                    isSecure = true,
                ),
                appContext = context,
                dbEncryptionKey = dbEncryptionKey,
            ),
        )
        return XmtpClientHandle(client)
    }

    actual fun getInboxId(client: XmtpClientHandle): String =
        (client.raw as Client).inboxId

    actual fun getPublicAddress(client: XmtpClientHandle): String =
        (client.raw as Client).publicIdentity.identifier

    actual suspend fun canMessage(
        client: XmtpClientHandle,
        ethereumAddress: String,
    ): Boolean {
        val xmtpClient = client.raw as Client
        val identity = PublicIdentity(IdentityKind.ETHEREUM, ethereumAddress)
        val result = xmtpClient.canMessage(listOf(identity))
        return result[ethereumAddress.lowercase()] == true
    }

    actual suspend fun findOrCreateDm(
        client: XmtpClientHandle,
        ethereumAddress: String,
    ): String {
        val xmtpClient = client.raw as Client
        val identity = PublicIdentity(IdentityKind.ETHEREUM, ethereumAddress)
        val dm = xmtpClient.conversations.findOrCreateDmWithIdentity(identity)
        return dm.id
    }

    actual suspend fun newGroup(
        client: XmtpClientHandle,
        memberInboxIds: List<String>,
    ): String {
        val xmtpClient = client.raw as Client
        val group = xmtpClient.conversations.newGroup(memberInboxIds)
        return group.id
    }

    actual suspend fun sendMessage(
        client: XmtpClientHandle,
        conversationId: String,
        text: String,
    ): String {
        val xmtpClient = client.raw as Client
        val conversation = xmtpClient.conversations.findConversation(conversationId)
            ?: error("Conversation not found: $conversationId")
        return when (conversation) {
            is Conversation.Dm -> conversation.dm.send(text)
            is Conversation.Group -> conversation.group.send(text)
        }
    }

    actual suspend fun syncConversations(client: XmtpClientHandle) {
        (client.raw as Client).conversations.sync()
    }

    actual fun streamAllMessages(client: XmtpClientHandle): Flow<IncomingMessage> = channelFlow {
        val xmtpClient = client.raw as Client
        xmtpClient.conversations.streamAllMessages().collect { msg ->
            // Filter XMTP system messages (inbox-ID-only payloads)
            if (!msg.body.matches(Regex("^(@[a-fA-F0-9]{40,}\\s*)+"))) {
                trySend(
                    IncomingMessage(
                        id = msg.id,
                        conversationId = msg.conversationId,
                        senderInboxId = msg.senderInboxId,
                        body = msg.body,
                        sentAtMs = msg.sentAt.time,
                    )
                )
            }
        }
    }
}
