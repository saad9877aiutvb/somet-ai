package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChatMessagePayload(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class ChatApiRequest(
    @Json(name = "message") val message: String,
    @Json(name = "format") val format: String = "markdown",
    @Json(name = "stream") val stream: Boolean = true,
    @Json(name = "model") val model: String = "C",
    @Json(name = "persona") val persona: String = "normal",
    @Json(name = "mode") val mode: String = "usual",
    @Json(name = "chatId") val chatId: String? = null,
    @Json(name = "messages") val messages: List<ChatMessagePayload>? = null
)

@JsonClass(generateAdapter = true)
data class ChatApiResponse(
    @Json(name = "ok") val ok: Boolean? = null,
    @Json(name = "reply") val reply: String? = null,
    @Json(name = "delta") val delta: String? = null,
    @Json(name = "done") val done: Boolean? = null,
    @Json(name = "started") val started: Boolean? = null,
    @Json(name = "error") val error: String? = null,
    @Json(name = "chatId") val chatId: String? = null,
    @Json(name = "requestId") val requestId: String? = null
)
