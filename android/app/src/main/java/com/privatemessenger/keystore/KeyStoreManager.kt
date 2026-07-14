package com.privatemessenger.keystore

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * KeyStoreManager manages hardware-backed cryptographic keys using the
 * Android Keystore system.
 *
 * Responsibilities:
 *   1. **SQLCipher passphrase**: Generates and stores a master AES-256 key
 *      in Android Keystore, derives the SQLCipher database passphrase from
 *      it. The passphrase never exists in plaintext on disk.
 *
 *   2. **Identity key encryption**: Encrypts/decrypts the Signal identity
 *      key pair's private half before writing it to SharedPreferences.
 *      The raw private key bytes exist in memory only while in use.
 *
 *   3. **Backup key derivation**: Derives an AES key from a user-provided
 *      passphrase (+ a random salt) for encrypting key exports. This key
 *      is never stored â€” the user must remember their passphrase.
 *
 * Security properties:
 *   - Keys stored in Android Keystore are hardware-backed on devices with
 *     a Secure Element or StrongBox (API 28+). On older devices they're
 *     software-backed but still isolated from the app's process memory.
 *   - Keystore keys cannot be exported â€” only used for encrypt/decrypt
 *     operations via the Keystore provider.
 *   - All encryption uses AES-256-GCM (authenticated encryption).
 */
class KeyStoreManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "pm_master_key"
        private const val IDENTITY_KEY_ALIAS = "pm_identity_key"
        private const val PREFS_FILE = "pm_encrypted_prefs"
        private const val PREF_DB_PASSPHRASE = "db_passphrase"
        private const val PREF_IDENTITY_KEY_PAIR = "identity_key_pair"
        private const val PREF_REGISTRATION_ID = "registration_id"
        private const val PREF_PROFILE_KEY = "profile_key"
        private const val PREF_ETH_PRIVATE_KEY = "eth_private_key"
        private const val ETH_KEY_ALIAS = "pm_eth_key"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12

        // PBKDF2 parameters for backup key derivation
        private const val PBKDF2_ITERATIONS = 600_000
        private const val PBKDF2_KEY_LENGTH = 256
        private const val SALT_LENGTH = 32
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

    // ------------------------------------------------------------------
    // SQLCipher database passphrase
    // ------------------------------------------------------------------

    /**
     * Returns the passphrase for the SQLCipher-encrypted Room database.
     * On first call, generates a random 32-byte passphrase, encrypts it
     * with a Keystore-backed key, and stores the ciphertext. Subsequent
     * calls decrypt and return the same passphrase.
     */
    fun getDatabasePassphrase(): ByteArray {
        val stored = encryptedPrefs.getString(PREF_DB_PASSPHRASE, null)
        if (stored != null) {
            return Base64.decode(stored, Base64.NO_WRAP)
        }

        // First run: generate a random passphrase
        val passphrase = ByteArray(32)
        SecureRandom().nextBytes(passphrase)

        encryptedPrefs.edit()
            .putString(PREF_DB_PASSPHRASE, Base64.encodeToString(passphrase, Base64.NO_WRAP))
            .apply()

        return passphrase
    }

    // ------------------------------------------------------------------
    // Identity key pair encryption
    // ------------------------------------------------------------------

    /**
     * Encrypts and stores the Signal identity key pair bytes.
     * Uses a dedicated Keystore key so the private identity key is
     * never written to disk in plaintext.
     */
    fun storeIdentityKeyPair(serializedKeyPair: ByteArray) {
        val key = getOrCreateKeystoreKey(IDENTITY_KEY_ALIAS)
        val encrypted = encryptWithKeystoreKey(key, serializedKeyPair)
        encryptedPrefs.edit()
            .putString(PREF_IDENTITY_KEY_PAIR, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    /**
     * Retrieves and decrypts the stored identity key pair, or null if
     * none has been stored yet (pre-registration).
     */
    fun loadIdentityKeyPair(): ByteArray? {
        val stored = encryptedPrefs.getString(PREF_IDENTITY_KEY_PAIR, null) ?: return null
        val encrypted = Base64.decode(stored, Base64.NO_WRAP)
        val key = getOrCreateKeystoreKey(IDENTITY_KEY_ALIAS)
        return decryptWithKeystoreKey(key, encrypted)
    }

    // ------------------------------------------------------------------
    // Ethereum Private Key
    // ------------------------------------------------------------------

    /**
     * Encrypts and stores the Ethereum private key (hex string).
     */
    fun storeEthereumPrivateKey(privateKeyHex: String) {
        val key = getOrCreateKeystoreKey(ETH_KEY_ALIAS)
        val encrypted = encryptWithKeystoreKey(key, privateKeyHex.toByteArray(Charsets.UTF_8))
        encryptedPrefs.edit()
            .putString(PREF_ETH_PRIVATE_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    /**
     * Retrieves and decrypts the stored Ethereum private key, or null if none.
     */
    fun getEthereumPrivateKey(): String? {
        val stored = encryptedPrefs.getString(PREF_ETH_PRIVATE_KEY, null) ?: return null
        val encrypted = Base64.decode(stored, Base64.NO_WRAP)
        val key = getOrCreateKeystoreKey(ETH_KEY_ALIAS)
        return String(decryptWithKeystoreKey(key, encrypted), Charsets.UTF_8)
    }

    // ------------------------------------------------------------------
    // Registration ID
    // ------------------------------------------------------------------

    fun storeRegistrationId(registrationId: Int) {
        encryptedPrefs.edit().putInt(PREF_REGISTRATION_ID, registrationId).apply()
    }

    fun loadRegistrationId(): Int {
        return encryptedPrefs.getInt(PREF_REGISTRATION_ID, -1)
    }

    // ------------------------------------------------------------------
    // Profile Key (Sealed Sender)
    // ------------------------------------------------------------------

    /**
     * Returns the 32-byte Profile Key used for Sealed Sender.
     * Generates and stores it securely on the first call.
     */
    fun getOrCreateProfileKey(): ByteArray {
        val stored = encryptedPrefs.getString(PREF_PROFILE_KEY, null)
        if (stored != null) {
            return Base64.decode(stored, Base64.NO_WRAP)
        }

        val profileKey = ByteArray(32)
        SecureRandom().nextBytes(profileKey)

        encryptedPrefs.edit()
            .putString(PREF_PROFILE_KEY, Base64.encodeToString(profileKey, Base64.NO_WRAP))
            .apply()

        return profileKey
    }

    // ------------------------------------------------------------------
    // Backup key derivation
    // ------------------------------------------------------------------

    /**
     * Derives an AES-256 key from a user-provided passphrase using
     * PBKDF2-HMAC-SHA256. The returned [BackupKey] includes the random
     * salt, which must be stored alongside the encrypted backup so the
     * key can be re-derived on restore.
     *
     * This key is never stored on the device â€” the user must remember
     * (or securely record) their backup passphrase.
     */
    fun deriveBackupKey(passphrase: String): BackupKey {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)

        val spec = PBEKeySpec(
            passphrase.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            PBKDF2_KEY_LENGTH,
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded

        return BackupKey(
            key = SecretKeySpec(keyBytes, "AES"),
            salt = salt,
        )
    }

    /**
     * Re-derives the backup key from a passphrase and an existing salt
     * (read from the backup file header).
     */
    fun rederiveBackupKey(passphrase: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(
            passphrase.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            PBKDF2_KEY_LENGTH,
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    // ------------------------------------------------------------------
    // Clear Keys (Logout)
    // ------------------------------------------------------------------

    fun clearAllKeys() {
        encryptedPrefs.edit().clear().apply()
        try {
            keyStore.deleteEntry(MASTER_KEY_ALIAS)
            keyStore.deleteEntry(IDENTITY_KEY_ALIAS)
            keyStore.deleteEntry(ETH_KEY_ALIAS)
        } catch (e: Exception) {
            // Ignore if aliases don't exist
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

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

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(spec)
        return generator.generateKey()
    }

    /**
     * AES-256-GCM encryption. The IV is prepended to the ciphertext
     * so it's available for decryption without separate storage.
     */
    private fun encryptWithKeystoreKey(key: SecretKey, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        // [IV (12 bytes)] [ciphertext + GCM tag]
        return iv + ciphertext
    }

    private fun decryptWithKeystoreKey(key: SecretKey, data: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }
}

data class BackupKey(
    val key: SecretKey,
    val salt: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupKey) return false
        return key == other.key && salt.contentEquals(other.salt)
    }

    override fun hashCode(): Int = 31 * key.hashCode() + salt.contentHashCode()
}
