package com.privatemessenger.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager private constructor(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "app_secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _isBiometricLockEnabled = MutableStateFlow(
        sharedPreferences.getBoolean(KEY_BIOMETRIC_LOCK, false)
    )
    val isBiometricLockEnabled: StateFlow<Boolean> = _isBiometricLockEnabled.asStateFlow()

    private val _isScreenshotProtectionEnabled = MutableStateFlow(
        sharedPreferences.getBoolean(KEY_SCREENSHOT_PROTECTION, false)
    )
    val isScreenshotProtectionEnabled: StateFlow<Boolean> = _isScreenshotProtectionEnabled.asStateFlow()

    fun setBiometricLockEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply()
        _isBiometricLockEnabled.value = enabled
    }

    fun setScreenshotProtectionEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SCREENSHOT_PROTECTION, enabled).apply()
        _isScreenshotProtectionEnabled.value = enabled
    }

    companion object {
        private const val KEY_BIOMETRIC_LOCK = "biometric_lock_enabled"
        private const val KEY_SCREENSHOT_PROTECTION = "screenshot_protection_enabled"

        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
