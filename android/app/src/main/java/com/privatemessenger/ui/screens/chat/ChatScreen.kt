package com.privatemessenger.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import android.net.Uri
import java.io.File
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privatemessenger.data.local.AppDatabase
import com.privatemessenger.data.local.entity.ConversationEntity
import com.privatemessenger.data.local.entity.MessageEntity
import com.privatemessenger.data.local.entity.MessageStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    database: AppDatabase,
    app: com.privatemessenger.PrivateMessengerApp,
    onBack: () -> Unit
) {
    val allMessages by database.messageDao().getMessagesForConversation(conversationId).collectAsState(initial = emptyList())
    val messages = remember(allMessages) { allMessages.filter { !it.content.trim().startsWith("@") } }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var conversation by remember { mutableStateOf<ConversationEntity?>(null) }
    var replyingToMessage by remember { mutableStateOf<MessageEntity?>(null) }
    
    LaunchedEffect(conversationId) {
        conversation = database.conversationDao().getConversation(conversationId)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            conversation?.displayName ?: "Chat", 
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages.size, key = { messages[it].id }) { index ->
                    val message = messages[index]
                    val previousMessage = if (index > 0) messages[index - 1] else null
                    val showTimeHeader = previousMessage == null || (message.timestamp - previousMessage.timestamp > 3600000L) // 1 hour
                    
                    if (showTimeHeader) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = formatTimestampHeader(message.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
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
                        allMessages = allMessages
                    )
                }
            }

            ChatInputArea(
                text = inputText,
                replyingToMessage = replyingToMessage,
                onCancelReply = { replyingToMessage = null },
                onTextChange = { inputText = it },
                onImageSelected = { uri ->
                },
                onSend = {
                    if (inputText.isNotBlank()) {
                        val textToSend = inputText
                        inputText = ""
                        val replyToId = replyingToMessage?.id
                        replyingToMessage = null
                        
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
                                        
                                    val payload = if (replyToId != null) {
                                        Gson().toJson(mapOf(
                                            "type" to "reply",
                                            "replyToId" to replyToId,
                                            "content" to textToSend
                                        ))
                                    } else {
                                        textToSend
                                    }
                                    
                                    val sentMessageId = when (xmtpConversation) {
                                        is org.xmtp.android.library.Conversation.Dm -> xmtpConversation.dm.send(payload)
                                        is org.xmtp.android.library.Conversation.Group -> xmtpConversation.group.send(payload)
                                    }
                                    
                                    val msgEntity = MessageEntity(
                                        id = sentMessageId,
                                        conversationId = conversation.id,
                                        senderUserId = client.inboxId,
                                        content = textToSend,
                                        replyToMessageId = replyToId,
                                        timestamp = System.currentTimeMillis(),
                                        status = MessageStatus.SENT
                                    )
                                    database.messageDao().insert(msgEntity)
                                    database.conversationDao().updateLastMessage(conversation.id, textToSend, System.currentTimeMillis())
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ChatScreen", "Failed to send message", e)
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    isCurrentUser: Boolean,
    onReply: (MessageEntity) -> Unit,
    onReact: (String) -> Unit,
    allMessages: List<MessageEntity>
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
        modifier = Modifier.fillMaxWidth(),
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

                    if (message.attachmentUri != null) {
                        AsyncImage(
                            model = File(message.attachmentUri),
                            contentDescription = "Attachment",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        if (message.content.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor,
                            modifier = if (message.attachmentUri != null) Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp) else Modifier
                        )
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
                            val icon = if (message.status == MessageStatus.READ) Icons.Default.DoneAll else Icons.Default.Check
                            val tint = if (message.status == MessageStatus.READ) Color(0xFF34B7F1) else textColor.copy(alpha = 0.7f) // Blue for read
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputArea(
    text: String,
    replyingToMessage: MessageEntity?,
    onCancelReply: () -> Unit,
    onTextChange: (String) -> Unit,
    onImageSelected: (Uri) -> Unit,
    onSend: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onImageSelected(it) }
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
                IconButton(onClick = { launcher.launch("image/*") }) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Add,
                        contentDescription = "Attach image",
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
                
                Spacer(modifier = Modifier.width(8.dp))
                
                AnimatedVisibility(visible = text.isNotBlank()) {
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
                            modifier = Modifier.padding(start = 4.dp) // Optical alignment
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimestampHeader(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val date = Date(timestamp)
    val now = Date()
    val format = if (now.time - timestamp < 86400000L) {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    } else {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    }
    return format.format(date)
}
