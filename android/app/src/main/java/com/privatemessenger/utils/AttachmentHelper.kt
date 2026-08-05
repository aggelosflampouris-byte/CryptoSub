package com.privatemessenger.utils

import com.google.protobuf.ByteString
import com.privatemessenger.AppConstants
import com.privatemessenger.PrivateMessengerApp
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmtp.android.library.codecs.Attachment
import org.xmtp.android.library.codecs.AttachmentCodec
import org.xmtp.android.library.codecs.ContentTypeRemoteAttachment
import org.xmtp.android.library.codecs.RemoteAttachment
import org.xmtp.android.library.codecs.RemoteAttachmentCodec
import org.xmtp.android.library.SendOptions
import org.xmtp.android.library.Conversation
import java.io.File
import java.net.URL
import org.xmtp.android.library.Client
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HexUtils {
    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    fun decodeHex(str: String): ByteArray {
        require(str.length % 2 == 0) { "Must have an even length" }
        return str.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

/**
 * Shared helper for sending encrypted remote attachments via XMTP.
 *
 * Pipeline:
 *  1. Build an [Attachment] from raw bytes + metadata
 *  2. Encrypt via XMTP's [RemoteAttachment.encodeEncrypted]
 *  3. Upload the ciphertext to Catbox (keyless, anonymous, end-to-end safe)
 *  4. Send the [RemoteAttachment] metadata over XMTP so the receiver can fetch & decrypt
 *
 * @return The sent XMTP message ID, or null on failure.
 */
suspend fun sendEncryptedAttachment(
    app: PrivateMessengerApp,
    conversationId: String,
    bytes: ByteArray,
    mimeType: String,
    filename: String
): Pair<RemoteAttachment, String>? {
    val client = app.xmtpClient ?: return null
    val xmtpConversation = client.conversations.findConversation(conversationId) ?: return null

    val attachment = Attachment(
        filename = filename,
        mimeType = mimeType,
        data = ByteString.copyFrom(bytes)
    )
    val encryptedAttachment = RemoteAttachment.encodeEncrypted(attachment, AttachmentCodec())

    // Upload encrypted payload to Catbox (no API key required)
    val requestBody = okhttp3.MultipartBody.Builder()
        .setType(okhttp3.MultipartBody.FORM)
        .addFormDataPart("reqtype", "fileupload")
        .addFormDataPart(
            "fileToUpload",
            filename,
            encryptedAttachment.payload.toByteArray().toRequestBody("application/octet-stream".toMediaTypeOrNull())
        )
        .build()
    val request = Request.Builder()
        .url(AppConstants.ATTACHMENT_UPLOAD_URL)
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .post(requestBody)
        .build()
    val response = app.httpClient.newCall(request).execute()
    if (!response.isSuccessful) throw Exception("Upload failed: ${response.code}")
    val finalUrl = response.body?.string()?.trim() ?: throw Exception("Empty response from upload")

    val payloadMap = mapOf(
        "type" to "attachment",
        "url" to finalUrl,
        "contentDigest" to encryptedAttachment.contentDigest,
        "salt" to HexUtils.toHex(encryptedAttachment.salt.toByteArray()),
        "nonce" to HexUtils.toHex(encryptedAttachment.nonce.toByteArray()),
        "secret" to HexUtils.toHex(encryptedAttachment.secret.toByteArray()),
        "scheme" to "https://",
        "contentLength" to encryptedAttachment.payload.size(),
        "filename" to filename
    )
    val payloadJson = com.google.gson.Gson().toJson(payloadMap)

    val sentMessageId = when (xmtpConversation) {
        is Conversation.Dm -> xmtpConversation.dm.send(payloadJson)
        is Conversation.Group -> xmtpConversation.group.send(payloadJson)
    }

    val dummyRemoteAttachment = RemoteAttachment(
        url = URL(finalUrl),
        contentDigest = encryptedAttachment.contentDigest,
        salt = encryptedAttachment.salt,
        nonce = encryptedAttachment.nonce,
        secret = encryptedAttachment.secret,
        scheme = "https://",
        contentLength = encryptedAttachment.payload.size(),
        filename = filename
    )

    return Pair(dummyRemoteAttachment, sentMessageId)
}

/**
 * Downloads a RemoteAttachment from Catbox and decrypts it into a local File.
 */
suspend fun downloadAndSaveRemoteAttachment(
    client: Client,
    remoteAttachment: RemoteAttachment,
    context: Context
): File? = withContext(Dispatchers.IO) {
    try {
        // The SDK's load() is reified — must call with explicit type to get Attachment back
        @Suppress("UNCHECKED_CAST")
        val decodedAttachment = remoteAttachment.load<org.xmtp.android.library.codecs.Attachment>()
        if (decodedAttachment == null) return@withContext null
        // Use filesDir/attachments (persistent) instead of cacheDir (gets cleared by OS)
        val dir = File(context.filesDir, "attachments").also { it.mkdirs() }
        val safeFilename = remoteAttachment.filename?.ifBlank { null } ?: "attachment"
        val file = File(dir, safeFilename)
        file.writeBytes(decodedAttachment.data.toByteArray())
        file
    } catch (e: Exception) {
        android.util.Log.e("AttachmentHelper", "Failed to download/decrypt attachment", e)
        null
    }
}
