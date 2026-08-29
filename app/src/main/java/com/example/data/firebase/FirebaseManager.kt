package com.example.data.firebase

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.data.local.ChatMessage
import com.example.data.local.ChatSession
import com.example.data.local.MessageSender
import com.example.data.local.MessageStatus
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

data class SharedChatData(
    val shareId: String = "",
    val title: String = "",
    val createdAt: Long = 0L,
    val authorName: String = "",
    val messageCount: Int = 0,
    val messages: List<SharedChatMessageData> = emptyList()
)

data class SharedChatMessageData(
    val sender: String = "",
    val content: String = "",
    val timestamp: Long = 0L
)

class FirebaseManager(private val context: Context) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val credentialManager = CredentialManager.create(context)

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(System.currentTimeMillis())
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private var sessionsListenerRegistration: ListenerRegistration? = null

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _currentUser.value = user
            if (user != null) {
                // Ensure user doc exists in Firestore
                syncUserProfile(user)
            }
        }
    }

    val isUserSignedIn: Boolean
        get() = auth.currentUser != null

    val currentUserId: String?
        get() = auth.currentUser?.uid

    val currentUserName: String
        get() = auth.currentUser?.displayName?.ifBlank { null }
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: ""

    val currentUserEmail: String
        get() = auth.currentUser?.email ?: ""

    // ---------------- AUTHENTICATION METHODS ---------------- //

    suspend fun signInWithEmail(email: String, pass: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val authResult = auth.signInWithEmailAndPassword(email.trim(), pass).await()
            val user = authResult.user ?: throw Exception("Sign in succeeded but user is null")
            syncUserProfile(user)
            Result.success(user)
        } catch (e: Exception) {
            Log.e("FirebaseManager", "signInWithEmail error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(email: String, pass: String, displayName: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val authResult = auth.createUserWithEmailAndPassword(email.trim(), pass).await()
            val user = authResult.user ?: throw Exception("Sign up succeeded but user is null")

            if (displayName.isNotBlank()) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName.trim())
                    .build()
                user.updateProfile(profileUpdates).await()
            }

            syncUserProfile(user, initialName = displayName)
            Result.success(user)
        } catch (e: Exception) {
            Log.e("FirebaseManager", "signUpWithEmail error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(webClientId: String? = null): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            // Setup Google ID Option
            val googleIdOption = GetSignInWithGoogleOption.Builder(
                serverClientId = webClientId ?: "597388818339-default.apps.googleusercontent.com"
            ).build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response: GetCredentialResponse = credentialManager.getCredential(
                context = context,
                request = request
            )

            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken

                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                val user = authResult.user ?: throw Exception("Google sign in completed but user is null")
                syncUserProfile(user)
                Result.success(user)
            } else {
                Result.failure(Exception("Unexpected credential type returned from Google"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d("FirebaseManager", "User cancelled Google sign in dialog")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("FirebaseManager", "signInWithGoogle error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithCustomGoogleAccount(
        name: String,
        email: String
    ): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        // Fallback for emulator / dev environments where Google Play Services credentials dialog may not be installed
        try {
            // Check if anonymous or email user already exists
            val existing = auth.currentUser
            if (existing != null) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(name.trim())
                    .build()
                existing.updateProfile(profileUpdates).await()
                syncUserProfile(existing, initialName = name)
                Result.success(existing)
            } else {
                // Try create standard dev user
                val pseudoPass = "SometAi_" + email.hashCode().toString().replace("-", "x") + "!2026"
                try {
                    val result = auth.signInWithEmailAndPassword(email.trim(), pseudoPass).await()
                    val user = result.user!!
                    syncUserProfile(user, initialName = name)
                    Result.success(user)
                } catch (_: Exception) {
                    val createResult = auth.createUserWithEmailAndPassword(email.trim(), pseudoPass).await()
                    val user = createResult.user!!
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(name.trim())
                        .build()
                    user.updateProfile(profileUpdates).await()
                    syncUserProfile(user, initialName = name)
                    Result.success(user)
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "signInWithCustomGoogleAccount error: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun signOut() {
        sessionsListenerRegistration?.remove()
        sessionsListenerRegistration = null
        auth.signOut()
        _currentUser.value = null
    }

    // ---------------- FIRESTORE USER PROFILE ---------------- //

    private fun syncUserProfile(user: FirebaseUser, initialName: String? = null) {
        try {
            val userRef = firestore.collection("users").document(user.uid)
            val name = initialName ?: user.displayName ?: user.email?.substringBefore("@") ?: "User"
            val email = user.email ?: ""

            val profileData = hashMapOf(
                "uid" to user.uid,
                "displayName" to name,
                "email" to email,
                "photoUrl" to (user.photoUrl?.toString() ?: ""),
                "lastActive" to FieldValue.serverTimestamp(),
                "updatedAt" to System.currentTimeMillis()
            )
            userRef.set(profileData, SetOptions.merge())
        } catch (e: Exception) {
            Log.e("FirebaseManager", "syncUserProfile error: ${e.message}")
        }
    }

    // ---------------- FIRESTORE REALTIME SYNC (CHATS & MESSAGES) ---------------- //

    suspend fun saveSessionToFirestore(session: ChatSession) = withContext(Dispatchers.IO) {
        val uid = currentUserId ?: return@withContext
        try {
            _isCloudSyncing.value = true
            val sessionDoc = firestore.collection("users")
                .document(uid)
                .collection("sessions")
                .document(session.id.toString())

            val data = hashMapOf(
                "id" to session.id,
                "title" to session.title,
                "createdAt" to session.createdAt,
                "updatedAt" to session.updatedAt,
                "isPinned" to session.isPinned,
                "lastSyncedAt" to System.currentTimeMillis()
            )

            sessionDoc.set(data, SetOptions.merge()).await()
            _lastSyncTimestamp.value = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e("FirebaseManager", "saveSessionToFirestore error: ${e.message}")
        } finally {
            _isCloudSyncing.value = false
        }
    }

    suspend fun saveMessageToFirestore(message: ChatMessage) = withContext(Dispatchers.IO) {
        val uid = currentUserId ?: return@withContext
        try {
            val messageDoc = firestore.collection("users")
                .document(uid)
                .collection("sessions")
                .document(message.chatId.toString())
                .collection("messages")
                .document(message.id.toString())

            val data = hashMapOf(
                "id" to message.id,
                "chatId" to message.chatId,
                "sender" to message.sender.name,
                "content" to message.content,
                "status" to message.status.name,
                "timestamp" to message.timestamp,
                "errorMessage" to (message.errorMessage ?: "")
            )

            messageDoc.set(data, SetOptions.merge()).await()
            _lastSyncTimestamp.value = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e("FirebaseManager", "saveMessageToFirestore error: ${e.message}")
        }
    }

    suspend fun deleteSessionFromFirestore(sessionId: Long) = withContext(Dispatchers.IO) {
        val uid = currentUserId ?: return@withContext
        try {
            val sessionDoc = firestore.collection("users")
                .document(uid)
                .collection("sessions")
                .document(sessionId.toString())

            // Delete all subcollection messages
            val messagesSnapshot = sessionDoc.collection("messages").get().await()
            for (doc in messagesSnapshot.documents) {
                doc.reference.delete()
            }
            sessionDoc.delete().await()
        } catch (e: Exception) {
            Log.e("FirebaseManager", "deleteSessionFromFirestore error: ${e.message}")
        }
    }

    suspend fun deleteMessageFromFirestore(sessionId: Long, messageId: Long) = withContext(Dispatchers.IO) {
        val uid = currentUserId ?: return@withContext
        try {
            firestore.collection("users")
                .document(uid)
                .collection("sessions")
                .document(sessionId.toString())
                .collection("messages")
                .document(messageId.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e("FirebaseManager", "deleteMessageFromFirestore error: ${e.message}")
        }
    }

    suspend fun clearAllHistoryFromFirestore() = withContext(Dispatchers.IO) {
        val uid = currentUserId ?: return@withContext
        try {
            val sessionsSnapshot = firestore.collection("users")
                .document(uid)
                .collection("sessions")
                .get()
                .await()

            for (sessionDoc in sessionsSnapshot.documents) {
                val messages = sessionDoc.reference.collection("messages").get().await()
                for (m in messages.documents) {
                    m.reference.delete()
                }
                sessionDoc.reference.delete()
            }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "clearAllHistoryFromFirestore error: ${e.message}")
        }
    }

    // ---------------- CHAT SHARING VIA FIRESTORE ---------------- //

    suspend fun shareChatToFirestore(
        session: ChatSession,
        messages: List<ChatMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            _isCloudSyncing.value = true
            val shareId = "share_" + UUID.randomUUID().toString().take(10)
            val shareDoc = firestore.collection("shared_chats").document(shareId)

            val messagesList = messages.map { msg ->
                hashMapOf(
                    "sender" to msg.sender.name,
                    "content" to msg.content,
                    "timestamp" to msg.timestamp
                )
            }

            val shareData = hashMapOf(
                "shareId" to shareId,
                "title" to session.title,
                "createdAt" to System.currentTimeMillis(),
                "authorName" to (currentUserName.ifBlank { "Somet AI User" }),
                "authorId" to (currentUserId ?: "anonymous"),
                "messageCount" to messages.size,
                "messages" to messagesList
            )

            shareDoc.set(shareData).await()
            _lastSyncTimestamp.value = System.currentTimeMillis()
            Result.success(shareId)
        } catch (e: Exception) {
            Log.e("FirebaseManager", "shareChatToFirestore error: ${e.message}", e)
            Result.failure(e)
        } finally {
            _isCloudSyncing.value = false
        }
    }

    suspend fun getSharedChatFromFirestore(shareId: String): Result<SharedChatData> = withContext(Dispatchers.IO) {
        try {
            val doc = firestore.collection("shared_chats").document(shareId).get().await()
            if (!doc.exists()) {
                return@withContext Result.failure(Exception("Shared conversation not found or expired"))
            }

            val title = doc.getString("title") ?: "Shared Conversation"
            val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
            val authorName = doc.getString("authorName") ?: "Somet AI User"
            val messageCount = doc.getLong("messageCount")?.toInt() ?: 0

            @Suppress("UNCHECKED_CAST")
            val rawMessages = doc.get("messages") as? List<Map<String, Any>> ?: emptyList()
            val parsedMessages = rawMessages.map { m ->
                SharedChatMessageData(
                    sender = m["sender"] as? String ?: "USER",
                    content = m["content"] as? String ?: "",
                    timestamp = (m["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                )
            }

            val sharedData = SharedChatData(
                shareId = shareId,
                title = title,
                createdAt = createdAt,
                authorName = authorName,
                messageCount = messageCount,
                messages = parsedMessages
            )
            Result.success(sharedData)
        } catch (e: Exception) {
            Log.e("FirebaseManager", "getSharedChatFromFirestore error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
