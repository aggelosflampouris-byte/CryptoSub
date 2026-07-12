package com.privatemessenger.domain.repository

import android.util.Log
import com.privatemessenger.PrivateMessengerApp
import com.privatemessenger.crypto.KeyManager
import com.privatemessenger.data.remote.ApiClient
import com.privatemessenger.data.remote.api.RegisterRequest
import com.privatemessenger.data.remote.api.UploadPreKeysRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AuthRepository coordinates the wallet-based registration flow:
 * 1. Trigger KeyManager to generate identity key pair and signed prekey
 * 2. Send the public halves of those keys to the server
 * 3. Save the returned session token
 * 4. Generate a batch of one-time prekeys and upload them
 */
class AuthRepository(
    private val apiClient: ApiClient,
    private val application: PrivateMessengerApp,
) {

    suspend fun register(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Generate identity key pair
            val identityKeyPair = org.whispersystems.libsignal.util.KeyHelper.generateIdentityKeyPair()
            val registrationId = org.whispersystems.libsignal.util.KeyHelper.generateRegistrationId(false)

            // 2. Generate signed prekey
            val keyPair = org.whispersystems.libsignal.ecc.Curve.generateKeyPair()
            val signature = org.whispersystems.libsignal.ecc.Curve.calculateSignature(
                identityKeyPair.privateKey,
                keyPair.publicKey.serialize()
            )
            val timestamp = System.currentTimeMillis()
            val signedPreKey = org.whispersystems.libsignal.state.SignedPreKeyRecord(1, timestamp, keyPair, signature)

            // 3. Register with the server
            val request = RegisterRequest(
                device_id = "1", // Hardcoded to 1 for the primary device
                identity_public_key = identityKeyPair.publicKey.serialize(),
                signed_pre_key = signedPreKey.serialize(),
                registration_id = registrationId,
                display_name = "" // We can prompt the user for this later
            )
            val response = apiClient.api.register(request)

            // 4. Save session token
            apiClient.saveSessionToken(response.session_token)

            // 5. Initialize the app's crypto services now that we have the identity key
            withContext(Dispatchers.Main) {
                application.initCryptoAfterRegistration(identityKeyPair, registrationId)
            }

            // 6. Generate and upload one-time prekeys (requires the initialized KeyManager)
            val appKeyManager = application.keyManager!!
            
            // Save the signed prekey locally now that the store exists
            appKeyManager.persistSignedPreKey(signedPreKey)

            val preKeyBatch = appKeyManager.generateOneTimePreKeys(startId = 1)
            appKeyManager.persistPreKeys(preKeyBatch.records)

            val uploadRequest = UploadPreKeysRequest(keys = preKeyBatch.publicKeysForServer)
            apiClient.api.uploadPreKeys(uploadRequest)

            // 7. Connect WebSocket
            apiClient.webSocketManager.connect(response.session_token)

            Result.success(Unit)
        } catch (e: Throwable) {
            val errString = Log.getStackTraceString(e)
            Log.e("AuthRepository", "Failed to register: $errString")
            Result.failure(Exception(errString, e))
        }
    }
}
