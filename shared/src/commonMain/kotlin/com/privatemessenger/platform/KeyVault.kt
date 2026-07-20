package com.privatemessenger.platform

/**
 * Platform-agnostic interface for secure key storage.
 *
 * Android actual: Android Keystore + EncryptedSharedPreferences
 * iOS actual:     iOS Keychain (Security.framework)
 */
expect class KeyVault {
    /** Returns (or generates on first call) the database encryption passphrase. */
    fun getDatabasePassphrase(): ByteArray

    /** Encrypts and persists the Ethereum private key (hex). */
    fun storeEthereumPrivateKey(privateKeyHex: String)

    /** Retrieves and decrypts the Ethereum private key, or null if not set. */
    fun getEthereumPrivateKey(): String?

    /** Clears all stored keys (used on logout / account wipe). */
    fun clear()
}
