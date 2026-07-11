package com.privatemessenger.crypto

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.SignedPreKeyStore
import org.signal.libsignal.protocol.groups.state.SenderKeyStore
import com.privatemessenger.data.local.dao.SignalDao
import com.privatemessenger.data.local.entity.IdentityKeyEntity
import com.privatemessenger.data.local.entity.PreKeyEntity
import com.privatemessenger.data.local.entity.SessionEntity
import com.privatemessenger.data.local.entity.SignedPreKeyEntity
import java.util.UUID

/**
 * Room-backed implementation of the four Signal Protocol store interfaces.
 *
 * libsignal requires a [SignalProtocolStore] to persist session state,
 * pre keys, signed pre keys, and identity keys between app restarts.
 * The default InMemorySignalProtocolStore is fine for tests but loses
 * everything on process death. This implementation persists all state
 * in the SQLCipher-encrypted Room database, so it survives restarts
 * and is protected at rest.
 *
 * Thread safety: Room DAOs are thread-safe. libsignal calls these
 * synchronously on whatever thread it's running on, so the DAO
 * methods here are blocking (Room allows this on non-main threads).
 */
class SignalProtocolStoreImpl(
    private val dao: SignalDao,
    private val localIdentityKeyPair: IdentityKeyPair,
    private val localRegistrationId: Int,
) : SignalProtocolStore {

    // ==================================================================
    // IdentityKeyStore
    // ==================================================================

    override fun getIdentityKeyPair(): IdentityKeyPair = localIdentityKeyPair

    override fun getLocalRegistrationId(): Int = localRegistrationId

    /**
     * Called by libsignal when a session is established and we learn a
     * peer's identity key. We store it and trust it on first use (TOFU).
     * If the key changes later, this returns false to signal a potential
     * identity change (the UI should warn the user).
     */
    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean {
        val existing = dao.getIdentityKey(address.name, address.deviceId)
        val entity = IdentityKeyEntity(
            userId = address.name,
            deviceId = address.deviceId,
            identityKey = identityKey.serialize(),
            trusted = true,
            firstSeen = System.currentTimeMillis(),
        )

        return if (existing != null) {
            // Key changed — save the new one but return true to indicate change
            val changed = !existing.identityKey.contentEquals(identityKey.serialize())
            if (changed) {
                dao.upsertIdentityKey(entity.copy(trusted = false))
            }
            changed
        } else {
            // First time seeing this identity — trust on first use
            dao.upsertIdentityKey(entity)
            false
        }
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction,
    ): Boolean {
        val existing = dao.getIdentityKey(address.name, address.deviceId)
            ?: return true // No record → trust on first use

        // Trusted if the key matches what we have stored AND it's marked trusted
        return existing.identityKey.contentEquals(identityKey.serialize()) && existing.trusted
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        val entity = dao.getIdentityKey(address.name, address.deviceId) ?: return null
        return IdentityKey(entity.identityKey, 0)
    }

    // ==================================================================
    // SessionStore
    // ==================================================================

    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        val entity = dao.getSession(address.name, address.deviceId)
            ?: return SessionRecord()
        return SessionRecord(entity.sessionRecord)
    }

    override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>): MutableList<SessionRecord> {
        val results = mutableListOf<SessionRecord>()
        for (address in addresses) {
            val entity = dao.getSession(address.name, address.deviceId)
            if (entity != null) {
                results.add(SessionRecord(entity.sessionRecord))
            }
        }
        return results
    }

    override fun getSubDeviceSessions(name: String): List<Int> {
        return dao.getSubDeviceSessions(name)
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        dao.upsertSession(
            SessionEntity(
                userId = address.name,
                deviceId = address.deviceId,
                sessionRecord = record.serialize(),
            )
        )
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean {
        return dao.getSession(address.name, address.deviceId) != null
    }

    override fun deleteSession(address: SignalProtocolAddress) {
        dao.deleteSession(address.name, address.deviceId)
    }

    override fun deleteAllSessions(name: String) {
        dao.deleteAllSessions(name)
    }

    // ==================================================================
    // PreKeyStore
    // ==================================================================

    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        val entity = dao.getPreKey(preKeyId)
            ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("No pre key: $preKeyId")
        return PreKeyRecord(entity.preKeyRecord)
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        dao.upsertPreKey(
            PreKeyEntity(keyId = preKeyId, preKeyRecord = record.serialize())
        )
    }

    override fun containsPreKey(preKeyId: Int): Boolean {
        return dao.getPreKey(preKeyId) != null
    }

    override fun removePreKey(preKeyId: Int) {
        dao.deletePreKey(preKeyId)
    }

    // ==================================================================
    // SignedPreKeyStore
    // ==================================================================

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        val entity = dao.getSignedPreKey(signedPreKeyId)
            ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("No signed pre key: $signedPreKeyId")
        return SignedPreKeyRecord(entity.signedPreKeyRecord)
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
        return dao.getAllSignedPreKeys().map { SignedPreKeyRecord(it.signedPreKeyRecord) }
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        dao.upsertSignedPreKey(
            SignedPreKeyEntity(keyId = signedPreKeyId, signedPreKeyRecord = record.serialize())
        )
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        return dao.getSignedPreKey(signedPreKeyId) != null
    }

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        dao.deleteSignedPreKey(signedPreKeyId)
    }

    // ==================================================================
    // SenderKeyStore (for group messaging via MLS/Sender Keys)
    // ==================================================================

    override fun storeSenderKey(
        sender: SignalProtocolAddress,
        distributionId: UUID,
        record: SenderKeyRecord,
    ) {
        // Group messaging sender keys — to be implemented in Phase 4
        // when group chat support is added.
    }

    override fun loadSenderKey(
        sender: SignalProtocolAddress,
        distributionId: java.util.UUID,
    ): SenderKeyRecord? {
        // Placeholder until group messaging is implemented
        return null
    }

    // ==================================================================
    // KyberPreKeyStore (Post-Quantum)
    // ==================================================================

    override fun loadKyberPreKey(preKeyId: Int): org.signal.libsignal.protocol.state.KyberPreKeyRecord {
        throw org.signal.libsignal.protocol.InvalidKeyIdException("No kyber key")
    }

    override fun loadKyberPreKeys(): MutableList<org.signal.libsignal.protocol.state.KyberPreKeyRecord> {
        return mutableListOf()
    }

    override fun storeKyberPreKey(preKeyId: Int, record: org.signal.libsignal.protocol.state.KyberPreKeyRecord) {
        // No-op for MVP
    }

    override fun containsKyberPreKey(preKeyId: Int): Boolean {
        return false
    }

    override fun markKyberPreKeyUsed(preKeyId: Int) {
        // No-op
    }
}
