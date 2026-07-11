package com.privatemessenger.data.local.dao

import androidx.room.*
import com.privatemessenger.data.local.entity.IdentityKeyEntity
import com.privatemessenger.data.local.entity.PreKeyEntity
import com.privatemessenger.data.local.entity.SessionEntity
import com.privatemessenger.data.local.entity.SignedPreKeyEntity

/**
 * DAO for all Signal Protocol state tables. Used by
 * [com.privatemessenger.crypto.SignalProtocolStoreImpl] to persist
 * sessions, prekeys, and identity keys.
 *
 * NOTE: These methods are intentionally NOT suspend functions because
 * libsignal's store interfaces require synchronous return values.
 * Room allows blocking queries on non-main threads, which is where
 * libsignal always calls from. If this ever becomes a problem, the
 * calls can be wrapped in runBlocking at the store level.
 */
@Dao
interface SignalDao {

    // --- Sessions ---

    @Query("SELECT * FROM signal_sessions WHERE user_id = :userId AND device_id = :deviceId")
    fun getSession(userId: String, deviceId: Int): SessionEntity?

    @Upsert
    fun upsertSession(session: SessionEntity)

    @Query("SELECT DISTINCT device_id FROM signal_sessions WHERE user_id = :userId")
    fun getSubDeviceSessions(userId: String): List<Int>

    @Query("DELETE FROM signal_sessions WHERE user_id = :userId AND device_id = :deviceId")
    fun deleteSession(userId: String, deviceId: Int)

    @Query("DELETE FROM signal_sessions WHERE user_id = :userId")
    fun deleteAllSessions(userId: String)

    // --- PreKeys ---

    @Query("SELECT * FROM signal_prekeys WHERE key_id = :keyId")
    fun getPreKey(keyId: Int): PreKeyEntity?

    @Upsert
    fun upsertPreKey(preKey: PreKeyEntity)

    @Query("DELETE FROM signal_prekeys WHERE key_id = :keyId")
    fun deletePreKey(keyId: Int)

    @Query("SELECT COUNT(*) FROM signal_prekeys")
    fun countPreKeys(): Int

    // --- Signed PreKeys ---

    @Query("SELECT * FROM signal_signed_prekeys WHERE key_id = :keyId")
    fun getSignedPreKey(keyId: Int): SignedPreKeyEntity?

    @Query("SELECT * FROM signal_signed_prekeys")
    fun getAllSignedPreKeys(): List<SignedPreKeyEntity>

    @Upsert
    fun upsertSignedPreKey(signedPreKey: SignedPreKeyEntity)

    @Query("DELETE FROM signal_signed_prekeys WHERE key_id = :keyId")
    fun deleteSignedPreKey(keyId: Int)

    // --- Identity Keys ---

    @Query("SELECT * FROM signal_identity_keys WHERE user_id = :userId AND device_id = :deviceId")
    fun getIdentityKey(userId: String, deviceId: Int): IdentityKeyEntity?

    @Upsert
    fun upsertIdentityKey(identityKey: IdentityKeyEntity)

    @Query("UPDATE signal_identity_keys SET trusted = :trusted WHERE user_id = :userId AND device_id = :deviceId")
    fun setIdentityTrusted(userId: String, deviceId: Int, trusted: Boolean)
}
