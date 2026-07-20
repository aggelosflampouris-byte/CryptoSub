package com.privatemessenger.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.privatemessenger.data.dao.ContactDao
import com.privatemessenger.data.dao.ConversationDao
import com.privatemessenger.data.dao.MessageDao
import com.privatemessenger.data.entity.ContactEntity
import com.privatemessenger.data.entity.ConversationEntity
import com.privatemessenger.data.entity.MessageEntity

/**
 * Abstract Room database definition shared across all platforms.
 *
 * The concrete instance is created by the platform-specific builder:
 *   - Android: [androidMain] AppDatabase.android.kt — uses SQLCipher
 *   - iOS:     [iosMain]     AppDatabase.ios.kt     — uses bundled SQLite
 *               (hardware encryption via iOS Secure Enclave / NSFileProtectionComplete)
 */
@Database(
    entities = [
        MessageEntity::class,
        ConversationEntity::class,
        ContactEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun contactDao(): ContactDao

    companion object {
        const val DATABASE_NAME = "cryptosub.db"
    }
}
