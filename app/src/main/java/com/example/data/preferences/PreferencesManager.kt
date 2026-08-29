package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AiModelInfo(
    val key: String,
    val displayName: String,
    val description: String,
    val badge: String
)

val AVAILABLE_AI_MODELS = listOf(
    AiModelInfo(
        key = "mini_flash",
        displayName = "Mini Flash",
        description = "Ultra-fast low latency neural engine via Pickle API (Default)",
        badge = "Flash"
    ),
    AiModelInfo(
        key = "pwa_2_0",
        displayName = "PWA 2.0",
        description = "Standard high-precision multi-turn conversational engine",
        badge = "2.0"
    )
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class AiPersonality(val displayName: String, val prompt: String) {
    BALANCED("Thoughtful & Balanced", "You are Somet AI powered by Mini Flash, a thoughtful, articulate, and helpful AI assistant. Provide clear, accurate, and structured responses with elegant formatting."),
    CONCISE("Concise & Direct", "You are Somet AI powered by Mini Flash. Provide direct, succinct, and distraction-free answers without unnecessary preamble."),
    CREATIVE("Creative & Expressive", "You are Somet AI powered by Mini Flash. Approach inquiries with creative depth, vivid analogies, and rich nuance while remaining accurate."),
    TECHNICAL("Engineering & Code", "You are Somet AI powered by Mini Flash. Focus on high precision, production-ready code examples, structured technical explanations, and edge-case awareness.")
}

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sonet_ai_preferences", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(getSavedThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _selectedModel = MutableStateFlow(prefs.getString(KEY_SELECTED_MODEL, "mini_flash") ?: "mini_flash")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _personality = MutableStateFlow(getSavedPersonality())
    val personality: StateFlow<AiPersonality> = _personality.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC, true))
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(prefs.getBoolean(KEY_IS_LOGGED_IN, false))
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _hasNameSetup = MutableStateFlow(prefs.getBoolean(KEY_HAS_NAME_SETUP, false))
    val hasNameSetup: StateFlow<Boolean> = _hasNameSetup.asStateFlow()

    private val _userName = MutableStateFlow(prefs.getString(KEY_USER_NAME, "") ?: "")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userEmail = MutableStateFlow(prefs.getString(KEY_USER_EMAIL, "") ?: "")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _userTitle = MutableStateFlow(prefs.getString(KEY_USER_TITLE, "Member") ?: "Member")
    val userTitle: StateFlow<String> = _userTitle.asStateFlow()

    private val _memberSince = MutableStateFlow(getOrInitMemberSince())
    val memberSince: StateFlow<Long> = _memberSince.asStateFlow()

    fun loginWithGoogle(name: String, email: String, title: String = "Google Account User") {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_TITLE, title)
            .apply()
        _isLoggedIn.value = true
        _userName.value = name
        _userEmail.value = email
        _userTitle.value = title
    }

    fun logout() {
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply()
        _isLoggedIn.value = false
    }

    fun setLoggedIn(loggedIn: Boolean) {
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, loggedIn).apply()
        _isLoggedIn.value = loggedIn
    }

    fun setUserName(name: String) {
        prefs.edit()
            .putString(KEY_USER_NAME, name)
            .putBoolean(KEY_HAS_NAME_SETUP, name.isNotBlank())
            .apply()
        _userName.value = name
        _hasNameSetup.value = name.isNotBlank()
    }

    fun completeNameSetup(name: String) {
        setUserName(name)
    }

    fun setUserEmail(email: String) {
        prefs.edit().putString(KEY_USER_EMAIL, email).apply()
        _userEmail.value = email
    }

    fun setUserTitle(title: String) {
        prefs.edit().putString(KEY_USER_TITLE, title).apply()
        _userTitle.value = title
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setSelectedModel(model: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model).apply()
        _selectedModel.value = model
    }

    fun setPersonality(personality: AiPersonality) {
        prefs.edit().putString(KEY_PERSONALITY, personality.name).apply()
        _personality.value = personality
    }

    fun setHapticEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply()
        _hapticEnabled.value = enabled
    }

    private fun getSavedThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(name ?: ThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    private fun getOrInitMemberSince(): Long {
        val saved = prefs.getLong(KEY_MEMBER_SINCE, 0L)
        return if (saved != 0L) {
            saved
        } else {
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_MEMBER_SINCE, now).apply()
            now
        }
    }

    private fun getSavedPersonality(): AiPersonality {
        val name = prefs.getString(KEY_PERSONALITY, AiPersonality.BALANCED.name)
        return try {
            AiPersonality.valueOf(name ?: AiPersonality.BALANCED.name)
        } catch (e: Exception) {
            AiPersonality.BALANCED
        }
    }

    companion object {
        private const val KEY_IS_LOGGED_IN = "key_is_logged_in"
        private const val KEY_HAS_NAME_SETUP = "key_has_name_setup"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_SELECTED_MODEL = "key_selected_model"
        private const val KEY_PERSONALITY = "key_personality"
        private const val KEY_HAPTIC = "key_haptic"
        private const val KEY_USER_NAME = "key_user_name"
        private const val KEY_USER_EMAIL = "key_user_email"
        private const val KEY_USER_TITLE = "key_user_title"
        private const val KEY_MEMBER_SINCE = "key_member_since"
    }
}
