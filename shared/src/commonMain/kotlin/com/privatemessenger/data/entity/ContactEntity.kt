package com.privatemessenger.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A discovered contact — stores their XMTP inbox ID and Ethereum address.
 * Defined in commonMain — Room 3.0 annotations are KMP-compatible.
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    @ColumnInfo(name = "inbox_id")
    val inboxId: String,

    @ColumnInfo(name = "ethereum_address")
    val ethereumAddress: String,

    @ColumnInfo(name = "display_name")
    val displayName: String? = null,

    @ColumnInfo(name = "avatar_uri")
    val avatarUri: String? = null,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis(),
)
