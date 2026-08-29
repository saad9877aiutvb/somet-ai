package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.api.ChatApiClient
import com.example.data.firebase.FirebaseManager
import com.example.data.local.AppDatabase
import com.example.data.preferences.PreferencesManager
import com.example.data.repository.ChatRepository

class ChatViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            val database = AppDatabase.getDatabase(context)
            val preferencesManager = PreferencesManager(context)
            val firebaseManager = FirebaseManager(context)
            val chatApiClient = ChatApiClient(
                getModel = { preferencesManager.selectedModel.value },
                getPersona = {
                    when (preferencesManager.personality.value) {
                        com.example.data.preferences.AiPersonality.BALANCED -> "normal"
                        com.example.data.preferences.AiPersonality.CONCISE -> "concise"
                        com.example.data.preferences.AiPersonality.CREATIVE -> "creative"
                        com.example.data.preferences.AiPersonality.TECHNICAL -> "technical"
                    }
                }
            )
            val repository = ChatRepository(
                chatDao = database.chatDao(),
                chatApiClient = chatApiClient,
                preferencesManager = preferencesManager,
                firebaseManager = firebaseManager
            )
            return ChatViewModel(repository, preferencesManager, firebaseManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
