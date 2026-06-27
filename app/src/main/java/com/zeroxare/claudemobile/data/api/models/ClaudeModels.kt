package com.zeroxare.claudemobile.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClaudeRequest(
    val model: String = "claude-sonnet-4-6",
    @SerialName("max_tokens") val maxTokens: Int = 2048,
    val messages: List<ClaudeMessage>,
    val system: String? = null
)

@Serializable
data class ClaudeMessage(
    val role: String,
    val content: String
)

@Serializable
data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ContentBlock>,
    val model: String,
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: Usage
)

@Serializable
data class ContentBlock(
    val type: String,
    val text: String
)

@Serializable
data class Usage(
    @SerialName("input_tokens") val inputTokens: Int,
    @SerialName("output_tokens") val outputTokens: Int
)

@Serializable
data class ClaudeErrorResponse(
    val type: String,
    val error: ClaudeError
)

@Serializable
data class ClaudeError(
    val type: String,
    val message: String
)
