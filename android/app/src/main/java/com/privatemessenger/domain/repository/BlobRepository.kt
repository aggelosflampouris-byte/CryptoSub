package com.privatemessenger.domain.repository

import com.privatemessenger.data.remote.api.MessengerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Handles uploading and downloading large media blobs directly to/from AWS S3
 * via pre-signed URLs.
 */
class BlobRepository(
    private val api: MessengerApi,
    private val okHttpClient: OkHttpClient
) {

    class UploadResult(val downloadUrl: String)

    /**
     * Fetches a pre-signed PUT URL from our server, then uploads the encrypted
     * ciphertext directly to S3.
     */
    suspend fun uploadEncryptedBlob(ciphertext: ByteArray, mimeType: String): UploadResult = withContext(Dispatchers.IO) {
        // 1. Get pre-signed URLs
        val urls = api.getUploadUrl()

        // 2. PUT to S3
        val body = ciphertext.toRequestBody(mimeType.toMediaTypeOrNull())
        val request = Request.Builder()
            .url(urls.upload_url)
            .put(body)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Failed to upload blob to S3: ${response.code}")
            }
        }

        UploadResult(urls.download_url)
    }

    /**
     * Downloads an encrypted blob from a pre-signed GET URL (or public URL).
     */
    suspend fun downloadEncryptedBlob(downloadUrl: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(downloadUrl)
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Failed to download blob from S3: ${response.code}")
            }
            response.body?.bytes() ?: throw RuntimeException("Empty body")
        }
    }
}
