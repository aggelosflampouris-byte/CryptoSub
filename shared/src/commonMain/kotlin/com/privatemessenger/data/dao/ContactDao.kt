package com.privatemessenger.data.dao

import androidx.room.*
import com.privatemessenger.data.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    @Query("SELECT * FROM contacts ORDER BY added_at DESC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE inbox_id = :inboxId")
    suspend fun getByInboxId(inboxId: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE ethereum_address = :address LIMIT 1")
    suspend fun getByEthereumAddress(address: String): ContactEntity?

    @Query("DELETE FROM contacts WHERE inbox_id = :inboxId")
    suspend fun delete(inboxId: String)
}
