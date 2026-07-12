package com.privatemessenger.domain.repository

import android.util.Log
import com.privatemessenger.crypto.EncryptedPayload
import com.privatemessenger.crypto.EnvelopeType
import com.privatemessenger.crypto.RatchetEngine
import com.privatemessenger.data.local.dao.MessageDao
import com.privatemessenger.data.local.entity.MessageEntity
import com.privatemessenger.data.remote.websocket.Envelope
import com.privatemessenger.data.remote.websocket.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID
import com.privatemessenger.crypto.SealedSenderCrypto
import com.privatemessenger.data.local.dao.ContactDao
import com.privatemessenger.crypto.AttachmentCrypto
import com.privatemessenger.crypto.AttachmentPayload
import com.privatemessenger.keystore.KeyStoreManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import android.content.Context
import java.io.File

/**
 * MessageRepository bridges the network, crypto, and local storage layers.
 * It listens to incoming envelopes, decrypts them, saves them to the local
 * database, and sends acknowledgments back to the server.
 */
class MessageRepository(
    private val webSocketManager: WebSocketManager,
    private val ratchetEngine: RatchetEngine,
    private val messageDao: MessageDao,
    private val contactDao: ContactDao,
    private val keyStoreManager: KeyStoreManager,
    private val sealedSenderCrypto: SealedSenderCrypto,
    private val blobRepository: BlobRepository,
    private val context: Context,
    private val localUserId: String,
    private val localDeviceId: Int,
    private val scope: CoroutineScope
) {
    init {
        // Listen to incoming envelopes from WebSocket
        webSocketManager.incomingEnvelopes
            .onEach { envelope ->
                processIncomingEnvelope(envelope)
            }
            .launchIn(scope)
    }

    private suspend fun processIncomingEnvelope(envelope: Envelope) {
        // Only process ciphertext or prekey messages
        val type = try {
            EnvelopeType.fromWire(envelope.type)
        } catch (e: IllegalArgumentException) {
            Log.w("MessageRepository", "Unknown envelope type: ${envelope.type}")
            return
        }

        if (type == EnvelopeType.ACK) return

        try {
            // 1. Trial Decryption: Find out who sent this envelope
            val contacts = contactDao.getAllContacts().firstOrNull() ?: emptyList()
            val candidateKeys = contacts
                .filter { it.profileKey != null }
                .associate { it.userId to it.profileKey!! }

            val senderInfo = sealedSenderCrypto.trialDecrypt(
                ciphertext = envelope.sealed_sender_ciphertext,
                candidateKeys = candidateKeys
            )

            if (senderInfo == null) {
                // Trial decryption failed. Either we don't have their profile key,
                // or the ciphertext is corrupted.
                // In a full implementation, we'd fall back to "Unidentified Sender" parsing
                // if they sent it in plaintext over the Double Ratchet.
                Log.w("MessageRepository", "Failed to identify sender via Trial Decryption. Dropping envelope.")
                return
            }

            val (identity, candidateUserId) = senderInfo
            val senderUserId = identity.userId
            val senderDeviceId = identity.deviceId

            // 2. Decrypt the Double Ratchet payload
            val decryptedPayload = ratchetEngine.decrypt(
                senderUserId = senderUserId,
                senderDeviceId = senderDeviceId,
                ciphertext = envelope.message_ciphertext,
                type = type
            )

            // 3. Update the sender's profile key in case it changed (or we want to ensure it's fresh)
            contactDao.getContact(senderUserId)?.let { contact ->
                if (!contact.profileKey.contentEquals(decryptedPayload.senderProfileKey)) {
                    contactDao.upsert(contact.copy(profileKey = decryptedPayload.senderProfileKey))
                }
            }

            // 4. Handle optional attachment
            var localAttachmentUri: String? = null
            decryptedPayload.attachment?.let { attachment ->
                try {
                    val ciphertext = blobRepository.downloadEncryptedBlob(attachment.url)
                    val plaintext = AttachmentCrypto.decrypt(
                        ciphertext = ciphertext,
                        key = android.util.Base64.decode(attachment.keyBase64, android.util.Base64.NO_WRAP),
                        iv = android.util.Base64.decode(attachment.ivBase64, android.util.Base64.NO_WRAP)
                    )
                    
                    // Save to local cache directory
                    val file = File(context.cacheDir, "attachment_${UUID.randomUUID()}.dat")
                    file.writeBytes(plaintext)
                    localAttachmentUri = file.absolutePath
                } catch (e: Exception) {
                    Log.e("MessageRepository", "Failed to download or decrypt attachment", e)
                }
            }

            // 5. Save to local DB
            val messageEntity = MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = senderUserId, // Simplified for 1:1
                senderUserId = senderUserId,
                content = decryptedPayload.text,
                attachmentUri = localAttachmentUri,
                timestamp = envelope.server_timestamp,
                serverMessageId = envelope.message_id
            )
            messageDao.insert(messageEntity)

            // 6. Acknowledge receipt to server so it deletes from Cassandra
            envelope.message_id?.let {
                webSocketManager.sendAck(it)
            }

        } catch (e: org.whispersystems.libsignal.InvalidMessageException) {
            Log.e("MessageRepository", "Failed to decrypt message", e)
        } catch (e: Exception) {
            Log.e("MessageRepository", "Error processing envelope", e)
        }
    }

    /**
     * Encrypts and sends a plaintext message, optionally with an attachment, to a recipient.
     */
    fun sendMessage(recipientUserId: String, recipientDeviceId: Int, plaintext: String, attachmentBytes: ByteArray? = null, mimeType: String? = null) {
        scope.launch(Dispatchers.IO) {
            try {
                var attachmentPayload: AttachmentPayload? = null
                var localAttachmentUri: String? = null

                if (attachmentBytes != null && mimeType != null) {
                    // 1. Encrypt attachment locally
                    val encryptedAttachment = AttachmentCrypto.encrypt(attachmentBytes)

                    // 2. Upload ciphertext to S3 via pre-signed URL
                    val uploadResult = blobRepository.uploadEncryptedBlob(encryptedAttachment.ciphertext, mimeType)

                    // 3. Build attachment payload for Ratchet JSON
                    attachmentPayload = AttachmentPayload(
                        url = uploadResult.downloadUrl,
                        keyBase64 = android.util.Base64.encodeToString(encryptedAttachment.key, android.util.Base64.NO_WRAP),
                        ivBase64 = android.util.Base64.encodeToString(encryptedAttachment.iv, android.util.Base64.NO_WRAP),
                        mimeType = mimeType
                    )

                    // 4. Save a local copy to avoid re-downloading our own sent files
                    val file = File(context.cacheDir, "attachment_${UUID.randomUUID()}.dat")
                    file.writeBytes(attachmentBytes)
                    localAttachmentUri = file.absolutePath
                }

                val myProfileKey = keyStoreManager.getOrCreateProfileKey()

                // 5. Encrypt Double Ratchet payload (bundles our Profile Key + attachment info inside)
                val encryptedPayload: EncryptedPayload = ratchetEngine.encrypt(
                    recipientUserId = recipientUserId,
                    recipientDeviceId = recipientDeviceId,
                    plaintext = plaintext,
                    myProfileKey = myProfileKey,
                    attachment = attachmentPayload
                )

                // 2. Encrypt Sealed Sender identity using our own Profile Key
                val sealedSenderCiphertext = sealedSenderCrypto.encrypt(
                    senderUserId = localUserId,
                    senderDeviceId = localDeviceId,
                    myProfileKey = myProfileKey
                )

                // 3. Construct routing envelope
                val envelope = Envelope(
                    recipient_user_id = recipientUserId,
                    recipient_device_id = recipientDeviceId.toString(),
                    sealed_sender_ciphertext = sealedSenderCiphertext,
                    message_ciphertext = encryptedPayload.ciphertext,
                    type = encryptedPayload.type.wire,
                    server_timestamp = 0L // Server will set this
                )

                // Send via WebSocket
                webSocketManager.sendEnvelope(envelope)

                // Save locally as SENDING
                val localMessage = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = "stub_conversation_id", // Should be derived from participants
                    senderUserId = localUserId,
                    content = plaintext,
                    attachmentUri = localAttachmentUri,
                    timestamp = System.currentTimeMillis()
                )
                messageDao.insert(localMessage)

            } catch (e: Exception) {
                Log.e("MessageRepository", "Failed to send message", e)
            }
        }
    }
}
