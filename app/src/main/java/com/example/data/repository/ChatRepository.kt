package com.example.data.repository

import com.example.data.api.ChatApiClient
import com.example.data.api.ChatMessagePayload
import com.example.data.api.StreamEvent
import com.example.data.firebase.FirebaseManager
import com.example.data.firebase.SharedChatData
import com.example.data.local.ChatDao
import com.example.data.local.ChatMessage
import com.example.data.local.ChatSession
import com.example.data.local.MessageSender
import com.example.data.local.MessageStatus
import com.example.data.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChatRepository(
    private val chatDao: ChatDao,
    private val chatApiClient: ChatApiClient,
    private val preferencesManager: PreferencesManager,
    private val firebaseManager: FirebaseManager
) {
    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions()
    val allMessages: Flow<List<ChatMessage>> = chatDao.getAllMessages()

    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun createNewSession(title: String = "New Chat"): Long = withContext(Dispatchers.IO) {
        val session = ChatSession(
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val id = chatDao.insertSession(session)
        val createdSession = session.copy(id = id)
        firebaseManager.saveSessionToFirestore(createdSession)
        id
    }

    suspend fun updateSessionTitle(sessionId: Long, newTitle: String) = withContext(Dispatchers.IO) {
        chatDao.updateSessionTitle(sessionId, newTitle)
        val session = chatDao.getSessionById(sessionId)
        if (session != null) {
            firebaseManager.saveSessionToFirestore(session)
        }
    }

    suspend fun togglePinSession(sessionId: Long, isPinned: Boolean) = withContext(Dispatchers.IO) {
        chatDao.updateSessionPinned(sessionId, isPinned)
        val session = chatDao.getSessionById(sessionId)
        if (session != null) {
            firebaseManager.saveSessionToFirestore(session)
        }
    }

    suspend fun deleteSession(sessionId: Long) = withContext(Dispatchers.IO) {
        chatDao.deleteMessagesForSession(sessionId)
        chatDao.deleteSession(sessionId)
        firebaseManager.deleteSessionFromFirestore(sessionId)
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        chatDao.clearAllMessages()
        chatDao.clearAllSessions()
        firebaseManager.clearAllHistoryFromFirestore()
    }

    suspend fun saveUserMessage(sessionId: Long, text: String): Long = withContext(Dispatchers.IO) {
        chatDao.touchSession(sessionId)
        val message = ChatMessage(
            chatId = sessionId,
            sender = MessageSender.USER,
            content = text,
            status = MessageStatus.SENT,
            timestamp = System.currentTimeMillis()
        )
        val msgId = chatDao.insertMessage(message)
        val inserted = message.copy(id = msgId)
        firebaseManager.saveMessageToFirestore(inserted)

        val session = chatDao.getSessionById(sessionId)
        if (session != null) {
            firebaseManager.saveSessionToFirestore(session)
        }
        msgId
    }

    suspend fun saveInitialAiMessage(sessionId: Long): Long = withContext(Dispatchers.IO) {
        chatDao.touchSession(sessionId)
        val message = ChatMessage(
            chatId = sessionId,
            sender = MessageSender.AI,
            content = "",
            status = MessageStatus.STREAMING,
            timestamp = System.currentTimeMillis()
        )
        val msgId = chatDao.insertMessage(message)
        val inserted = message.copy(id = msgId)
        firebaseManager.saveMessageToFirestore(inserted)
        msgId
    }

    suspend fun updateMessageContent(
        messageId: Long,
        content: String,
        status: MessageStatus,
        errorMessage: String? = null
    ) = withContext(Dispatchers.IO) {
        val existing = chatDao.getMessageById(messageId)
        if (existing != null) {
            val updated = existing.copy(
                content = content,
                status = status,
                errorMessage = errorMessage
            )
            chatDao.updateMessage(updated)
            firebaseManager.saveMessageToFirestore(updated)
        }
    }

    suspend fun deleteMessage(messageId: Long) = withContext(Dispatchers.IO) {
        val existing = chatDao.getMessageById(messageId)
        if (existing != null) {
            val sessionId = existing.chatId
            chatDao.deleteMessage(messageId)
            firebaseManager.deleteMessageFromFirestore(sessionId, messageId)
        }
    }

    suspend fun shareSessionToFirestore(sessionId: Long): Result<String> = withContext(Dispatchers.IO) {
        val session = chatDao.getSessionById(sessionId)
            ?: return@withContext Result.failure(Exception("Session not found"))
        val messages = chatDao.getMessagesForSessionSync(sessionId)
        firebaseManager.shareChatToFirestore(session, messages)
    }

    suspend fun importSharedChat(shareId: String): Result<Long> = withContext(Dispatchers.IO) {
        val result = firebaseManager.getSharedChatFromFirestore(shareId)
        if (result.isFailure) {
            return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Import failed"))
        }

        val sharedData: SharedChatData = result.getOrThrow()
        val newSessionId = createNewSession(sharedData.title)

        for (msg in sharedData.messages) {
            val sender = try {
                MessageSender.valueOf(msg.sender)
            } catch (_: Exception) {
                if (msg.sender.equals("AI", true) || msg.sender.equals("ASSISTANT", true)) MessageSender.AI else MessageSender.USER
            }
            val chatMessage = ChatMessage(
                chatId = newSessionId,
                sender = sender,
                content = msg.content,
                status = MessageStatus.SENT,
                timestamp = msg.timestamp
            )
            val msgId = chatDao.insertMessage(chatMessage)
            firebaseManager.saveMessageToFirestore(chatMessage.copy(id = msgId))
        }

        Result.success(newSessionId)
    }

    suspend fun getConversationHistoryPayload(sessionId: Long): List<ChatMessagePayload> = withContext(Dispatchers.IO) {
        val messages = chatDao.getMessagesForSessionSync(sessionId)
        val contents = mutableListOf<ChatMessagePayload>()

        // Retain up to 40 recent messages for deep multi-turn conversation memory
        val validMessages = messages.filter {
            it.status != MessageStatus.ERROR && it.content.isNotBlank()
        }
        val recentMessages = if (validMessages.size > 40) validMessages.takeLast(40) else validMessages

        for (msg in recentMessages) {
            val role = when (msg.sender) {
                MessageSender.USER -> "user"
                MessageSender.AI -> "assistant"
                MessageSender.SYSTEM -> "user"
            }
            contents.add(
                ChatMessagePayload(
                    role = role,
                    content = msg.content
                )
            )
        }
        contents
    }

    fun streamChatResponse(
        prompt: String,
        history: List<ChatMessagePayload>,
        chatId: String
    ): Flow<StreamEvent> {
        val persona = when (preferencesManager.personality.value) {
            com.example.data.preferences.AiPersonality.BALANCED -> "normal"
            com.example.data.preferences.AiPersonality.CONCISE -> "concise"
            com.example.data.preferences.AiPersonality.CREATIVE -> "creative"
            com.example.data.preferences.AiPersonality.TECHNICAL -> "technical"
        }
        val model = preferencesManager.selectedModel.value

        // Filter out the current active user prompt if it was already recorded in history
        val priorHistory = if (history.isNotEmpty() && history.last().content.trim() == prompt.trim()) {
            history.dropLast(1)
        } else {
            history
        }

        return chatApiClient.streamChatResponse(
            prompt = prompt.trim(),
            messages = priorHistory,
            chatId = chatId,
            personaOverride = persona,
            modelOverride = model
        )
    }

    suspend fun autoGenerateTitleIfFirstMessage(sessionId: Long, firstPrompt: String) = withContext(Dispatchers.IO) {
        val session = chatDao.getSessionById(sessionId)
        if (session != null && (session.title == "New Chat" || session.title.isBlank())) {
            val generatedTitle = chatApiClient.generateTitleForChat(firstPrompt)
            chatDao.updateSessionTitle(sessionId, generatedTitle)
            val updated = session.copy(title = generatedTitle)
            firebaseManager.saveSessionToFirestore(updated)
        }
    }
}

