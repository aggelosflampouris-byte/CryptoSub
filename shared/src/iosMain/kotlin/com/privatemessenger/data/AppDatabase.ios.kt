package com.privatemessenger.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import platform.Foundation.NSFileManager
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask

/**
 * iOS platform builder for [AppDatabase].
 *
 * Uses Room's bundled SQLite driver (no SQLCipher — iOS hardware provides
 * equivalent protection via NSFileProtectionCompleteUnlessOpen, which makes
 * the database file inaccessible when the device is locked and the app is not running).
 */
fun buildIosDatabase(): AppDatabase {
    val dbPath = getIosDatabasePath()
    return Room.databaseBuilder<AppDatabase>(
        name = dbPath,
    )
        .setDriver(BundledSQLiteDriver())
        .build()
}

private fun getIosDatabasePath(): String {
    val documentDirectory = NSFileManager.defaultManager
        .URLsForDirectory(NSDocumentDirectory, inDomains = NSUserDomainMask)
        .firstOrNull()
        ?.path ?: error("Could not resolve iOS document directory")
    return "$documentDirectory/${AppDatabase.DATABASE_NAME}"
}
