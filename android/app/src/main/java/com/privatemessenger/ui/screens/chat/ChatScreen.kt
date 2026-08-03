package com.privatemessenger.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import android.net.Uri
import android.media.MediaRecorder
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.widget.Toast
import java.io.File
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privatemessenger.data.local.AppDatabase
import com.privatemessenger.data.local.entity.ConversationEntity
import com.privatemessenger.data.local.entity.MessageEntity
import com.privatemessenger.data.local.entity.MessageStatus
import com.privatemessenger.data.local.entity.MessageType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.URL
import com.google.protobuf.ByteString
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmtp.android.library.SendOptions
import org.xmtp.android.library.codecs.Attachment
import org.xmtp.android.library.codecs.AttachmentCodec
import org.xmtp.android.library.codecs.ContentTypeRemoteAttachment
import org.xmtp.android.library.codecs.RemoteAttachment
import org.xmtp.android.library.codecs.RemoteAttachmentCodec
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.privatemessenger.utils.TypingManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    conversationId: String,
    database: AppDatabase,
    app: com.privatemessenger.PrivateMessengerApp,
    onBack: () -> Unit,
    onHeaderClicked: () -> Unit,
    onCallClicked: (isVoiceOnly: Boolean) -> Unit
) {
    val allMessages by database.messageDao().getMessagesForConversation(conversationId).collectAsState(initial = emptyList())
    val messages = remember(allMessages) { allMessages.filter { !it.content.trim().startsWith("@") } }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var conversation by remember { mutableStateOf<ConversationEntity?>(null) }
    var replyingToMessage by remember { mutableStateOf<MessageEntity?>(null) }
    val clipboardManager = LocalClipboardManager.current
    var showCallPicker by remember { mutableStateOf(false) }
    
    val typingStates by TypingManager.typingStates.collectAsState()
    val isTyping by remember(conversationId, typingStates) {
        androidx.compose.runtime.derivedStateOf { typingStates[conversationId]?.any { it != app.xmtpClient?.inboxId } == true }
    }

    LaunchedEffect(conversationId) {
        conversation = database.conversationDao().getConversation(conversationId)
        
        // Historical Backfill
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = app.xmtpClient ?: return@launch
                val xmtpConversation = client.conversations.findConversation(conversationId) ?: return@launch
                val xmtpMessages = xmtpConversation.messages(limit = 50)
                
                var hasNew = false
                var lastMsgContent = ""
                var lastMsgTime = 0L

                xmtpMessages.forEach { msg ->
                    val isCleared = (msg.sentAt.time <= (conversation?.clearedUpTo ?: 0L))
                    if (!isCleared && database.messageDao().findById(msg.id) == null) {
                        var finalContent = ""
                        try {
                            finalContent = msg.body
                        } catch (e: Exception) {
                            finalContent = ""
                        }
                        var finalReplyToId: String? = null
                        var isStructuralPayload = false

                        var downloadedAttachmentPath: String? = null
                        val trimmedBody = finalContent.trim()
                        if (trimmedBody.startsWith("{") && trimmedBody.endsWith("}")) {
                            try {
                                val json = com.google.gson.JsonParser.parseString(trimmedBody).asJsonObject
                                val type = json.get("type")?.asString
                                
                                if (type == "reaction" || type == "read" || type == "typing" || type == "clear_history_request" || type == "clear_history_accept" || type == "clear_history_decline" || type?.startsWith("webrtc_") == true) {
                                    isStructuralPayload = true
                                } else if (type == "attachment") {
                                    isStructuralPayload = false
                                    try {
                                        val dummyRA = org.xmtp.android.library.codecs.RemoteAttachment(
                                            url = java.net.URL(json.get("url")?.asString ?: ""),
                                            contentDigest = json.get("contentDigest")?.asString ?: "",
                                            salt = com.google.protobuf.ByteString.copyFrom(com.privatemessenger.utils.decodeHex(json.get("salt")?.asString ?: "")),
                                            nonce = com.google.protobuf.ByteString.copyFrom(com.privatemessenger.utils.decodeHex(json.get("nonce")?.asString ?: "")),
                                            secret = com.google.protobuf.ByteString.copyFrom(com.privatemessenger.utils.decodeHex(json.get("secret")?.asString ?: "")),
                                            scheme = json.get("scheme")?.asString ?: "https://",
                                            contentLength = json.get("contentLength")?.asInt ?: 0,
                                            filename = json.get("filename")?.asString ?: "attachment"
                                        )
                                        val file = com.privatemessenger.utils.downloadAndSaveRemoteAttachment(client, dummyRA, app)
                                        if (file != null) {
                                            downloadedAttachmentPath = file.absolutePath
                                            finalContent = "Attachment: ${dummyRA.filename}"
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("ChatScreen", "Failed to parse/download structural attachment", e)
                                    }
                                } else if (type == "reply") {
                                    finalContent = json.get("content")?.asString ?: msg.body
                                    finalReplyToId = json.get("replyToId")?.asString
                                }
                            } catch (e: Exception) {
                                // Not a valid structural JSON, treat as normal text
                            }
                        }

                        try {
                            val contentObj = msg.content<Any?>()
                            if (contentObj is org.xmtp.android.library.codecs.RemoteAttachment) {
                                val file = com.privatemessenger.utils.downloadAndSaveRemoteAttachment(client, contentObj, app)
                                if (file != null) {
                                    downloadedAttachmentPath = file.absolutePath
                                    finalContent = "Attachment: ${contentObj.filename}"
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ChatScreen", "Failed to parse content as RemoteAttachment", e)
                        }

                        if (!isStructuralPayload) {
                            val msgEntity = MessageEntity(
                                id = msg.id,
                                conversationId = conversationId,
                                senderUserId = msg.senderInboxId,
                                content = finalContent,
                                replyToMessageId = finalReplyToId,
                                timestamp = msg.sentAt.time,
                                status = MessageStatus.DELIVERED,
                                attachmentUri = downloadedAttachmentPath
                            )
                            database.messageDao().insert(msgEntity)
                            hasNew = true
                            if (msg.sentAt.time > lastMsgTime) {
                                lastMsgTime = msg.sentAt.time
                                lastMsgContent = finalContent
                            }
                        }
                    }
                }
                
                if (hasNew) {
                    if (lastMsgTime > (conversation?.lastMessageTimestamp ?: 0L)) {
                        database.conversationDao().updateLastMessage(conversationId, lastMsgContent, lastMsgTime)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatScreen", "Failed to backfill historical messages", e)
            }
        }
    }

    // Mark as read when entering or receiving new messages while in the chat
    LaunchedEffect(allMessages.size) {
        val hasUnread = allMessages.any { it.senderUserId != app.xmtpClient?.inboxId && it.status != MessageStatus.READ }
        if (allMessages.isNotEmpty()) {
            database.conversationDao().markAsRead(conversationId)
        }
        if (hasUnread) {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val client = app.xmtpClient ?: return@launch
                    val xmtpConversation = client.conversations.findConversation(conversationId) ?: return@launch
                    val payload = """{"type":"read","timestamp":${System.currentTimeMillis()}}"""
                    when (xmtpConversation) {
                        is org.xmtp.android.library.Conversation.Dm -> xmtpConversation.dm.send(payload)
                        is org.xmtp.android.library.Conversation.Group -> xmtpConversation.group.send(payload)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatScreen", "Failed to send read receipt", e)
                }
            }
        }
    }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Call type picker dialog
    if (showCallPicker) {
        AlertDialog(
            onDismissRequest = { showCallPicker = false },
            title = { Text("Start a Call") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Voice call option
                    OutlinedButton(
                        onClick = {
                            showCallPicker = false
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val client = app.xmtpClient ?: return@launch
                                    val xmtpConv = client.conversations.findConversation(conversationId) ?: return@launch
                                    val payload = "SYSTEM_UI:call_started:📞 Voice call started"
                                    when (xmtpConv) {
                                        is org.xmtp.android.library.Conversation.Dm -> xmtpConv.dm.send(payload)
                                        is org.xmtp.android.library.Conversation.Group -> xmtpConv.group.send(payload)
                                    }
                                } catch (e: Exception) { android.util.Log.e("ChatScreen", "call event failed", e) }
                            }
                            onCallClicked(true)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Voice Call")
                    }
                    // Video call option
                    Button(
                        onClick = {
                            showCallPicker = false
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val client = app.xmtpClient ?: return@launch
                                    val xmtpConv = client.conversations.findConversation(conversationId) ?: return@launch
                                    val payload = "SYSTEM_UI:call_started:📹 Video call started"
                                    when (xmtpConv) {
                                        is org.xmtp.android.library.Conversation.Dm -> xmtpConv.dm.send(payload)
                                        is org.xmtp.android.library.Conversation.Group -> xmtpConv.group.send(payload)
                                    }
                                } catch (e: Exception) { android.util.Log.e("ChatScreen", "call event failed", e) }
                            }
                            onCallClicked(false)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Video Call")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCallPicker = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onHeaderClicked() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (conversation?.profilePictureUri != null) {
                                AsyncImage(
                                    model = conversation?.profilePictureUri,
                                    contentDescription = "Profile Picture",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                val icon = if (conversation?.isGroup == true) Icons.Default.Group else Icons.Default.Person
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            conversation?.displayName ?: "Chat", 
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "End-to-End Encrypted",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCallPicker = true }) {
                        Icon(Icons.Default.Phone, contentDescription = "Call")
                    }
                    var showClearHistoryDialog by remember { mutableStateOf(false) }
                    IconButton(onClick = { showClearHistoryDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Chat History")
                    }

                    if (showClearHistoryDialog) {
                        AlertDialog(
                            onDismissRequest = { showClearHistoryDialog = false },
                            title = { Text("Clear Chat History") },
                            text = { Text("How would you like to clear this chat history?\n\n'Local Clear' will instantly delete all messages from this device.\n\n'Clear for Both' will send a request to the other user to consensually delete the history for both of you.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showClearHistoryDialog = false
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val now = System.currentTimeMillis()
                                        database.conversationDao().updateClearedUpTo(conversationId, now)
                                        database.messageDao().deleteAllInConversationBefore(conversationId, now)
                                    }
                                }) {
                                    Text("Local Clear")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showClearHistoryDialog = false
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val client = app.xmtpClient ?: return@launch
                                            val xmtpConversation = client.conversations.findConversation(conversationId) ?: return@launch
                                            val payload = """{"type":"clear_history_request","timestamp":${System.currentTimeMillis()}}"""
                                            when (xmtpConversation) {
                                                is org.xmtp.android.library.Conversation.Dm -> xmtpConversation.dm.send(payload)
                                                is org.xmtp.android.library.Conversation.Group -> xmtpConversation.group.send(payload)
                                            }
                                            val msgEntity = com.privatemessenger.data.local.entity.MessageEntity(
                                                id = java.util.UUID.randomUUID().toString(),
                                                conversationId = conversationId,
                                                senderUserId = client.inboxId,
                                                content = "SYSTEM_UI:clear_history_request_sent",
                                                timestamp = System.currentTimeMillis(),
                                                status = com.privatemessenger.data.local.entity.MessageStatus.DELIVERED
                                            )
                                            database.messageDao().insert(msgEntity)
                                        } catch (e: Exception) {
                                            android.util.Log.e("ChatScreen", "Failed to send clear request", e)
                                        }
                                    }
                                }) {
                                    Text("Clear for Both")
                                }
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val groupedMessages = messages.groupBy { 
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.timestamp))
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedMessages.forEach { (_, msgs) ->
                        stickyHeader {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = getDateLabel(msgs.first().timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        
                        items(msgs, key = { it.id }) { message ->
                            MessageBubble(
                                message = message, 
                                isCurrentUser = message.senderUserId == app.xmtpClient?.inboxId,
                                onReply = { replyingToMessage = it },
                                onReact = { emoji ->
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val client = app.xmtpClient ?: return@launch
                                            val xmtpConversation = client.conversations.findConversation(conversationId) ?: return@launch
                                            val payload = Gson().toJson(mapOf(
                                                "type" to "reaction",
                                                "messageId" to message.id,
                                                "emoji" to emoji
                                            ))
                                            when (xmtpConversation) {
                                                is org.xmtp.android.library.Conversation.Dm -> xmtpConversation.dm.send(payload)
                                                is org.xmtp.android.library.Conversation.Group -> xmtpConversation.group.send(payload)
                                            }
                                        } catch (e: Exception) { }
                                    }
                                },
                                onCopy = { clipboardManager.setText(AnnotatedString(it)) },
                                allMessages = allMessages,
                                modifier = Modifier.animateItemPlacement(),
                                onSystemAction = { action ->
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val client = app.xmtpClient ?: return@launch
                                            val xmtpConversation = client.conversations.findConversation(conversationId) ?: return@launch
                                            if (action.startsWith("accept_clear:")) {
                                                val tsStr = action.substringAfter(":")
                                                val ts = tsStr.toLongOrNull() ?: System.currentTimeMillis()
                                                val payload = """{"type":"clear_history_accept","timestamp":$ts}"""
                                                when (xmtpConversation) {
                                                    is org.xmtp.android.library.Conversation.Dm -> xmtpConversation.dm.send(payload)
                                                    is org.xmtp.android.library.Conversation.Group -> xmtpConversation.group.send(payload)
                                                }
                                                database.conversationDao().updateClearedUpTo(conversationId, ts)
                                                database.messageDao().deleteAllInConversationBefore(conversationId, ts)
                                                // Remove the request message
                                                database.messageDao().delete(message.id)
                                                // Add accept message
                                                val msgEntity = com.privatemessenger.data.local.entity.MessageEntity(
                                                    id = java.util.UUID.randomUUID().toString(),
                                                    conversationId = conversationId,
                                                    senderUserId = client.inboxId,
                                                    content = "SYSTEM_UI:clear_history_accept",
                                                    timestamp = System.currentTimeMillis(),
                                                    status = com.privatemessenger.data.local.entity.MessageStatus.DELIVERED
                                                )
                                                database.messageDao().insert(msgEntity)
                                            } else if (action == "decline_clear") {
                                                val payload = """{"type":"clear_history_decline"}"""
                                                when (xmtpConversation) {
                                                    is org.xmtp.android.library.Conversation.Dm -> xmtpConversation.dm.send(payload)
                                                    is org.xmtp.android.library.Conversation.Group -> xmtpConversation.group.send(payload)
                                                }
                                                // Remove the request message
                                                database.messageDao().delete(message.id)
                                                // Add decline message
                                                val msgEntity = com.privatemessenger.data.local.entity.MessageEntity(
                                                    id = java.util.UUID.randomUUID().toString(),
                                                    conversationId = conversationId,
                                                    senderUserId = client.inboxId,
                                                    content = "SYSTEM_UI:clear_history_decline",
                                                    timestamp = System.currentTimeMillis(),
                                                    status = com.privatemessenger.data.local.entity.MessageStatus.DELIVERED
                                                )
                                                database.messageDao().insert(msgEntity)
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("ChatScreen", "Failed to send system action", e)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    if (isTyping) {
                        item {
                            TypingIndicatorBubble(modifier = Modifier.animateItemPlacement())
                        }
                    }
                }

                ChatInputArea(
                    text = inputText,
                    replyingToMessage = replyingToMessage,
                    onCancelReply = { replyingToMessage = null },
                    onTextChange = { inputText = it },
                    onImageSelected = { uri ->
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val inputStream = app.contentResolver.openInputStream(uri)
                                val bytes = inputStream?.readBytes() ?: return@launch
                                val mimeType = app.contentResolver.getType(uri) ?: "application/octet-stream"
                                val ext = mimeType.substringAfterLast('/')
                                val filename = "attachment_${System.currentTimeMillis()}.$ext"

                                val localFile = File(app.filesDir, filename)
                                localFile.writeBytes(bytes)

                                val client = app.xmtpClient ?: return@launch
                                val localMessageId = java.util.UUID.randomUUID().toString()
                                val msgEntity = MessageEntity(
                                    id = localMessageId,
                                    conversationId = conversationId,
                                    senderUserId = client.inboxId,
                                    content = "📎 Attachment",
                                    attachmentUri = localFile.absolutePath,
                                    timestamp = System.currentTimeMillis(),
                                    status = MessageStatus.SENDING
                                )
                                database.messageDao().insert(msgEntity)
                                database.conversationDao().updateLastMessage(conversationId, "📎 Attachment", msgEntity.timestamp)

                                try {
                                    val result = com.privatemessenger.utils.sendEncryptedAttachment(
                                        app, conversationId, bytes, mimeType, filename
                                    ) ?: throw Exception("Attachment null result")
                                    val (_, sentMessageId) = result

                                    database.messageDao().delete(localMessageId)
                                    database.messageDao().insert(msgEntity.copy(id = sentMessageId, status = MessageStatus.SENT))
                                } catch (e: Exception) {
                                    android.util.Log.e("ChatScreen", "Failed to send attachment", e)
                                    database.messageDao().updateStatus(localMessageId, MessageStatus.FAILED)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ChatScreen", "Failed to process attachment", e)
                            }
                        }
                    },
                    onVoiceMemoRecorded = { audioFile ->
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val bytes = audioFile.readBytes()
                                val filename = audioFile.name

                                val localCopy = File(app.filesDir, filename)
                                localCopy.writeBytes(bytes)
                                val client = app.xmtpClient ?: return@launch
                                val localMessageId = java.util.UUID.randomUUID().toString()
                                val msgEntity = MessageEntity(
                                    id = localMessageId,
                                    conversationId = conversationId,
                                    senderUserId = client.inboxId,
                                    content = "",
                                    audioUri = localCopy.absolutePath,
                                    type = MessageType.VOICE,
                                    timestamp = System.currentTimeMillis(),
                                    status = MessageStatus.SENDING
                                )
                                database.messageDao().insert(msgEntity)
                                database.conversationDao().updateLastMessage(conversationId, "🎙️ Voice memo", msgEntity.timestamp)

                                try {
                                    val result = com.privatemessenger.utils.sendEncryptedAttachment(
                                        app, conversationId, bytes, "audio/ogg", filename
                                    ) ?: throw Exception("Audio attachment null result")
                                    val (_, sentMessageId) = result

                                    database.messageDao().delete(localMessageId)
                                    database.messageDao().insert(msgEntity.copy(id = sentMessageId, status = MessageStatus.SENT))
                                } catch (e: Exception) {
                                    android.util.Log.e("ChatScreen", "Failed to send voice memo", e)
                                    database.messageDao().updateStatus(localMessageId, MessageStatus.FAILED)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(app, "Voice memo failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ChatScreen", "Failed to process voice memo", e)
                            }
                        }
                    },
                    onTypingStateChange = { isTypingPayload ->
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val client = app.xmtpClient ?: return@launch
                                val xmtpConversation = client.conversations.findConversation(conversationId) ?: return@launch
                                val payload = Gson().toJson(mapOf(
                                    "type" to "typing",
                                    "isTyping" to isTypingPayload
                                ))
                                when (xmtpConversation) {
                                    is org.xmtp.android.library.Conversation.Dm -> xmtpConversation.dm.send(payload)
                                    is org.xmtp.android.library.Conversation.Group -> xmtpConversation.group.send(payload)
                                }
                            } catch (e: Exception) {}
                        }
                    },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            val textToSend = inputText
                            inputText = ""
                            val replyToId = replyingToMessage?.id
                            replyingToMessage = null

                            // Instantly cancel typing status locally to trigger the false broadcast via LaunchedEffect
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val conversation = database.conversationDao().getConversation(conversationId)
                                    if (conversation != null) {
                                        val client = app.xmtpClient ?: return@launch
                                        val xmtpConversation = client.conversations.findConversation(conversationId)
                                            ?: run {
                                                android.util.Log.e("ChatScreen", "Conversation not found: $conversationId")
                                                return@launch
                                            }
                                            
                                        val localMessageId = java.util.UUID.randomUUID().toString()
                                        val msgEntity = MessageEntity(
                                            id = localMessageId,
                                            conversationId = conversation.id,
                                            senderUserId = client.inboxId,
                                            content = textToSend,
                                            replyToMessageId = replyToId,
                                            timestamp = System.currentTimeMillis(),
                                            status = MessageStatus.SENDING
                                        )
                                        database.messageDao().insert(msgEntity)
                                        database.conversationDao().updateLastMessage(conversation.id, textToSend, msgEntity.timestamp)

                                        val payload = if (replyToId != null) {
                                            Gson().toJson(mapOf(
                                                "type" to "reply",
                                                "replyToId" to replyToId,
                                                "content" to textToSend
                                            ))
                                        } else {
                                            textToSend
                                        }
                                        
                                        try {
                                            val sentMessageId = when (xmtpConversation) {
                                                is org.xmtp.android.library.Conversation.Dm -> xmtpConversation.dm.send(payload)
                                                is org.xmtp.android.library.Conversation.Group -> xmtpConversation.group.send(payload)
                                                else -> error("Unknown conversation type")
                                            }
                                            database.messageDao().delete(localMessageId)
                                            database.messageDao().insert(msgEntity.copy(id = sentMessageId, status = MessageStatus.SENT))
                                        } catch (e: Exception) {
                                            android.util.Log.e("ChatScreen", "Failed to send message", e)
                                            database.messageDao().updateStatus(localMessageId, MessageStatus.FAILED)
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("ChatScreen", "Failed to construct message", e)
                                }
                            }
                        }
                    }
                )
            }

            val showFab by remember {
                derivedStateOf { listState.canScrollForward }
            }

            AnimatedVisibility(
                visible = showFab,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 80.dp, end = 16.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(messages.size - 1)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll to bottom", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
fun TypingIndicatorBubble(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "Typing")
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0..2) {
                    val yOffset by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = -6f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(300, delayMillis = i * 150),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot"
                    )
                    Box(
                        modifier = Modifier
                            .offset(y = yOffset.dp)
                            .size(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    isCurrentUser: Boolean,
    onReply: (MessageEntity) -> Unit,
    onReact: (String) -> Unit,
    onCopy: (String) -> Unit,
    allMessages: List<MessageEntity>,
    modifier: Modifier = Modifier,
    onSystemAction: ((String) -> Unit)? = null
) {
    val alignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = if (isCurrentUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    var showReactionsMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start) {
            
            // The Message Box
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(backgroundColor)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { showReactionsMenu = true },
                            onDoubleTap = { onReply(message) }
                        )
                    }
                    .padding(if (message.attachmentUri != null) 4.dp else 12.dp, if (message.attachmentUri != null) 4.dp else 8.dp)
                    .widthIn(min = 80.dp, max = 280.dp)
            ) {
                Column {
                    // Quoted Reply Preview
                    if (message.replyToMessageId != null) {
                        val quoted = allMessages.find { it.id == message.replyToMessageId }
                        if (quoted != null) {
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.15f))
                                .padding(8.dp)
                            ) {
                                Text(
                                    text = quoted.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor.copy(alpha = 0.8f),
                                    maxLines = 2
                                )
                            }
                        }
                    }

                    if (message.content.startsWith("SYSTEM_UI:")) {
                        val parts = message.content.split(":")
                        val type = parts.getOrNull(1)
                        val timestampStr = parts.getOrNull(2)
                        
                        when (type) {
                            "clear_history_request_sent" -> {
                                Text(
                                    text = "You requested to clear history for both users. Waiting for consent...",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                    color = textColor.copy(alpha = 0.8f)
                                )
                            }
                            "clear_history_request" -> {
                                Column {
                                    Text(
                                        text = "Contact requested to clear the chat history for both users.",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = textColor
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { onSystemAction?.invoke("accept_clear:$timestampStr") },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                                        ) {
                                            Text("Accept")
                                        }
                                        OutlinedButton(
                                            onClick = { onSystemAction?.invoke("decline_clear") },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
                                        ) {
                                            Text("Decline")
                                        }
                                    }
                                }
                            }
                            "clear_history_accept" -> {
                                Text(
                                    text = "History cleared by consent.",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                    color = textColor.copy(alpha = 0.8f)
                                )
                            }
                            "clear_history_decline" -> {
                                Text(
                                    text = "Contact declined the clear history request.",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                    color = textColor.copy(alpha = 0.8f)
                                )
                            }
                            "call_started" -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val icon = if (timestampStr?.startsWith("📞") == true) "📞" else "📹"
                                    val rest = timestampStr?.removePrefix("📞")?.removePrefix("📹")?.trim()?.replace(Regex(" at \\d{1,2}:\\d{2}"), "") ?: "Call started"
                                    val timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
                                    Text(icon, fontSize = 16.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "${rest.ifEmpty { "Call started" }} at $timeString",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                        color = textColor.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            "call_ended" -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("📵", fontSize = 16.sp)
                                    Spacer(Modifier.width(8.dp))
                                    val timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
                                    val baseText = parts.drop(2).joinToString(":").ifEmpty { "Call ended" }
                                    // baseText might be "📞 Voice call ended • 0:45"
                                    // we want "Voice call ended at 14:05 • 0:45"
                                    val cleanText = baseText.removePrefix("📞").removePrefix("📹").trim().replace(Regex(" at \\d{1,2}:\\d{2}"), "")
                                    val durationPart = if (cleanText.contains("•")) " • " + cleanText.substringAfter("•").trim() else ""
                                    val callPart = if (cleanText.contains("•")) cleanText.substringBefore("•").trim() else cleanText
                                    
                                    Text(
                                        text = "$callPart at $timeString$durationPart",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                        color = textColor.copy(alpha = 0.75f)
                                    )
                                }
                            }
                            else -> {
                                Text(
                                    text = "System Message",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textColor
                                )
                            }
                        }
                    } else if (message.attachmentUri != null) {
                        // Detect content type from file extension
                        val path = message.attachmentUri
                        val ext = path.substringAfterLast('.', "").lowercase()
                        val isVideo = ext in listOf("mp4", "mkv", "webm", "mov", "avi")
                        val isPdf = ext == "pdf"

                        if (isVideo) {
                            // Video thumbnail via AsyncImage + play overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                AsyncImage(
                                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                        .data(android.net.Uri.fromFile(File(path)))
                                        .decoderFactory(coil.decode.VideoFrameDecoder.Factory())
                                        .build(),
                                    contentDescription = "Video",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("▶", fontSize = 40.sp, color = Color.White)
                                }
                            }
                        } else if (isPdf) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Text("📄", fontSize = 28.sp)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = File(path).name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = textColor
                                    )
                                    Text("PDF Document", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
                                }
                            }
                        } else {
                            AsyncImage(
                                model = File(message.attachmentUri),
                                contentDescription = "Attachment",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                        if (message.content.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Voice memo
                    if (message.audioUri != null) {
                        AudioPlayerBubble(
                            audioPath = message.audioUri,
                            textColor = textColor,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    if (message.content.isNotBlank() && message.content != "🎙️ Voice memo" && !message.content.startsWith("SYSTEM_UI:") && !message.content.trim().startsWith("{")) {
                        MessageText(text = message.content, textColor = textColor)
                    }
                    
                    // Time and Read Status
                    Row(
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = textColor.copy(alpha = 0.7f)
                        )
                        if (isCurrentUser) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val (icon, tint) = when (message.status) {
                                MessageStatus.READ -> Icons.Default.DoneAll to Color(0xFF34B7F1)
                                MessageStatus.DELIVERED -> Icons.Default.DoneAll to textColor.copy(alpha = 0.7f)
                                MessageStatus.SENT -> Icons.Default.Check to textColor.copy(alpha = 0.7f)
                                MessageStatus.SENDING -> Icons.Default.Schedule to textColor.copy(alpha = 0.5f)
                                MessageStatus.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = "Status",
                                modifier = Modifier.size(14.dp),
                                tint = tint
                            )
                        }
                    }
                }
                
                // Reaction Popup Menu overlay
                DropdownMenu(
                    expanded = showReactionsMenu,
                    onDismissRequest = { showReactionsMenu = false }
                ) {
                    val emojis = listOf("❤️", "👍", "😂", "😮", "😢", "🔥")
                    Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                        emojis.forEach { emoji ->
                            TextButton(
                                onClick = {
                                    onReact(emoji)
                                    showReactionsMenu = false
                                },
                                modifier = Modifier.size(40.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(emoji, fontSize = 24.sp)
                            }
                        }
                        IconButton(
                            onClick = {
                                onCopy(message.content)
                                showReactionsMenu = false
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                    }
                }
            }
            
            // Display Reactions Below Bubble
            if (message.reactionsJson != null) {
                val reactionsType = object : TypeToken<Map<String, String>>() {}.type
                val reactionsMap: Map<String, String>? = Gson().fromJson(message.reactionsJson, reactionsType)
                if (reactionsMap != null && reactionsMap.isNotEmpty()) {
                    val counts = reactionsMap.values.groupingBy { it }.eachCount()
                    Row(
                        modifier = Modifier
                            .offset(y = (-8).dp, x = if (isCurrentUser) (-8).dp else 8.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        counts.forEach { (emoji, count) ->
                            Text(
                                text = if (count > 1) "$emoji $count" else emoji,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageText(text: String, textColor: Color) {
    val urlPattern = Regex("(?i)\\bhttps?://[^\\s]+")
    val matches = urlPattern.findAll(text).toList()
    
    if (matches.isEmpty()) {
        Text(
            text = text, 
            style = MaterialTheme.typography.bodyLarge, 
            color = textColor,
            modifier = Modifier.padding(if (text.contains("\n")) 4.dp else 0.dp)
        )
        return
    }

    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        for (match in matches) {
            append(text.substring(lastIndex, match.range.first))
            pushStringAnnotation(tag = "URL", annotation = match.value)
            withStyle(style = SpanStyle(color = Color(0xFF34B7F1), textDecoration = TextDecoration.Underline)) {
                append(match.value)
            }
            pop()
            lastIndex = match.range.last + 1
        }
        append(text.substring(lastIndex, text.length))
    }
    
    val uriHandler = LocalUriHandler.current
    
    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    try { uriHandler.openUri(annotation.item) } catch (e: Exception) {}
                }
        },
        modifier = Modifier.padding(if (text.contains("\n")) 4.dp else 0.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputArea(
    text: String,
    replyingToMessage: MessageEntity?,
    onCancelReply: () -> Unit,
    onTextChange: (String) -> Unit,
    onImageSelected: (Uri) -> Unit,
    onVoiceMemoRecorded: (File) -> Unit,
    onTypingStateChange: (Boolean) -> Unit,
    onSend: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onImageSelected(it) }
    }

    var lastTypingTime by remember { mutableStateOf(0L) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val mediaRecorderRef = remember { mutableStateOf<MediaRecorder?>(null) }
    val voiceFileRef = remember { mutableStateOf<File?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Microphone permission is required for voice memos", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                delay(1000)
                recordingSeconds++
            }
        } else {
            recordingSeconds = 0
        }
    }
    
    LaunchedEffect(text) {
        if (text.isNotBlank()) {
            val now = System.currentTimeMillis()
            if (now - lastTypingTime > 2000) {
                lastTypingTime = now
                onTypingStateChange(true)
            }
            delay(3000)
            onTypingStateChange(false)
            lastTypingTime = 0L
        } else if (lastTypingTime != 0L) {
            onTypingStateChange(false)
            lastTypingTime = 0L
        }
    }

    // Cleanup recorder on dispose
    DisposableEffect(Unit) {
        onDispose {
            mediaRecorderRef.value?.release()
            mediaRecorderRef.value = null
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            AnimatedVisibility(visible = replyingToMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Replying to message",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = replyingToMessage?.content ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = onCancelReply, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isRecording) {
                    // Attach image / file button
                    IconButton(onClick = { launcher.launch("*/*") }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Attach file",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    TextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp)),
                        placeholder = { Text("Message") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        maxLines = 4
                    )
                } else {
                    // Recording Indicator
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val minutes = recordingSeconds / 60
                        val seconds = recordingSeconds % 60
                        val timeString = String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
                        Text(
                            text = "Recording... $timeString",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                // Cancel recording
                                try { mediaRecorderRef.value?.stop() } catch (e: Exception) {}
                                mediaRecorderRef.value?.release()
                                mediaRecorderRef.value = null
                                isRecording = false
                                voiceFileRef.value?.delete()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                if (isRecording) {
                    // Send voice memo button
                    IconButton(
                        onClick = {
                            try { mediaRecorderRef.value?.stop() } catch (e: Exception) { }
                            mediaRecorderRef.value?.release()
                            mediaRecorderRef.value = null
                            isRecording = false
                            val file = voiceFileRef.value
                            if (file != null && file.exists()) {
                                onVoiceMemoRecorded(file)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send voice memo",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                } else if (text.isNotBlank()) {
                    // Send text message button
                    IconButton(
                        onClick = onSend,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                } else {
                    // Mic button (Start recording)
                    IconButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                return@IconButton
                            }
                            val outputFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.ogg")
                            voiceFileRef.value = outputFile
                            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                MediaRecorder(context)
                            } else {
                                @Suppress("DEPRECATION")
                                MediaRecorder()
                            }
                            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
                            mr.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                            mr.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                            mr.setOutputFile(outputFile.absolutePath)
                            try {
                                mr.prepare()
                                mr.start()
                                mediaRecorderRef.value = mr
                                isRecording = true
                            } catch (e: Exception) {
                                android.util.Log.e("ChatScreen", "Failed to start recording", e)
                                android.widget.Toast.makeText(context, "Failed to start recording", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Record voice memo",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

fun getDateLabel(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Date()
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val dateStr = format.format(date)
    val nowStr = format.format(now)
    val yesterdayStr = format.format(Date(now.time - 86400000L))
    return when (dateStr) {
        nowStr -> "Today"
        yesterdayStr -> "Yesterday"
        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(date)
    }
}
