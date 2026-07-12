package com.privatemessenger.data.local.dao

import androidx.room.*
import com.privatemessenger.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(contacts: List<ContactEntity>)

    @Query("SELECT * FROM contacts ORDER BY display_name ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE user_id = :userId")
    suspend fun getContact(userId: String): ContactEntity?

    @Query("UPDATE contacts SET identity_key = :identityKey WHERE user_id = :userId")
    suspend fun updateIdentityKey(userId: String, identityKey: ByteArray)

    @Query("UPDATE contacts SET is_verified = :verified WHERE user_id = :userId")
    suspend fun setVerified(userId: String, verified: Boolean)

    @Query("DELETE FROM contacts WHERE user_id = :userId")
    suspend fun delete(userId: String)
}
