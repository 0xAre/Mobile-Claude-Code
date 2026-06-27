package com.zeroxare.claudemobile.data.api

import com.zeroxare.claudemobile.data.api.models.ClaudeMessage
import com.zeroxare.claudemobile.data.api.models.ClaudeRequest
import com.zeroxare.claudemobile.data.api.models.ClaudeResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ClaudeApiService {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val systemPrompt = """
        You are Claude Terminal Assistant — an AI coding assistant embedded inside a mobile terminal emulator.

        Your role:
        - Help users write, debug, and understand code
        - Explain shell commands and their output
        - Suggest commands for common tasks
        - Provide concise, actionable answers optimized for mobile reading
        - Format code in markdown code blocks
        - When the user shares terminal output, analyze it and provide targeted help

        Keep responses concise and mobile-friendly. Use short paragraphs and bullet points.
    """.trimIndent()

    suspend fun sendMessage(
        apiKey: String,
        messages: List<ClaudeMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = ClaudeRequest(
                messages = messages,
                system = systemPrompt
            )

            val bodyJson = json.encodeToString(requestBody)
            val body = bodyJson.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val claudeResponse = json.decodeFromString<ClaudeResponse>(responseBody)
                val text = claudeResponse.content.firstOrNull()?.text ?: "No response"
                Result.success(text)
            } else {
                Result.failure(Exception("API Error ${response.code}: $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
