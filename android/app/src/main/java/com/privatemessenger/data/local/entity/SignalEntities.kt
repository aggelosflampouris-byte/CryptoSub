package com.privatemessenger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Persists a libsignal SessionRecord for a specific remote device.
 * The session contains the current ratchet state â€” losing it means
 * the user has to re-establish the session from a fresh prekey bundle.
 */
@Entity(
    tableName = "signal_sessions",
    primaryKeys = ["user_id", "device_id"],
)
data class SessionEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "device_id")
    val deviceId: Int,

    @ColumnInfo(name = "session_record", typeAffinity = ColumnInfo.BLOB)
    val sessionRecord: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SessionEntity) return false
        return userId == other.userId && deviceId == other.deviceId
    }
    override fun hashCode(): Int = 31 * userId.hashCode() + deviceId
}

/**
 * Persists a one-time prekey (private + public). These are consumed
 * by incoming prekey messages â€” each is deleted after use.
 */
@Entity(tableName = "signal_prekeys")
data class PreKeyEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "key_id")
    val keyId: Int,

    @ColumnInfo(name = "pre_key_record", typeAffinity = ColumnInfo.BLOB)
    val preKeyRecord: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreKeyEntity) return false
        return keyId == other.keyId
    }
    override fun hashCode(): Int = keyId
}

/**
 * Persists a signed prekey. Unlike one-time prekeys, signed prekeys
 * are rotated rather than consumed â€” the old one is kept around
 * briefly so in-flight prekey messages can still be processed.
 */
@Entity(tableName = "signal_signed_prekeys")
data class SignedPreKeyEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "key_id")
    val keyId: Int,

    @ColumnInfo(name = "signed_pre_key_record", typeAffinity = ColumnInfo.BLOB)
    val signedPreKeyRecord: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignedPreKeyEntity) return false
        return keyId == other.keyId
    }
    override fun hashCode(): Int = keyId
}

/**
 * Persists a remote device's identity key and trust status.
 * Used by the TOFU (Trust On First Use) identity verification model.
 */
@Entity(
    tableName = "signal_identity_keys",
    primaryKeys = ["user_id", "device_id"],
)
data class IdentityKeyEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "device_id")
    val deviceId: Int,

    @ColumnInfo(name = "identity_key", typeAffinity = ColumnInfo.BLOB)
    val identityKey: ByteArray,

    /** False after a key change until the user re-verifies */
    @ColumnInfo(name = "trusted")
    val trusted: Boolean = true,

    @ColumnInfo(name = "first_seen")
    val firstSeen: Long = System.currentTimeMillis(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IdentityKeyEntity) return false
        return userId == other.userId && deviceId == other.deviceId
    }
    override fun hashCode(): Int = 31 * userId.hashCode() + deviceId
}
