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
        val prefs = application.getSharedPreferences("trace_prefs", android.content.Context.MODE_PRIVATE)
        fun trace(msg: String) {
            prefs.edit().putString("last_trace", msg).commit()
        }
        
        try {
            trace("1. Generating identity keys")
            // 1. Generate identity key pair
            val identityKeyPair = org.whispersystems.libsignal.util.KeyHelper.generateIdentityKeyPair()
            val registrationId = org.whispersystems.libsignal.util.KeyHelper.generateRegistrationId(false)

            // 2. Generate signed prekey
            trace("2. Generating signed prekey")
            val keyPair = org.whispersystems.libsignal.ecc.Curve.generateKeyPair()
            val signature = org.whispersystems.libsignal.ecc.Curve.calculateSignature(
                identityKeyPair.privateKey,
                keyPair.publicKey.serialize()
            )
            val timestamp = System.currentTimeMillis()
            val signedPreKey = org.whispersystems.libsignal.state.SignedPreKeyRecord(1, timestamp, keyPair, signature)

            // 3. Register with the server
            trace("3. Registering with server")
            val request = RegisterRequest(
                device_id = "1", // Hardcoded to 1 for the primary device
                identity_public_key = identityKeyPair.publicKey.serialize(),
                signed_pre_key = signedPreKey.serialize(),
                registration_id = registrationId,
                display_name = "" // We can prompt the user for this later
            )
            val response = apiClient.api.register(request)

            // 4. Save session token
            trace("4. Saving session token")
            apiClient.saveSessionToken(response.session_token)

            // 5. Initialize the app's crypto services now that we have the identity key
            trace("5. Initializing crypto services")
            withContext(Dispatchers.Main) {
                application.initCryptoAfterRegistration(identityKeyPair, registrationId)
            }

            // 6. Generate and upload one-time prekeys (requires the initialized KeyManager)
            trace("6. Generating one time prekeys")
            val appKeyManager = application.keyManager!!
            
            // Save the signed prekey locally now that the store exists
            trace("7. Persisting signed prekey to DB")
            appKeyManager.persistSignedPreKey(signedPreKey)

            trace("8. Generating prekey batch")
            val preKeyBatch = appKeyManager.generateOneTimePreKeys(startId = 1)
            
            trace("9. Persisting prekeys to DB")
            appKeyManager.persistPreKeys(preKeyBatch.records)

            trace("10. Uploading prekeys")
            val uploadRequest = UploadPreKeysRequest(keys = preKeyBatch.publicKeysForServer)
            val uploadResponse = apiClient.api.uploadPreKeys(uploadRequest)
            if (!uploadResponse.isSuccessful) {
                throw retrofit2.HttpException(uploadResponse)
            }

            // 7. Connect WebSocket
            trace("11. Connecting websocket")
            apiClient.webSocketManager.connect(response.session_token)

            trace("12. Success")
            prefs.edit().remove("last_trace").apply()
            Result.success(Unit)
        } catch (e: Throwable) {
            trace("Error: ${e.javaClass.simpleName} - ${e.message}")
            val errString = Log.getStackTraceString(e)
            Log.e("AuthRepository", "Failed to register: $errString")
            Result.failure(Exception(errString, e))
        }
    }
}
