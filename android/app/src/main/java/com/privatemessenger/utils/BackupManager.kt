package com.privatemessenger.utils

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.privatemessenger.data.local.entity.ConversationEntity

object BackupManager {
    private const val ITERATIONS = 65536
    private const val KEY_LENGTH = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val SALT_LENGTH = 16
    private const val TAG_LENGTH = 128

    fun encryptBackup(json: String, password: String): String {
        val salt = ByteArray(SALT_LENGTH)
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(salt)
        SecureRandom().nextBytes(iv)

        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val parameterSpec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)

        val cipherText = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        
        // Format: salt:iv:cipherText
        return Base64.encodeToString(salt, Base64.NO_WRAP) + ":" +
               Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
               Base64.encodeToString(cipherText, Base64.NO_WRAP)
    }

    fun decryptBackup(encryptedPayload: String, password: String): String {
        val parts = encryptedPayload.split(":")
        if (parts.size != 3) throw IllegalArgumentException("Invalid backup format")

        val salt = Base64.decode(parts[0], Base64.NO_WRAP)
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipherText = Base64.decode(parts[2], Base64.NO_WRAP)

        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val parameterSpec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)

        val plainText = cipher.doFinal(cipherText)
        return String(plainText, Charsets.UTF_8)
    }

    data class BackupMetadata(
        val version: Int = 1,
        val conversations: List<ConversationBackupData>
    )

    data class ConversationBackupData(
        val id: String,
        val displayName: String?,
        val profilePictureUri: String?
    )

    fun createBackupPayload(conversations: List<ConversationEntity>): String {
        val dataList = conversations.map { 
            ConversationBackupData(it.id, it.displayName, it.profilePictureUri) 
        }
        val metadata = BackupMetadata(version = 1, conversations = dataList)
        return Gson().toJson(metadata)
    }

    fun parseBackupPayload(json: String): BackupMetadata {
        return Gson().fromJson(json, BackupMetadata::class.java)
    }
}
