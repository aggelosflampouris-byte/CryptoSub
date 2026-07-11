package com.privatemessenger.domain.repository

import android.annotation.SuppressLint
import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import com.privatemessenger.data.local.dao.ContactDao
import com.privatemessenger.data.local.entity.ContactEntity
import com.privatemessenger.data.remote.ApiClient
import com.privatemessenger.data.remote.api.ContactSyncRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Handles syncing local device contacts with the privacy-preserving backend.
 */
class ContactRepository(
    private val context: Context,
    private val apiClient: ApiClient,
    private val contactDao: ContactDao
) {

    /**
     * Reads all device contacts, extracts E.164-like phone numbers, and sends them
     * to the backend for transient hashing and matching.
     * Matches are saved into the local encrypted Room database.
     *
     * Requires READ_CONTACTS permission to be granted beforehand.
     */
    @SuppressLint("Range")
    suspend fun syncContacts() = withContext(Dispatchers.IO) {
        try {
            val phoneNumbers = mutableSetOf<String>()
            val phoneToName = mutableMapOf<String, String>()

            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                null,
                null,
                null
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val rawNumber = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    val name = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                    
                    // Simple normalization: strip non-digits, prepend + if it had one
                    val normalized = normalizePhoneNumber(rawNumber)
                    if (normalized.isNotBlank()) {
                        phoneNumbers.add(normalized)
                        phoneToName[normalized] = name
                    }
                }
            }

            if (phoneNumbers.isEmpty()) return@withContext

            // Chunk the sync requests to avoid hitting server limits
            val chunks = phoneNumbers.chunked(1000)
            val matchedEntities = mutableListOf<ContactEntity>()

            for (chunk in chunks) {
                val response = apiClient.api.syncContacts(ContactSyncRequest(chunk))
                for (match in response.contacts) {
                    // Compute a local hash for storage so we don't store plaintext E.164 
                    // in our database either, adding defense-in-depth even though Room is encrypted.
                    val localHash = computeLocalHash(match.phone_number)
                    val name = phoneToName[match.phone_number]

                    matchedEntities.add(
                        ContactEntity(
                            userId = match.user_id,
                            phoneHash = localHash,
                            displayName = name
                        )
                    )
                }
            }

            // Upsert all matched contacts
            if (matchedEntities.isNotEmpty()) {
                contactDao.upsertAll(matchedEntities)
                Log.d("ContactRepository", "Synced ${matchedEntities.size} contacts")
            }

        } catch (e: Exception) {
            Log.e("ContactRepository", "Failed to sync contacts", e)
        }
    }

    private fun normalizePhoneNumber(raw: String?): String {
        if (raw == null) return ""
        val hasPlus = raw.startsWith("+")
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 7) return "" // Ignore invalid/short numbers
        return if (hasPlus) "+$digits" else digits
    }

    private fun computeLocalHash(phone: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(phone.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
