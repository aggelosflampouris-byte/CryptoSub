package com.privatemessenger.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.privatemessenger.data.local.entity.MessageEntity
import com.privatemessenger.data.local.entity.MessageStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    database: AppDatabase,
    app: com.privatemessenger.PrivateMessengerApp,
    onBack: () -> Unit
) {
    val allMessages by database.messageDao().getMessagesForConversation(conversationId).collectAsState(initial = emptyList())
    val messages = remember(allMessages) { allMessages.filter { !it.content.matches(Regex("^(@[a-fA-F0-9]{40,}\\s*)+$")) } }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Mark as read when entering
    LaunchedEffect(conversationId) {
        database.conversationDao().markAsRead(conversationId)
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
                            "Chat", 
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
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message, isCurrentUser = message.senderUserId == app.xmtpClient?.inboxId)
                }
            }

            ChatInputArea(
                text = inputText,
                onTextChange = { inputText = it },
                onImageSelected = { uri ->
                    // In a real implementation, the ViewModel would read the bytes from the URI
                    // and call messageRepository.sendMessage(..., attachmentBytes = bytes, mimeType = "image/jpeg")
                    // For now, we simulate an attachment send:
                    // TODO: Trigger MessageRepository.sendMessage with attachment
                },
                onSend = {
                    if (inputText.isNotBlank()) {
                        val textToSend = inputText
                        inputText = ""
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val conversation = database.conversationDao().getConversation(conversationId)
                                if (conversation != null) {
                                val client = app.xmtpClient ?: return@launch
                                // conversationId IS the XMTP DM hex ID (set during contact add)
                                val xmtpConversation = client.conversations.findConversation(conversationId)
                                    ?: run {
                                        android.util.Log.e("ChatScreen", "Conversation not found: $conversationId")
                                        return@launch
                                    }
                                val sentMessageId = when (xmtpConversation) {
                                    is org.xmtp.android.library.Conversation.Dm -> xmtpConversation.dm.send(textToSend)
                                    is org.xmtp.android.library.Conversation.Group -> xmtpConversation.group.send(textToSend)
                                }
                                
                                val msgEntity = MessageEntity(
                                    id = sentMessageId,
                                    conversationId = conversation.id,
                                    senderUserId = client.inboxId,
                                    content = textToSend,
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
    isCurrentUser: Boolean
) {
    val alignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = if (isCurrentUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(backgroundColor)
                    .padding(if (message.attachmentUri != null) 4.dp else 16.dp, if (message.attachmentUri != null) 4.dp else 10.dp)
                    .widthIn(max = 280.dp)
            ) {
                Column {
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
                }
            }
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                if (isCurrentUser) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (message.status) {
                            MessageStatus.SENDING -> "â€¢"
                            MessageStatus.SENT -> "âœ“"
                            MessageStatus.DELIVERED -> "âœ“âœ“"
                            MessageStatus.READ -> "âœ“âœ“" // You can color this tertiary
                            MessageStatus.FAILED -> "!"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.status == MessageStatus.READ) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputArea(
    text: String,
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

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
