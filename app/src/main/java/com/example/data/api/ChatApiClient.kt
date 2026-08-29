package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import okio.BufferedSource
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class StreamEvent {
    data class Chunk(val text: String) : StreamEvent()
    data class Done(val fullReply: String? = null) : StreamEvent()
    object Complete : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}

class ChatApiClient(
    private val getModel: () -> String = { "mini_flash" },
    private val getPersona: () -> String = { "normal" }
) {
    companion object {
        const val PWA_BASE_URL = "https://soft-truth-5517.pickleapi.workers.dev/api/chat"
        const val MILI_FLASH_BASE_URL = "https://pickle-api-worker.pickleapi.workers.dev/api/chat"
        const val MILI_FLASH_API_KEY = "sk_pickle_59ce96b06b7b40caadfad404"
    }

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val requestAdapter = moshi.adapter(ChatApiRequest::class.java)

    // Ultra-optimized OkHttpClient with persistent HTTP/2 connection pooling
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    init {
        // Pre-warm HTTP/2 connection pool in background for both endpoints
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pingPwa = Request.Builder()
                    .url("https://soft-truth-5517.pickleapi.workers.dev/health")
                    .head()
                    .build()
                okHttpClient.newCall(pingPwa).execute().close()
            } catch (ignored: Exception) {
            }
            try {
                val pingMili = Request.Builder()
                    .url("https://pickle-api-worker.pickleapi.workers.dev/health")
                    .head()
                    .build()
                okHttpClient.newCall(pingMili).execute().close()
            } catch (ignored: Exception) {
            }
        }
    }

    fun streamChatResponse(
        prompt: String,
        messages: List<ChatMessagePayload>,
        chatId: String = UUID.randomUUID().toString(),
        personaOverride: String? = null,
        modelOverride: String? = null
    ): Flow<StreamEvent> = callbackFlow {
        val rawModel = (modelOverride ?: getModel()).ifBlank { "mini_flash" }
        val isMiliFlash = rawModel == "mili_flash" || rawModel == "mini_flash" || rawModel.contains("flash", ignoreCase = true)
        val backendModel = when (rawModel) {
            "pwa_2_0_fast", "fast" -> "fast"
            else -> "C"
        }
        val persona = (personaOverride ?: getPersona()).ifBlank { "normal" }

        val targetUrl = if (isMiliFlash) MILI_FLASH_BASE_URL else PWA_BASE_URL

        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream, application/json")
            .addHeader("Connection", "keep-alive")

        if (isMiliFlash) {
            requestBuilder.addHeader("Authorization", "Bearer $MILI_FLASH_API_KEY")

            val jsonObject = JSONObject()
            jsonObject.put("message", prompt)
            jsonObject.put("stream", true)
            val historyArray = org.json.JSONArray()
            messages.forEach { msg ->
                val msgObj = JSONObject()
                msgObj.put("role", msg.role)
                msgObj.put("content", msg.content)
                historyArray.put(msgObj)
            }
            jsonObject.put("history", historyArray)

            val body = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            requestBuilder.post(body)
        } else {
            val jsonObject = JSONObject()
            jsonObject.put("message", prompt)
            jsonObject.put("format", "markdown")
            jsonObject.put("stream", true)
            jsonObject.put("model", backendModel)
            jsonObject.put("persona", persona)
            jsonObject.put("mode", "usual")
            jsonObject.put("chatId", "${chatId}_${UUID.randomUUID()}")

            // Full message history ending with the current user prompt (Standard multi-turn LLM format)
            val fullMessagesArray = org.json.JSONArray()
            messages.forEach { msg ->
                val msgObj = JSONObject()
                msgObj.put("role", msg.role)
                msgObj.put("content", msg.content)
                fullMessagesArray.put(msgObj)
            }
            // Ensure the latest prompt is appended to messages if not already the final item
            val lastIsCurrentPrompt = messages.isNotEmpty() &&
                    messages.last().role == "user" &&
                    messages.last().content.trim() == prompt.trim()
            if (!lastIsCurrentPrompt && prompt.isNotBlank()) {
                val currentPromptObj = JSONObject()
                currentPromptObj.put("role", "user")
                currentPromptObj.put("content", prompt)
                fullMessagesArray.put(currentPromptObj)
            }
            jsonObject.put("messages", fullMessagesArray)

            // Prior history array (excluding current prompt) for backends expecting separate history
            val priorHistoryArray = org.json.JSONArray()
            messages.forEach { msg ->
                if (!(msg.role == "user" && msg.content.trim() == prompt.trim())) {
                    val msgObj = JSONObject()
                    msgObj.put("role", msg.role)
                    msgObj.put("content", msg.content)
                    priorHistoryArray.put(msgObj)
                }
            }
            jsonObject.put("history", priorHistoryArray)

            val body = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            requestBuilder.post(body)
        }

        val request = requestBuilder.build()

        val call = okHttpClient.newCall(request)
        var response: Response? = null
        var source: BufferedSource? = null

        val thread = Thread {
            try {
                response = call.execute()
                if (response == null || !response!!.isSuccessful) {
                    val errBody = response?.body?.string() ?: ""
                    var errMessage: String? = null
                    try {
                        val parsed = JSONObject(errBody)
                        errMessage = parsed.optString("error", null)
                    } catch (e: Exception) {
                        // ignore
                    }
                    val message = errMessage ?: "Request failed with code ${response?.code}: ${response?.message}"
                    trySend(StreamEvent.Error(message))
                    close()
                    return@Thread
                }

                val responseBody = response?.body
                if (responseBody == null) {
                    trySend(StreamEvent.Error("Empty response received from chat API"))
                    close()
                    return@Thread
                }

                source = responseBody.source()
                var receivedAnyDelta = false

                while (!source!!.exhausted()) {
                    val currentLine = source!!.readUtf8Line()?.trim() ?: break
                    if (currentLine.isEmpty()) continue

                    val payload = if (currentLine.startsWith("data:")) {
                        currentLine.removePrefix("data:").trim()
                    } else {
                        currentLine
                    }

                    if (payload == "[DONE]") {
                        trySend(StreamEvent.Complete)
                        break
                    }

                    if (payload.startsWith("{") && payload.endsWith("}")) {
                        try {
                            val json = JSONObject(payload)
                            val ok = json.optBoolean("ok", true)
                            val success = json.optBoolean("success", true)
                            if ((!ok || !success) && json.has("error")) {
                                val errorMsg = json.optString("error", "Service returned an error.")
                                trySend(StreamEvent.Error(errorMsg))
                                close()
                                return@Thread
                            }

                            if (json.has("delta")) {
                                val delta = json.getString("delta")
                                if (delta.isNotEmpty()) {
                                    receivedAnyDelta = true
                                    trySend(StreamEvent.Chunk(delta))
                                }
                            }

                            if (json.optBoolean("done", false)) {
                                val finalReply = if (json.has("response")) {
                                    json.optString("response", "")
                                } else {
                                    json.optString("reply", "")
                                }
                                if (!receivedAnyDelta && finalReply.isNotEmpty()) {
                                    trySend(StreamEvent.Chunk(finalReply))
                                }
                                trySend(StreamEvent.Done(finalReply.ifBlank { null }))
                                trySend(StreamEvent.Complete)
                                close()
                                return@Thread
                            }

                            // Non-streamed response object support
                            if (!json.has("delta") && !json.has("done")) {
                                val reply = when {
                                    json.has("response") -> json.optString("response")
                                    json.has("reply") -> json.optString("reply")
                                    json.has("data") -> {
                                        val dataObj = json.optJSONObject("data")
                                        dataObj?.optString("response") ?: dataObj?.optString("reply") ?: ""
                                    }
                                    else -> ""
                                }
                                if (reply.isNotEmpty()) {
                                    trySend(StreamEvent.Chunk(reply))
                                    trySend(StreamEvent.Complete)
                                    close()
                                    return@Thread
                                }
                            }
                        } catch (e: Exception) {
                            // Non-json chunk or malformed line
                        }
                    }
                }

                trySend(StreamEvent.Complete)
                close()
            } catch (e: Exception) {
                if (!call.isCanceled()) {
                    trySend(StreamEvent.Error(e.localizedMessage ?: "Network connection failed"))
                }
                close()
            } finally {
                try {
                    source?.close()
                    response?.close()
                } catch (ignored: Exception) {
                }
            }
        }

        thread.start()

        awaitClose {
            call.cancel()
            try {
                source?.close()
                response?.close()
            } catch (ignored: Exception) {
            }
        }
    }

    suspend fun generateTitleForChat(firstUserMessage: String): String = withContext(Dispatchers.IO) {
        val cleanMsg = firstUserMessage.trim()
        if (cleanMsg.isBlank()) return@withContext "New Chat"

        val words = cleanMsg.split("\\s+".toRegex()).take(4)
        words.joinToString(" ").take(30).trim()
    }
}
