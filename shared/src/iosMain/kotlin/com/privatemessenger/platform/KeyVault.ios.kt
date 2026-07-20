package com.privatemessenger.platform

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.Security.*

/**
 * iOS actual for [KeyVault].
 * Uses the iOS Keychain (Security.framework) for hardware-backed key storage.
 * All values are stored as generic password items under a dedicated service name.
 */
actual class KeyVault {

    private companion object {
        const val SERVICE = "com.privatemessenger.keyvault"
        const val ACCOUNT_ETH_KEY = "eth_private_key"
        const val ACCOUNT_DB_PASS = "db_passphrase"
    }

    actual fun getDatabasePassphrase(): ByteArray {
        val stored = readKeychain(ACCOUNT_DB_PASS)
        if (stored != null) return stored

        val passphrase = ByteArray(32)
        val nsData = NSMutableData.create(length = 32.toULong())!!
        SecRandomCopyBytes(kSecRandomDefault, 32.toULong(), nsData.mutableBytes)
        val bytes = nsData.bytes!!.reinterpret<ByteVar>()
        for (i in 0 until 32) passphrase[i] = bytes[i]

        writeKeychain(ACCOUNT_DB_PASS, passphrase)
        return passphrase
    }

    actual fun storeEthereumPrivateKey(privateKeyHex: String) {
        writeKeychain(ACCOUNT_ETH_KEY, privateKeyHex.encodeToByteArray())
    }

    actual fun getEthereumPrivateKey(): String? {
        return readKeychain(ACCOUNT_ETH_KEY)?.decodeToString()
    }

    actual fun clear() {
        deleteKeychain(ACCOUNT_ETH_KEY)
        deleteKeychain(ACCOUNT_DB_PASS)
    }

    // ── Keychain helpers ────────────────────────────────────────────────────

    private fun writeKeychain(account: String, data: ByteArray) {
        // Delete any existing item first (upsert pattern)
        deleteKeychain(account)

        val nsData = data.toNSData()
        val query = CFDictionaryCreateMutable(null, 0, null, null)!!
        CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionarySetValue(query, kSecAttrService, CFBridgingRetain(NSString.stringWithString(SERVICE)))
        CFDictionarySetValue(query, kSecAttrAccount, CFBridgingRetain(NSString.stringWithString(account)))
        CFDictionarySetValue(query, kSecValueData, CFBridgingRetain(nsData))
        // NSFileProtectionComplete — data inaccessible when device is locked
        CFDictionarySetValue(query, kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
        SecItemAdd(query, null)
    }

    private fun readKeychain(account: String): ByteArray? {
        val query = CFDictionaryCreateMutable(null, 0, null, null)!!
        CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionarySetValue(query, kSecAttrService, CFBridgingRetain(NSString.stringWithString(SERVICE)))
        CFDictionarySetValue(query, kSecAttrAccount, CFBridgingRetain(NSString.stringWithString(account)))
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)

        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        if (status != errSecSuccess) return null

        val nsData = CFBridgingRelease(result.value) as? NSData ?: return null
        return nsData.toByteArray()
    }

    private fun deleteKeychain(account: String) {
        val query = CFDictionaryCreateMutable(null, 0, null, null)!!
        CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionarySetValue(query, kSecAttrService, CFBridgingRetain(NSString.stringWithString(SERVICE)))
        CFDictionarySetValue(query, kSecAttrAccount, CFBridgingRetain(NSString.stringWithString(account)))
        SecItemDelete(query)
    }

    // ── ByteArray <-> NSData helpers ────────────────────────────────────────

    private fun ByteArray.toNSData(): NSData = memScoped {
        val ptr = allocArray<ByteVar>(size)
        for (i in indices) ptr[i] = this@toNSData[i]
        NSData.create(bytes = ptr, length = size.toULong())
    }

    private fun NSData.toByteArray(): ByteArray {
        val bytes = this.bytes?.reinterpret<ByteVar>() ?: return ByteArray(0)
        return ByteArray(this.length.toInt()) { bytes[it] }
    }
}
