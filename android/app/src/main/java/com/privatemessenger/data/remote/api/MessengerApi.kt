package com.privatemessenger.data.remote.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit interface for the Privacy Messenger backend REST API.
 * All encrypted messaging happens over WebSockets — these endpoints
 * are strictly for out-of-band operations like registration and
 * key exchange.
 */
interface MessengerApi {

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    @POST("/v1/register/start")
    suspend fun startRegistration(@Body request: StartRegistrationRequest)

    @POST("/v1/register/complete")
    suspend fun completeRegistration(@Body request: CompleteRegistrationRequest): CompleteRegistrationResponse

    @POST("/v1/logout")
    suspend fun logout(@Body request: LogoutRequest)

    // ------------------------------------------------------------------
    // Key Exchange (X3DH)
    // ------------------------------------------------------------------

    /**
     * Fetches a target device's prekey bundle to initiate a new session.
     * Consumes one of the target's one-time prekeys if available.
     * Requires Authorization: Bearer token header (injected by AuthInterceptor).
     */
    @GET("/v1/users/{userId}/devices/{deviceId}/bundle")
    suspend fun fetchPreKeyBundle(
        @Path("userId") userId: String,
        @Path("deviceId") deviceId: String
    ): PreKeyBundleResponse

    /**
     * Uploads new one-time prekeys to top up the server's supply.
     * Requires Authorization header.
     */
    @POST("/v1/devices/prekeys")
    suspend fun uploadPreKeys(@Body request: UploadPreKeysRequest)

    /**
     * Checks how many one-time prekeys are left on the server.
     * Requires Authorization header.
     */
    @GET("/v1/devices/prekeys/count")
    suspend fun getPreKeyCount(): PreKeyCountResponse

    // ------------------------------------------------------------------
    // Contact Sync
    // ------------------------------------------------------------------

    /**
     * Uploads a batch of phone numbers for discovery. The server computes
     * hashes transiently and returns the matched user IDs.
     */
    @POST("/v1/contacts/sync")
    suspend fun syncContacts(@Body request: ContactSyncRequest): ContactSyncResponse

    // ------------------------------------------------------------------
    // Media Attachments
    // ------------------------------------------------------------------

    /**
     * Requests a pre-signed AWS S3 URL for uploading an encrypted media blob.
     */
    @GET("/v1/attachments/upload-url")
    suspend fun getUploadUrl(): UploadUrlResponse
}

// --- Request/Response Models ---

data class StartRegistrationRequest(
    val phone_number: String
)

data class CompleteRegistrationRequest(
    val phone_number: String,
    val otp_code: String,
    val device_id: String,
    val identity_public_key: ByteArray,
    val signed_pre_key: ByteArray,
    val registration_id: Int
)

data class CompleteRegistrationResponse(
    val user_id: String,
    val session_token: String
)

data class LogoutRequest(
    val session_token: String
)

data class PreKeyBundleResponse(
    val identity_public_key: ByteArray,
    val signed_pre_key: ByteArray,
    val registration_id: Int,
    val one_time_pre_key_id: Int?,
    val one_time_pre_key: ByteArray?
)

data class UploadPreKeysRequest(
    val keys: Map<Int, ByteArray>
)

data class PreKeyCountResponse(
    val count: Int
)

data class ContactSyncRequest(
    val phone_numbers: List<String>
)

data class ContactMatch(
    val phone_number: String,
    val user_id: String
)

data class ContactSyncResponse(
    val contacts: List<ContactMatch>
)

data class UploadUrlResponse(
    val upload_url: String,
    val download_url: String
)
