package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.StreamEvent
import com.example.data.local.ChatMessage
import com.example.data.local.ChatSession
import com.example.data.local.MessageSender
import com.example.data.local.MessageStatus
import com.example.data.preferences.AiPersonality
import com.example.data.preferences.PreferencesManager
import com.example.data.preferences.ThemeMode
import com.example.data.repository.ChatRepository
import com.example.data.firebase.FirebaseManager
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UserProfileStats(
    val totalChats: Int = 0,
    val totalMessages: Int = 0,
    val userMessagesCount: Int = 0,
    val aiMessagesCount: Int = 0,
    val totalTokensUsed: Long = 0,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val totalWords: Long = 0,
    val pinnedChatsCount: Int = 0,
    val averageResponseLengthChars: Int = 0,
    val estimatedDbSizeKb: Double = 0.0,
    val firstChatDate: Long? = null,
    val mostRecentActiveDate: Long? = null
)

class ChatViewModel(
    private val repository: ChatRepository,
    private val preferencesManager: PreferencesManager,
    val firebaseManager: FirebaseManager
) : ViewModel() {

    val currentUser: StateFlow<FirebaseUser?> = firebaseManager.currentUser
    val isCloudSyncing: StateFlow<Boolean> = firebaseManager.isCloudSyncing
    val lastSyncTimestamp: StateFlow<Long> = firebaseManager.lastSyncTimestamp

    init {
        // Sync local preferences with Firebase user
        viewModelScope.launch {
            firebaseManager.currentUser.collect { user ->
                if (user != null) {
                    val name = user.displayName ?: preferencesManager.userName.value.ifBlank { user.email?.substringBefore("@") ?: "User" }
                    val email = user.email ?: preferencesManager.userEmail.value
                    preferencesManager.setLoggedIn(true)
                    preferencesManager.setUserName(name)
                    preferencesManager.setUserEmail(email)
                }
            }
        }
    }

    val sessions: StateFlow<List<ChatSession>> = repository.allSessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    // High-speed in-memory streaming overlay: provides 0ms token updates
    private val _streamingOverlay = MutableStateFlow<Map<Long, Pair<String, MessageStatus>>>(emptyMap())

    val currentMessages: StateFlow<List<ChatMessage>> = combine(
        _activeSessionId.flatMapLatest { sessionId ->
            if (sessionId != null) {
                repository.getMessagesForSession(sessionId)
            } else {
                flowOf(emptyList())
            }
        },
        _streamingOverlay
    ) { dbMessages, overlayMap ->
        if (overlayMap.isEmpty()) {
            dbMessages
        } else {
            dbMessages.map { msg ->
                val overlay = overlayMap[msg.id]
                if (overlay != null) {
                    msg.copy(content = overlay.first, status = overlay.second)
                } else {
                    msg
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = preferencesManager.themeMode
    val selectedModel: StateFlow<String> = preferencesManager.selectedModel
    val personality: StateFlow<AiPersonality> = preferencesManager.personality
    val hapticEnabled: StateFlow<Boolean> = preferencesManager.hapticEnabled

    val isLoggedIn: StateFlow<Boolean> = preferencesManager.isLoggedIn
    val hasNameSetup: StateFlow<Boolean> = preferencesManager.hasNameSetup
    val userName: StateFlow<String> = preferencesManager.userName
    val userEmail: StateFlow<String> = preferencesManager.userEmail
    val userTitle: StateFlow<String> = preferencesManager.userTitle
    val memberSince: StateFlow<Long> = preferencesManager.memberSince

    val userStats: StateFlow<UserProfileStats> = combine(
        repository.allSessions,
        repository.allMessages
    ) { sessionList, messageList ->
        val totalSessions = sessionList.size
        val pinnedCount = sessionList.count { it.isPinned }
        val userMessages = messageList.filter { it.sender == MessageSender.USER }
        val aiMessages = messageList.filter { it.sender == MessageSender.AI && it.status != MessageStatus.ERROR }

        var promptTokens = 0L
        var promptWords = 0L
        for (msg in userMessages) {
            val len = msg.content.trim().length
            promptTokens += (len / 4 + if (len % 4 != 0) 1 else 0)
            promptWords += msg.content.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
        }

        var completionTokens = 0L
        var completionWords = 0L
        var totalAiChars = 0L
        for (msg in aiMessages) {
            val len = msg.content.trim().length
            totalAiChars += len
            completionTokens += (len / 4 + if (len % 4 != 0) 1 else 0)
            completionWords += msg.content.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
        }

        val totalTokens = promptTokens + completionTokens
        val totalWords = promptWords + completionWords
        val avgAiChars = if (aiMessages.isNotEmpty()) (totalAiChars / aiMessages.size).toInt() else 0

        // Approximate DB storage: ~500 bytes overhead + character data
        val totalCharBytes = messageList.sumOf { it.content.length.toLong() }
        val estimatedSizeKb = ((messageList.size * 256 + totalCharBytes) / 1024.0).coerceAtLeast(0.0)

        val firstChatTime = sessionList.minOfOrNull { it.createdAt } ?: preferencesManager.memberSince.value
        val mostRecentTime = sessionList.maxOfOrNull { it.updatedAt }

        UserProfileStats(
            totalChats = totalSessions,
            totalMessages = messageList.size,
            userMessagesCount = userMessages.size,
            aiMessagesCount = aiMessages.size,
            totalTokensUsed = totalTokens,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalWords = totalWords,
            pinnedChatsCount = pinnedCount,
            averageResponseLengthChars = avgAiChars,
            estimatedDbSizeKb = estimatedSizeKb,
            firstChatDate = firstChatTime,
            mostRecentActiveDate = mostRecentTime
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserProfileStats()
    )

    private var currentStreamJob: Job? = null
    private var currentAiMessageId: Long? = null

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    fun selectSession(sessionId: Long) {
        if (_isGenerating.value) {
            stopGeneration()
        }
        _activeSessionId.value = sessionId
    }

    fun startNewChat() {
        if (_isGenerating.value) {
            stopGeneration()
        }
        _activeSessionId.value = null
        _inputText.value = ""
    }

    fun sendMessage(userText: String = _inputText.value) {
        val trimmed = userText.trim()
        if (trimmed.isBlank() || _isGenerating.value) return

        _inputText.value = ""
        _isGenerating.value = true

        currentStreamJob = viewModelScope.launch {
            // Ensure session exists
            var sessionId = _activeSessionId.value
            val isFirstMessageInSession = sessionId == null

            if (sessionId == null) {
                sessionId = repository.createNewSession(title = trimmed.take(28))
                _activeSessionId.value = sessionId
            }

            // 1. Save user message to DB
            repository.saveUserMessage(sessionId, trimmed)

            // Auto-generate title in background
            if (isFirstMessageInSession) {
                launch {
                    repository.autoGenerateTitleIfFirstMessage(sessionId, trimmed)
                }
            }

            // 2. Prepare AI message placeholder
            val aiMsgId = repository.saveInitialAiMessage(sessionId)
            currentAiMessageId = aiMsgId

            // 3. Retrieve conversation history payload
            val history = repository.getConversationHistoryPayload(sessionId)

            val accumulatedContent = StringBuilder()
            var lastDbPersistTime = System.currentTimeMillis()

            // 4. Stream response with 0ms in-memory rendering
            try {
                repository.streamChatResponse(
                    prompt = trimmed,
                    history = history,
                    chatId = "sonet_session_$sessionId"
                ).collect { event ->
                    when (event) {
                        is StreamEvent.Chunk -> {
                            accumulatedContent.append(event.text)
                            val rawText = accumulatedContent.toString()
                            val currentText = cleanTranscriptEcho(rawText)

                            // Instant 0ms memory update for 120fps UI
                            _streamingOverlay.value = _streamingOverlay.value + (aiMsgId to Pair(currentText, MessageStatus.STREAMING))

                            // Throttled background DB save (every 1.5s) to eliminate SQLite lock contention
                            val now = System.currentTimeMillis()
                            if (now - lastDbPersistTime > 1500) {
                                lastDbPersistTime = now
                                launch {
                                    repository.updateMessageContent(
                                        messageId = aiMsgId,
                                        content = currentText,
                                        status = MessageStatus.STREAMING
                                    )
                                }
                            }
                        }

                        is StreamEvent.Done -> {
                            if (event.fullReply != null && accumulatedContent.isEmpty()) {
                                accumulatedContent.append(event.fullReply)
                            }
                            val finalContent = cleanTranscriptEcho(accumulatedContent.toString())
                            _streamingOverlay.value = _streamingOverlay.value + (aiMsgId to Pair(finalContent, MessageStatus.SENT))
                            repository.updateMessageContent(
                                messageId = aiMsgId,
                                content = finalContent,
                                status = MessageStatus.SENT
                            )
                        }

                        is StreamEvent.Complete -> {
                            val finalContent = cleanTranscriptEcho(accumulatedContent.toString())
                            repository.updateMessageContent(
                                messageId = aiMsgId,
                                content = finalContent,
                                status = MessageStatus.SENT
                            )
                            _streamingOverlay.value = _streamingOverlay.value - aiMsgId
                            _isGenerating.value = false
                        }

                        is StreamEvent.Error -> {
                            val finalContent = accumulatedContent.toString()
                            repository.updateMessageContent(
                                messageId = aiMsgId,
                                content = finalContent,
                                status = MessageStatus.ERROR,
                                errorMessage = event.message
                            )
                            _streamingOverlay.value = _streamingOverlay.value - aiMsgId
                            _isGenerating.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                val finalContent = accumulatedContent.toString()
                repository.updateMessageContent(
                    messageId = aiMsgId,
                    content = finalContent,
                    status = MessageStatus.ERROR,
                    errorMessage = e.localizedMessage ?: "Failed to generate response"
                )
                _streamingOverlay.value = _streamingOverlay.value - aiMsgId
                _isGenerating.value = false
            } finally {
                _isGenerating.value = false
                currentAiMessageId = null
            }
        }
    }

    fun stopGeneration() {
        currentStreamJob?.cancel()
        currentStreamJob = null
        val aiMsgId = currentAiMessageId
        if (aiMsgId != null) {
            val overlay = _streamingOverlay.value[aiMsgId]
            val finalContent = overlay?.first?.ifBlank { "Response stopped." } ?: "Response stopped."
            _streamingOverlay.value = _streamingOverlay.value - aiMsgId
            viewModelScope.launch {
                repository.updateMessageContent(
                    messageId = aiMsgId,
                    content = finalContent,
                    status = MessageStatus.SENT
                )
            }
        }
        _isGenerating.value = false
        currentAiMessageId = null
    }

    fun retryLastMessage(aiMessage: ChatMessage) {
        val sessionId = _activeSessionId.value ?: return
        viewModelScope.launch {
            repository.deleteMessage(aiMessage.id)
            val history = repository.getConversationHistoryPayload(sessionId)
            if (history.isEmpty()) return@launch

            val lastUserMsg = history.lastOrNull { it.role == "user" }?.content ?: ""

            _isGenerating.value = true
            val aiMsgId = repository.saveInitialAiMessage(sessionId)
            currentAiMessageId = aiMsgId

            val accumulatedContent = StringBuilder()
            var lastDbPersistTime = System.currentTimeMillis()

            try {
                repository.streamChatResponse(
                    prompt = lastUserMsg,
                    history = history,
                    chatId = "sonet_session_$sessionId"
                ).collect { event ->
                    when (event) {
                        is StreamEvent.Chunk -> {
                            accumulatedContent.append(event.text)
                            val currentText = accumulatedContent.toString()

                            _streamingOverlay.value = _streamingOverlay.value + (aiMsgId to Pair(currentText, MessageStatus.STREAMING))

                            val now = System.currentTimeMillis()
                            if (now - lastDbPersistTime > 1500) {
                                lastDbPersistTime = now
                                launch {
                                    repository.updateMessageContent(
                                        messageId = aiMsgId,
                                        content = currentText,
                                        status = MessageStatus.STREAMING
                                    )
                                }
                            }
                        }

                        is StreamEvent.Done -> {
                            if (event.fullReply != null && accumulatedContent.isEmpty()) {
                                accumulatedContent.append(event.fullReply)
                            }
                            val finalContent = accumulatedContent.toString()
                            _streamingOverlay.value = _streamingOverlay.value + (aiMsgId to Pair(finalContent, MessageStatus.SENT))
                            repository.updateMessageContent(
                                messageId = aiMsgId,
                                content = finalContent,
                                status = MessageStatus.SENT
                            )
                        }

                        is StreamEvent.Complete -> {
                            val finalContent = accumulatedContent.toString()
                            repository.updateMessageContent(
                                messageId = aiMsgId,
                                content = finalContent,
                                status = MessageStatus.SENT
                            )
                            _streamingOverlay.value = _streamingOverlay.value - aiMsgId
                            _isGenerating.value = false
                        }

                        is StreamEvent.Error -> {
                            val finalContent = accumulatedContent.toString()
                            repository.updateMessageContent(
                                messageId = aiMsgId,
                                content = finalContent,
                                status = MessageStatus.ERROR,
                                errorMessage = event.message
                            )
                            _streamingOverlay.value = _streamingOverlay.value - aiMsgId
                            _isGenerating.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                val finalContent = accumulatedContent.toString()
                repository.updateMessageContent(
                    messageId = aiMsgId,
                    content = finalContent,
                    status = MessageStatus.ERROR,
                    errorMessage = e.localizedMessage ?: "Failed to generate response"
                )
                _streamingOverlay.value = _streamingOverlay.value - aiMsgId
                _isGenerating.value = false
            } finally {
                _isGenerating.value = false
                currentAiMessageId = null
            }
        }
    }

    fun renameSession(sessionId: Long, newTitle: String) {
        viewModelScope.launch {
            repository.updateSessionTitle(sessionId, newTitle)
        }
    }

    fun togglePinSession(sessionId: Long, isPinned: Boolean) {
        viewModelScope.launch {
            repository.togglePinSession(sessionId, isPinned)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            if (_activeSessionId.value == sessionId) {
                _activeSessionId.value = null
            }
            repository.deleteSession(sessionId)
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            stopGeneration()
            _activeSessionId.value = null
            repository.clearAllHistory()
        }
    }

    // Settings actions
    fun setThemeMode(mode: ThemeMode) {
        preferencesManager.setThemeMode(mode)
    }

    fun setSelectedModel(model: String) {
        preferencesManager.setSelectedModel(model)
    }

    fun setAiPersonality(personality: AiPersonality) {
        preferencesManager.setPersonality(personality)
    }

    fun setHapticEnabled(enabled: Boolean) {
        preferencesManager.setHapticEnabled(enabled)
    }

    // Real Firebase Authentication Actions
    fun signInWithEmail(email: String, pass: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = firebaseManager.signInWithEmail(email, pass)
            if (result.isSuccess) {
                val user = result.getOrNull()
                val name = user?.displayName ?: email.substringBefore("@")
                preferencesManager.loginWithGoogle(name, email, "Cloud Member")
                onComplete(true, null)
            } else {
                onComplete(false, result.exceptionOrNull()?.localizedMessage ?: "Failed to sign in")
            }
        }
    }

    fun signUpWithEmail(email: String, pass: String, name: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = firebaseManager.signUpWithEmail(email, pass, name)
            if (result.isSuccess) {
                preferencesManager.loginWithGoogle(name, email, "Cloud Member")
                onComplete(true, null)
            } else {
                onComplete(false, result.exceptionOrNull()?.localizedMessage ?: "Failed to create account")
            }
        }
    }

    fun sendPasswordResetEmail(email: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = firebaseManager.sendPasswordResetEmail(email)
            if (result.isSuccess) {
                onComplete(true, null)
            } else {
                onComplete(false, result.exceptionOrNull()?.localizedMessage ?: "Failed to send reset email")
            }
        }
    }

    fun signInWithGoogleNative(onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = firebaseManager.signInWithGoogle()
            if (result.isSuccess) {
                val user = result.getOrNull()
                val name = user?.displayName ?: user?.email?.substringBefore("@") ?: "Google User"
                val email = user?.email ?: ""
                preferencesManager.loginWithGoogle(name, email, "Google Verified")
                onComplete(true, null)
            } else {
                onComplete(false, result.exceptionOrNull()?.localizedMessage ?: "Google Sign-In failed")
            }
        }
    }

    fun signInWithCustomGoogleAccount(name: String, email: String, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = firebaseManager.signInWithCustomGoogleAccount(name, email)
            if (result.isSuccess) {
                preferencesManager.loginWithGoogle(name, email, "Google Account")
                onComplete(true, null)
            } else {
                onComplete(false, result.exceptionOrNull()?.localizedMessage ?: "Failed to sign in")
            }
        }
    }

    fun logout() {
        firebaseManager.signOut()
        preferencesManager.logout()
    }

    // Chat Sharing
    fun shareActiveChat(onComplete: (Result<String>) -> Unit) {
        val sessionId = _activeSessionId.value
        if (sessionId == null) {
            onComplete(Result.failure(Exception("No active chat to share")))
            return
        }
        viewModelScope.launch {
            val res = repository.shareSessionToFirestore(sessionId)
            onComplete(res)
        }
    }

    fun importSharedChat(shareId: String, onComplete: (Result<Long>) -> Unit) {
        viewModelScope.launch {
            val res = repository.importSharedChat(shareId)
            if (res.isSuccess) {
                _activeSessionId.value = res.getOrNull()
            }
            onComplete(res)
        }
    }

    fun setUserName(name: String) {
        preferencesManager.setUserName(name)
    }

    fun completeNameSetup(name: String) {
        preferencesManager.completeNameSetup(name)
    }

    fun setUserEmail(email: String) {
        preferencesManager.setUserEmail(email)
    }

    fun setUserTitle(title: String) {
        preferencesManager.setUserTitle(title)
    }

    private fun cleanTranscriptEcho(text: String): String {
        return text.replace(
            Regex("^(?:(?:\\[Prior Conversation History\\]|\\[Current User Question / Instruction\\]|User|Sonet AI|Somet AI|Assistant):\\s*[^\\n]*\\n?)+", RegexOption.IGNORE_CASE),
            ""
        ).trimStart()
    }
}
