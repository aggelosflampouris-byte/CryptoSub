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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import com.privatemessenger.utils.TypingManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    conversationId: String,
    database: AppDatabase,
    app: com.privatemessenger.PrivateMessengerApp,
    onBack: () -> Unit,
    onHeaderClicked: () -> Unit
) {
    val allMessages by database.messageDao().getMessagesForConversation(conversationId).collectAsState(initial = emptyList())
    val messages = remember(allMessages) { allMessages.filter { !it.content.trim().startsWith("@") } }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var conversation by remember { mutableStateOf<ConversationEntity?>(null) }
    var replyingToMessage by remember { mutableStateOf<MessageEntity?>(null) }
    val clipboardManager = LocalClipboardManager.current
    
    val typingStates by TypingManager.typingStates.collectAsState()
    val isTyping = typingStates[conversationId]?.any { it != app.xmtpClient?.inboxId } == true

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
                                modifier = Modifier.animateItemPlacement()
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
    modifier: Modifier = Modifier
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
    onTypingStateChange: (Boolean) -> Unit,
    onSend: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onImageSelected(it) }
    }

    var lastTypingTime by remember { mutableStateOf(0L) }
    
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
