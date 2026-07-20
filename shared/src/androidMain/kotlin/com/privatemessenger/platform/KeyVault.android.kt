package com.privatemessenger.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android actual for [KeyVault].
 * Secures keys in the Android Hardware Keystore and stores encrypted blobs in
 * EncryptedSharedPreferences (AES-256-GCM at rest, never exported).
 */
actual class KeyVault(private val context: Context) {

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ETH_KEY_ALIAS = "pm_eth_key"
        const val DB_PASS_KEY_ALIAS = "pm_db_key"
        const val PREFS_FILE = "pm_encrypted_prefs"
        const val PREF_DB_PASSPHRASE = "db_passphrase"
        const val PREF_ETH_PRIVATE_KEY = "eth_private_key"
        const val GCM_TAG_LENGTH = 128
        const val GCM_IV_LENGTH = 12
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    actual fun getDatabasePassphrase(): ByteArray {
        val stored = encryptedPrefs.getString(PREF_DB_PASSPHRASE, null)
        if (stored != null) return Base64.decode(stored, Base64.NO_WRAP)

        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        encryptedPrefs.edit()
            .putString(PREF_DB_PASSPHRASE, Base64.encodeToString(passphrase, Base64.NO_WRAP))
            .apply()
        return passphrase
    }

    actual fun storeEthereumPrivateKey(privateKeyHex: String) {
        val key = getOrCreateKeystoreKey(ETH_KEY_ALIAS)
        val encrypted = encryptAesGcm(key, privateKeyHex.toByteArray(Charsets.UTF_8))
        encryptedPrefs.edit()
            .putString(PREF_ETH_PRIVATE_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    actual fun getEthereumPrivateKey(): String? {
        val stored = encryptedPrefs.getString(PREF_ETH_PRIVATE_KEY, null) ?: return null
        val encrypted = Base64.decode(stored, Base64.NO_WRAP)
        val key = getOrCreateKeystoreKey(ETH_KEY_ALIAS)
        return String(decryptAesGcm(key, encrypted), Charsets.UTF_8)
    }

    actual fun clear() {
        encryptedPrefs.edit().clear().apply()
        if (keyStore.containsAlias(ETH_KEY_ALIAS)) keyStore.deleteEntry(ETH_KEY_ALIAS)
        if (keyStore.containsAlias(DB_PASS_KEY_ALIAS)) keyStore.deleteEntry(DB_PASS_KEY_ALIAS)
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private fun getOrCreateKeystoreKey(alias: String): SecretKey {
        if (keyStore.containsAlias(alias)) {
            return (keyStore.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    /** AES-256-GCM encrypt. Prepends 12-byte IV to ciphertext. */
    private fun encryptAesGcm(key: SecretKey, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(plaintext)
    }

    /** AES-256-GCM decrypt. Reads 12-byte IV prefix. */
    private fun decryptAesGcm(key: SecretKey, data: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(data.copyOfRange(GCM_IV_LENGTH, data.size))
    }
}
