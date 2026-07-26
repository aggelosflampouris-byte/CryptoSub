package com.privatemessenger.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object TypingManager {
    // conversationId -> set of senderInboxIds currently typing
    private val _typingStates = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val typingStates: StateFlow<Map<String, Set<String>>> = _typingStates.asStateFlow()

    fun setTyping(conversationId: String, senderId: String, isTyping: Boolean) {
        _typingStates.update { currentMap ->
            val currentTypingUsers = currentMap[conversationId]?.toMutableSet() ?: mutableSetOf()
            if (isTyping) {
                currentTypingUsers.add(senderId)
            } else {
                currentTypingUsers.remove(senderId)
            }
            currentMap.toMutableMap().apply {
                if (currentTypingUsers.isEmpty()) {
                    remove(conversationId)
                } else {
                    put(conversationId, currentTypingUsers)
                }
            }
        }
    }
}
