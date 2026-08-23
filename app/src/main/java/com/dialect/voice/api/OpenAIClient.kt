package com.dialect.voice.api

import com.dialect.voice.domain.ChatMessage
import com.dialect.voice.domain.OpenAIRequest
import com.dialect.voice.domain.OpenAIResponse
import com.dialect.voice.domain.WhisperResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.File

class OpenAIClient(private val apiKey: String) {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    suspend fun convertToDialect(
        text: String,
        systemPrompt: String
    ): String {
        val request = OpenAIRequest(
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = text)
            )
        )

        val response: OpenAIResponse = client.post("https://api.openai.com/v1/chat/completions") {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        return response.choices.firstOrNull()?.message?.content 
            ?: throw Exception("No response from OpenAI")
    }

    suspend fun transcribeAudio(audioFile: File): String {
        // Note: Whisper API requires multipart/form-data upload
        // This is a simplified version - you may need to use a custom implementation
        // for proper multipart file upload with Ktor
        
        return try {
            val response: WhisperResponse = client.post("https://api.openai.com/v1/audio/transcriptions") {
                bearerAuth(apiKey)
                // TODO: Implement proper multipart upload
                // For now, return empty string as placeholder
            }.body()
            response.text
        } catch (e: Exception) {
            throw Exception("Transcription failed: ${e.message}")
        }
    }

    fun close() {
        client.close()
    }
}
