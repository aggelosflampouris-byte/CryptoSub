package com.privatemessenger.crypto

import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.InvalidKeyException
import org.whispersystems.libsignal.SessionBuilder
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.state.PreKeyBundle

/**
 * SignalSessionBuilder handles the X3DH (Extended Triple Diffie-Hellman)
 * key agreement protocol to establish encrypted sessions between devices.
 *
 * Flow (initiator side):
 *   1. Fetch the recipient's pre-key bundle from the server
 *      (GET /v1/users/{id}/devices/{id}/bundle)
 *   2. Call [buildSession] with the bundle â†’ creates a local session
 *   3. The first message sent through this session is a "prekey message"
 *      that carries the ephemeral key the responder needs to derive the
 *      same shared secret. After that, the Double Ratchet takes over.
 *
 * The server never learns the shared secret â€” it only relays the public
 * key material and the resulting ciphertext.
 */
class SignalSessionBuilder(
    private val protocolStore: SignalProtocolStoreImpl,
) {

    /**
     * Creates a Signal session with a remote device using the pre-key
     * bundle fetched from the server.
     *
     * @param recipientUserId   The remote user's ID (UUID string)
     * @param recipientDeviceId The remote device's ID
     * @param bundle            The server-provided pre-key bundle containing:
     *                            - identity public key
     *                            - signed pre key (+ signature)
     *                            - one-time pre key (optional, may be null)
     *                            - registration ID
     *
     * @throws InvalidKeyException if the bundle contains invalid key material
     *         (e.g. a bad signature on the signed pre key â€” this could
     *         indicate tampering or a server bug and must halt session
     *         creation rather than silently proceeding)
     */
    @Throws(InvalidKeyException::class)
    fun buildSession(
        recipientUserId: String,
        recipientDeviceId: Int,
        bundle: PreKeyBundle,
    ) {
        val address = SignalProtocolAddress(recipientUserId, recipientDeviceId)
        val builder = SessionBuilder(protocolStore, address)
        builder.process(bundle)
    }

    /**
     * Checks whether we already have an active session with the given
     * device. If true, we can send ciphertext messages directly without
     * needing a pre-key bundle fetch.
     */
    fun hasSession(recipientUserId: String, recipientDeviceId: Int): Boolean {
        val address = SignalProtocolAddress(recipientUserId, recipientDeviceId)
        return protocolStore.loadSession(address).hasSenderChain()
    }

    /**
     * Builds a [PreKeyBundle] object from the raw fields returned by the
     * server's GET /v1/users/{id}/devices/{id}/bundle endpoint.
     *
     * This conversion lives here (rather than in the network layer) because
     * it involves parsing cryptographic key material, and any errors should
     * be caught at this layer.
     */
    @Throws(InvalidKeyException::class)
    fun bundleFromServerResponse(
        registrationId: Int,
        deviceId: Int,
        signedPreKeyId: Int,
        signedPreKeyPublic: ByteArray,
        signedPreKeySignature: ByteArray,
        identityKeyPublic: ByteArray,
        oneTimePreKeyId: Int?,
        oneTimePreKeyPublic: ByteArray?,
    ): PreKeyBundle {
        val identityKey = IdentityKey(identityKeyPublic, 0)

        return if (oneTimePreKeyId != null && oneTimePreKeyPublic != null) {
            PreKeyBundle(
                registrationId,
                deviceId,
                oneTimePreKeyId,
                org.whispersystems.libsignal.ecc.Curve.decodePoint(oneTimePreKeyPublic, 0),
                signedPreKeyId,
                org.whispersystems.libsignal.ecc.Curve.decodePoint(signedPreKeyPublic, 0),
                signedPreKeySignature,
                identityKey,
            )
        } else {
            // No one-time pre key available â€” X3DH still works, just
            // with slightly reduced forward secrecy for this handshake.
            PreKeyBundle(
                registrationId,
                deviceId,
                -1,   // sentinel: no one-time prekey
                null, // no one-time prekey
                signedPreKeyId,
                org.whispersystems.libsignal.ecc.Curve.decodePoint(signedPreKeyPublic, 0),
                signedPreKeySignature,
                identityKey,
            )
        }
    }
}
