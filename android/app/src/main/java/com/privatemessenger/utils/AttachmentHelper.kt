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
        .post(requestBody)
        .build()
    val response = app.httpClient.newCall(request).execute()
    if (!response.isSuccessful) throw Exception("Upload failed: ${response.code}")
    val finalUrl = response.body?.string()?.trim() ?: throw Exception("Empty response from upload")

    val remoteAttachment = RemoteAttachment(
        url = URL(finalUrl),
        contentDigest = encryptedAttachment.contentDigest,
        salt = encryptedAttachment.salt,
        nonce = encryptedAttachment.nonce,
        secret = encryptedAttachment.secret,
        scheme = "https://",
        contentLength = encryptedAttachment.payload.size(),
        filename = filename
    )

    val sentMessageId = when (xmtpConversation) {
        is Conversation.Dm -> xmtpConversation.dm.send(remoteAttachment, options = SendOptions(contentType = ContentTypeRemoteAttachment))
        is Conversation.Group -> xmtpConversation.group.send(remoteAttachment, options = SendOptions(contentType = ContentTypeRemoteAttachment))
    }

    return Pair(remoteAttachment, sentMessageId)
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
        val decodedAttachment = RemoteAttachmentCodec.load(remoteAttachment, client)
        val file = File(context.cacheDir, remoteAttachment.filename)
        file.writeBytes(decodedAttachment.data.toByteArray())
        file
    } catch (e: Exception) {
        android.util.Log.e("AttachmentHelper", "Failed to download/decrypt attachment", e)
        null
    }
}
