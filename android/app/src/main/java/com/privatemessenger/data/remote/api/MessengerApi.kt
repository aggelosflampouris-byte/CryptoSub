package com.privatemessenger.data.remote.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit interface for the Privacy Messenger backend REST API.
 * All encrypted messaging happens over WebSockets â€” these endpoints
 * are strictly for out-of-band operations like registration and
 * key exchange.
 */
interface MessengerApi {

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    @POST("/v1/register")
    suspend fun register(@Body request: RegisterRequest): RegisterResponse

    @POST("/v1/logout")
    suspend fun logout(@Body request: LogoutRequest): retrofit2.Response<Unit>

    // ------------------------------------------------------------------
    // Identity / Discovery
    // ------------------------------------------------------------------

    @GET("/v1/users/{userId}")
    suspend fun getUser(@Path("userId") userId: String): UserResponse

    // ------------------------------------------------------------------
    // Key Exchange (X3DH)
    // ------------------------------------------------------------------

    @GET("/v1/users/{userId}/devices/{deviceId}/bundle")
    suspend fun fetchPreKeyBundle(
        @Path("userId") userId: String,
        @Path("deviceId") deviceId: String
    ): PreKeyBundleResponse

    @POST("/v1/devices/prekeys")
    suspend fun uploadPreKeys(@Body request: UploadPreKeysRequest): retrofit2.Response<Unit>

    @GET("/v1/devices/prekeys/count")
    suspend fun getPreKeyCount(): PreKeyCountResponse

    // ------------------------------------------------------------------
    // Media Attachments
    // ------------------------------------------------------------------

    @GET("/v1/attachments/upload-url")
    suspend fun getUploadUrl(): UploadUrlResponse
}

// --- Request/Response Models ---

data class RegisterRequest(
    val device_id: String,
    val identity_public_key: ByteArray,
    val signed_pre_key: ByteArray,
    val registration_id: Int,
    val display_name: String
)

data class RegisterResponse(
    val user_id: String,
    val session_token: String
)

data class LogoutRequest(
    val session_token: String
)

data class UserResponse(
    val user_id: String
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

data class UploadUrlResponse(
    val upload_url: String,
    val download_url: String
)
