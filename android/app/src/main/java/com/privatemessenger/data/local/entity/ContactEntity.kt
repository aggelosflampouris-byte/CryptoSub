package com.privatemessenger.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A known contact â€” discovered via hashed phone-number matching.
 * The raw phone number is never sent to the server; only the
 * HMAC-SHA256 hash (see server's HashPhoneNumber) is transmitted
 * for contact discovery.
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "phone_hash")
    val phoneHash: String,

    @ColumnInfo(name = "display_name")
    val displayName: String? = null,

    /** The contact's public identity key â€” used for safety number generation */
    @ColumnInfo(name = "identity_key", typeAffinity = ColumnInfo.BLOB)
    val identityKey: ByteArray? = null,

    /** The 32-byte Profile Key used to try and decrypt Sealed Sender envelopes from this contact */
    @ColumnInfo(name = "profile_key", typeAffinity = ColumnInfo.BLOB)
    val profileKey: ByteArray? = null,

    @ColumnInfo(name = "is_verified")
    val isVerified: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContactEntity) return false
        return userId == other.userId
    }

    override fun hashCode(): Int = userId.hashCode()
}
