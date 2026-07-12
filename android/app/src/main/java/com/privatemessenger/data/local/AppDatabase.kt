package com.privatemessenger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.privatemessenger.data.local.dao.ContactDao
import com.privatemessenger.data.local.dao.ConversationDao
import com.privatemessenger.data.local.dao.MessageDao
import com.privatemessenger.data.local.entity.ContactEntity
import com.privatemessenger.data.local.entity.ConversationEntity
import com.privatemessenger.data.local.entity.MessageEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * The single Room database for the entire app, encrypted at rest with
 * SQLCipher.
 *
 * The SQLCipher passphrase is derived from a hardware-backed AES-256 key
 * stored in Android Keystore (see [com.privatemessenger.keystore.KeyStoreManager]).
 * This means:
 *   - The database file on disk is AES-256-CBC encrypted.
 *   - It cannot be read without the Keystore key, which cannot be exported.
 *   - A physical device extraction yields only ciphertext.
 *
 * Tables:
 *   - messages          â€” decrypted message content (plaintext only here)
 *   - conversations     â€” chat list metadata
 *   - contacts          â€” discovered contacts + identity keys
 *   - signal_sessions   â€” Double Ratchet session state
 *   - signal_prekeys    â€” one-time prekey records
 *   - signal_signed_prekeys â€” signed prekey records
 *   - signal_identity_keys  â€” remote identity keys + trust status
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
        private const val DATABASE_NAME = "private_messenger.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the singleton database instance, creating it on first
         * call with SQLCipher encryption using the provided passphrase.
         *
         * @param context   Application context
         * @param passphrase  The database encryption passphrase, sourced
         *                    from [com.privatemessenger.keystore.KeyStoreManager.getDatabasePassphrase].
         *                    Must be the same on every call or the database
         *                    will fail to open.
         */
        fun getInstance(context: Context, passphrase: ByteArray): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context, passphrase).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context, passphrase: ByteArray): AppDatabase {
            // SupportFactory bridges SQLCipher into Room's SupportSQLiteOpenHelper
            val factory = SupportOpenHelperFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME,
            )
                .openHelperFactory(factory)
                // Allow queries on non-main threads (required for libsignal's
                // synchronous store calls â€” all crypto happens off the main thread)
                .allowMainThreadQueries()  // TODO: restrict to signal DAO only
                .build()
        }
    }
}
