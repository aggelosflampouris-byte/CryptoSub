package com.privatemessenger.crypto

import org.signal.libsignal.protocol.DuplicateMessageException
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import com.google.gson.Gson

/**
 * The inner structure of the JSON payload encrypted by the Double Ratchet.
 */
data class AttachmentPayload(
    val url: String,
    val keyBase64: String,
    val ivBase64: String,
    val mimeType: String
)

data class PlaintextPayload(
    val text: String,
    val senderProfileKey: ByteArray? = null,
    val attachment: AttachmentPayload? = null
)

/**
 * RatchetEngine wraps libsignal's [SessionCipher] to provide encrypt/decrypt
 * operations using the Double Ratchet Algorithm.
 *
 * Each call to [encrypt] ratchets the sending chain forward, producing a
 * unique key per message. Each call to [decrypt] ratchets the receiving
 * chain. Out-of-order messages are handled automatically by the ratchet's
 * message-key cache (up to a configurable window).
 *
 * The engine never stores plaintext — it receives plaintext from the UI
 * layer, returns ciphertext (and vice versa), and delegates all key-state
 * persistence to the [SignalProtocolStoreImpl] (which writes to the
 * encrypted Room database).
 */
class RatchetEngine(
    private val protocolStore: SignalProtocolStoreImpl,
    private val gson: Gson = Gson()
) {

    /**
     * Encrypts a plaintext message for a specific recipient device.
     *
     * If this is the first message to a new session (established via
     * [SignalSessionBuilder.buildSession]), the returned [CiphertextMessage]
     * will be a [PreKeySignalMessage] (type = PREKEY_TYPE). Subsequent
     * messages will be regular [SignalMessage]s (type = WHISPER_TYPE).
     *
     * @return [EncryptedPayload] containing the ciphertext bytes and the
     *         type indicator the server needs to route it correctly.
     *
     * @throws NoSessionException if no session exists with this device —
     *         caller must fetch a pre-key bundle and build a session first.
     */
    @Throws(NoSessionException::class)
    fun encrypt(
        recipientUserId: String,
        recipientDeviceId: Int,
        plaintext: String,
        myProfileKey: ByteArray?,
        attachment: AttachmentPayload? = null
    ): EncryptedPayload {
        val payloadObj = PlaintextPayload(
            text = plaintext,
            senderProfileKey = myProfileKey,
            attachment = attachment
        )
        val rawPlaintext = gson.toJson(payloadObj).toByteArray()

        val address = SignalProtocolAddress(recipientUserId, recipientDeviceId)
        val cipher = SessionCipher(protocolStore, address)
        val ciphertextMessage = cipher.encrypt(rawPlaintext)

        val type = when (ciphertextMessage.type) {
            CiphertextMessage.PREKEY_TYPE -> EnvelopeType.PREKEY_MESSAGE
            CiphertextMessage.WHISPER_TYPE -> EnvelopeType.CIPHERTEXT
            else -> EnvelopeType.CIPHERTEXT
        }

        return EncryptedPayload(
            ciphertext = ciphertextMessage.serialize(),
            type = type,
        )
    }

    /**
     * Decrypts an incoming ciphertext message from a sender device.
     *
     * Handles both pre-key messages (first message of a new session, which
     * implicitly completes the X3DH handshake on the responder side) and
     * regular ratcheted messages.
     *
     * @param senderUserId   The sender's user ID (from the sealed-sender
     *                       decryption — see [SealedSenderDecryptor])
     * @param senderDeviceId The sender's device ID
     * @param ciphertext     The raw ciphertext bytes
     * @param type           Whether this is a prekey message or regular ciphertext
     *
     * @return The decrypted plaintext bytes.
     *
     * @throws InvalidMessageException if the ciphertext is malformed
     * @throws DuplicateMessageException if this exact message was already decrypted
     * @throws InvalidKeyException if key material in a pre-key message is bad
     */
    @Throws(
        InvalidMessageException::class,
        DuplicateMessageException::class,
        InvalidKeyException::class,
    )
    fun decrypt(
        senderUserId: String,
        senderDeviceId: Int,
        ciphertext: ByteArray,
        type: EnvelopeType,
    ): DecryptedPayload {
        val address = SignalProtocolAddress(senderUserId, senderDeviceId)
        val cipher = SessionCipher(protocolStore, address)

        val rawPlaintext = when (type) {
            EnvelopeType.PREKEY_MESSAGE -> {
                val preKeyMessage = PreKeySignalMessage(ciphertext)
                cipher.decrypt(preKeyMessage)
            }
            EnvelopeType.CIPHERTEXT -> {
                val signalMessage = SignalMessage(ciphertext)
                cipher.decrypt(signalMessage)
            }
            else -> throw InvalidMessageException("Unsupported envelope type: $type")
        }

        val payloadObj = gson.fromJson(String(rawPlaintext), PlaintextPayload::class.java)
        return DecryptedPayload(
            text = payloadObj.text,
            senderProfileKey = payloadObj.senderProfileKey ?: ByteArray(0),
            attachment = payloadObj.attachment
        )
    }
}

/**
 * Output of a decrypt operation containing the message text, sender's profile key, and optional attachment.
 */
data class DecryptedPayload(
    val text: String,
    val senderProfileKey: ByteArray,
    val attachment: AttachmentPayload? = null
)

/**
 * The output of an encrypt operation: ciphertext bytes + the envelope type
 * the server needs (prekey_message vs ciphertext).
 */
data class EncryptedPayload(
    val ciphertext: ByteArray,
    val type: EnvelopeType,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedPayload) return false
        return ciphertext.contentEquals(other.ciphertext) && type == other.type
    }

    override fun hashCode(): Int {
        return 31 * ciphertext.contentHashCode() + type.hashCode()
    }
}

/**
 * Mirrors the server-side relay.EnvelopeType constants so the Android
 * client and Go server agree on type strings.
 */
enum class EnvelopeType(val wire: String) {
    PREKEY_MESSAGE("prekey_message"),
    CIPHERTEXT("ciphertext"),
    RECEIPT("receipt"),
    GROUP_CONTROL("group_control"),
    ACK("ack");

    companion object {
        fun fromWire(value: String): EnvelopeType =
            entries.firstOrNull { it.wire == value }
                ?: throw IllegalArgumentException("Unknown envelope type: $value")
    }
}
