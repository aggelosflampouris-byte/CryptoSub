package com.privatemessenger.data

import android.content.Context
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Android platform builder for [AppDatabase].
 * Opens the Room database encrypted with SQLCipher using a hardware-backed passphrase
 * from [com.privatemessenger.platform.KeyVault].
 */
fun buildAndroidDatabase(context: Context, passphrase: ByteArray): AppDatabase {
    // Load native SQLCipher library
    System.loadLibrary("sqlcipher")
    val factory = SupportOpenHelperFactory(passphrase)

    return Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        AppDatabase.DATABASE_NAME,
    )
        .openHelperFactory(factory)
        .build()
}
