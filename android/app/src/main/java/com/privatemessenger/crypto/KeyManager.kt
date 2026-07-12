package com.privatemessenger.crypto

import android.content.Context
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import org.whispersystems.libsignal.util.KeyHelper
import java.security.SecureRandom
import java.time.Instant

/**
 * KeyManager generates and manages all cryptographic key material on the device.
 *
 * Key hierarchy:
 *   Identity Key Pair  â€” long-lived, created once at registration, never changes
 *                         unless the user explicitly re-registers (which resets trust)
 *   Signed Pre Key     â€” medium-lived, rotated periodically (e.g. every 7 days)
 *   One-Time Pre Keys  â€” ephemeral, each consumed by exactly one X3DH handshake
 *
 * The private halves of all keys live ONLY on this device. The server stores
 * only the public halves (see POST /v1/register/complete and POST /v1/devices/prekeys).
 *
 * Storage: Identity key bytes are encrypted at rest via [com.privatemessenger.keystore.KeyStoreManager].
 * Signal session state is persisted in the Room database via [SignalProtocolStoreImpl].
 */
class KeyManager(
    private val protocolStore: SignalProtocolStoreImpl,
) {
    companion object {
        private const val PREKEY_BATCH_SIZE = 100
        private const val SIGNED_PREKEY_ROTATION_DAYS = 7L
    }

    // ------------------------------------------------------------------
    // Identity Key â€” generated once, used for the lifetime of this device
    // ------------------------------------------------------------------

    /**
     * Generates a new identity key pair. Called exactly once during
     * registration. The result must be persisted before anything else
     * happens (see [SignalProtocolStoreImpl]).
     */
    fun generateIdentityKeyPair(): IdentityKeyPair {
        return KeyHelper.generateIdentityKeyPair()
    }

    /**
     * Returns the local device's public identity key, which is already
     * stored in the protocol store after registration.
     */
    fun getLocalIdentityKey(): IdentityKey {
        return protocolStore.identityKeyPair.publicKey
    }

    // ------------------------------------------------------------------
    // Registration ID â€” a random 14-bit integer identifying this device
    // ------------------------------------------------------------------

    fun generateRegistrationId(): Int {
        return KeyHelper.generateRegistrationId(false)
    }

    // ------------------------------------------------------------------
    // Signed Pre Key â€” rotated periodically
    // ------------------------------------------------------------------

    /**
     * Generates a new signed pre key, signed by the identity key.
     * The [keyId] should be monotonically increasing per device.
     */
    fun generateSignedPreKey(identityKeyPair: IdentityKeyPair, keyId: Int): SignedPreKeyRecord {
        val keyPair = Curve.generateKeyPair()
        val signature = Curve.calculateSignature(
            identityKeyPair.privateKey,
            keyPair.publicKey.serialize()
        )
        val timestamp = Instant.now().toEpochMilli()
        return SignedPreKeyRecord(keyId, timestamp, keyPair, signature)
    }

    /**
     * Whether the current signed pre key is old enough to warrant rotation.
     */
    fun isSignedPreKeyStale(record: SignedPreKeyRecord): Boolean {
        val ageMs = Instant.now().toEpochMilli() - record.timestamp
        val rotationMs = SIGNED_PREKEY_ROTATION_DAYS * 24 * 60 * 60 * 1000
        return ageMs > rotationMs
    }

    // ------------------------------------------------------------------
    // One-Time Pre Keys â€” consumed once per X3DH handshake
    // ------------------------------------------------------------------

    /**
     * Generates a batch of one-time pre keys starting at [startId].
     * Returns the records (persisted locally) and the public-key map
     * that should be uploaded to the server.
     */
    fun generateOneTimePreKeys(startId: Int): OneTimePreKeyBatch {
        val records = mutableListOf<PreKeyRecord>()
        val publicKeys = mutableMapOf<Int, ByteArray>()

        for (i in 0 until PREKEY_BATCH_SIZE) {
            val keyId = startId + i
            val keyPair = Curve.generateKeyPair()
            val record = PreKeyRecord(keyId, keyPair)
            records.add(record)
            publicKeys[keyId] = keyPair.publicKey.serialize()
        }

        return OneTimePreKeyBatch(records, publicKeys)
    }

    /**
     * Stores generated pre keys in the local protocol store.
     */
    suspend fun persistPreKeys(records: List<PreKeyRecord>) {
        records.forEach { protocolStore.storePreKey(it.id, it) }
    }

    /**
     * Stores a signed pre key in the local protocol store.
     */
    suspend fun persistSignedPreKey(record: SignedPreKeyRecord) {
        protocolStore.storeSignedPreKey(record.id, record)
    }
}

/**
 * Holds the output of a one-time pre key generation: the full records
 * (kept locally, contain private keys) and the public-only map (uploaded
 * to the server via POST /v1/devices/prekeys).
 */
data class OneTimePreKeyBatch(
    val records: List<PreKeyRecord>,
    val publicKeysForServer: Map<Int, ByteArray>,
)
