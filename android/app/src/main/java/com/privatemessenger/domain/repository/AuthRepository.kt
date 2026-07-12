package com.privatemessenger.domain.repository

import android.util.Log
import com.privatemessenger.PrivateMessengerApp
import com.privatemessenger.crypto.KeyManager
import com.privatemessenger.data.remote.ApiClient
import com.privatemessenger.data.remote.api.CompleteRegistrationRequest
import com.privatemessenger.data.remote.api.StartRegistrationRequest
import com.privatemessenger.data.remote.api.UploadPreKeysRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AuthRepository coordinates the multi-step registration flow:
 * 1. Request OTP via the REST API.
 * 2. Verify OTP.
 * 3. On success, trigger the [KeyManager] to generate the identity key
 *    pair, signed prekey, and a batch of one-time prekeys.
 * 4. Send the *public* halves of those keys to the server.
 * 5. Initialize the application's crypto state.
 */
class AuthRepository(
    private val apiClient: ApiClient,
    private val application: PrivateMessengerApp,
) {

    suspend fun startRegistration(phoneNumber: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = StartRegistrationRequest(phone_number = phoneNumber)
            apiClient.api.startRegistration(request)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to start registration", e)
            Result.failure(e)
        }
    }

    suspend fun completeRegistration(phoneNumber: String, otpCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // We temporarily use an ephemeral KeyManager connected to the uninitialized
            // database just for key generation. The real one is injected into the app later.
            // But since KeyManager requires SignalProtocolStoreImpl, which requires IdentityKeyPair,
            // we have a chicken-and-egg problem.
            // Actually, we generate the IdentityKeyPair first, independent of the store.
            
            // 1. Generate identity key pair
            // 1. Generate identity key pair using direct signal library calls
            // because KeyManager requires an initialized protocol store which we don't have yet.
            val identityKeyPair = org.signal.libsignal.protocol.IdentityKeyPair.generate()
            val registrationId = org.signal.libsignal.protocol.util.KeyHelper.generateRegistrationId(false)

            // 2. Generate signed prekey
            val keyPair = org.signal.libsignal.protocol.ecc.Curve.generateKeyPair()
            val signature = org.signal.libsignal.protocol.ecc.Curve.calculateSignature(
                identityKeyPair.privateKey,
                keyPair.publicKey.serialize()
            )
            val timestamp = System.currentTimeMillis()
            val signedPreKey = org.signal.libsignal.protocol.state.SignedPreKeyRecord(1, timestamp, keyPair, signature)

            // 3. Complete registration with the server
            val request = CompleteRegistrationRequest(
                phone_number = phoneNumber,
                otp_code = otpCode,
                device_id = "1", // Hardcoded to 1 for the primary device
                identity_public_key = identityKeyPair.publicKey.serialize(),
                signed_pre_key = signedPreKey.serialize(),
                registration_id = registrationId
            )
            val response = apiClient.api.completeRegistration(request)

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
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to complete registration", e)
            Result.failure(e)
        }
    }
}
