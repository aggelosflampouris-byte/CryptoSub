package com.privatemessenger.ui.screens.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.privatemessenger.PrivateMessengerApp
import com.privatemessenger.data.local.entity.ConversationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmtp.android.library.libxmtp.IdentityKind
import org.xmtp.android.library.libxmtp.PublicIdentity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    app: PrivateMessengerApp,
    onGroupCreated: (String) -> Unit,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    // Load only 1:1 conversations
    val conversations by app.database.conversationDao().getAllConversations().collectAsState(initial = emptyList())
    val contacts = conversations.filter { !it.isGroup }
    
    var selectedContactIds by remember { mutableStateOf(setOf<String>()) }
    var groupName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Group", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (selectedContactIds.isNotEmpty() && !isCreating) {
                                isCreating = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val client = app.xmtpClient ?: return@launch
                                        val addresses = mutableListOf<String>()
                                        
                                        // Resolve XMTP DM IDs to peer addresses
                                        for (id in selectedContactIds) {
                                            val xmtpConv = client.conversations.findConversation(id)
                                            if (xmtpConv is org.xmtp.android.library.Conversation.Dm) {
                                                // Wait, peerInboxId is a property of DM in XMTP v4
                                                addresses.add(xmtpConv.dm.peerInboxId) 
                                            }
                                        }
                                        
                                        if (addresses.isEmpty()) {
                                            withContext(Dispatchers.Main) {
                                                isCreating = false
                                                android.widget.Toast.makeText(app, "No valid XMTP addresses found for selection", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                            return@launch
                                        }
                                        
                                        // Create group using XMTP SDK. Note: newGroup expects List<String> of addresses/inboxIds.
                                        val group = client.conversations.newGroup(addresses)
                                        val finalGroupName = groupName.trim().ifEmpty { "Group Chat" }
                                        
                                        // Save to DB
                                        val groupEntity = ConversationEntity(
                                            id = group.id,
                                            deviceId = 1,
                                            displayName = finalGroupName,
                                            isGroup = true,
                                            lastMessage = "Group created",
                                            lastMessageTimestamp = System.currentTimeMillis(),
                                            unreadCount = 0
                                        )
                                        app.database.conversationDao().upsert(groupEntity)
                                        
                                        withContext(Dispatchers.Main) {
                                            isCreating = false
                                            onGroupCreated(group.id)
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("CreateGroup", "Error creating group", e)
                                        withContext(Dispatchers.Main) {
                                            isCreating = false
                                            android.widget.Toast.makeText(app, "Failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        },
                        enabled = selectedContactIds.isNotEmpty() && !isCreating
                    ) {
                        Text("Create", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Group Name (Optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            
            Text(
                text = "Select Participants",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )
            
            if (contacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No contacts available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(contacts, key = { it.id }) { contact ->
                        val isSelected = selectedContactIds.contains(contact.id)
                        ContactSelectionItem(
                            contact = contact,
                            isSelected = isSelected,
                            onToggle = {
                                selectedContactIds = if (isSelected) {
                                    selectedContactIds - contact.id
                                } else {
                                    selectedContactIds + contact.id
                                }
                            }
                        )
                    }
                }
            }
        }
        
        if (isCreating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun ContactSelectionItem(
    contact: ConversationEntity,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.displayName?.take(1)?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = contact.displayName ?: "Unknown",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .run {
                    if (!isSelected) {
                        border(
                            1.dp,
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            CircleShape
                        )
                    } else this
                },
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
