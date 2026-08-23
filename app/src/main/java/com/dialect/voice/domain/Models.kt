package com.dialect.voice.domain

import kotlinx.serialization.Serializable

// Dialect definition with system prompt and ElevenLabs voice ID
data class Dialect(
    val id: String,
    val label: String,
    val description: String,
    val systemPrompt: String,
    val elevenLabsVoiceId: String
)

// Chat message model
data class Message(
    val id: String,
    val role: MessageRole, // "user" or "assistant"
    val text: String,
    val dialect: String? = null,
    val audioUrl: String? = null, // Base64 or cloud URL
    val status: MessageStatus = MessageStatus.DONE,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class MessageRole {
    USER, ASSISTANT
}

enum class MessageStatus {
    PENDING, DONE, ERROR
}

// OpenAI API models
@Serializable
data class OpenAIRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 500
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAIResponse(
    val choices: List<Choice>
)

@Serializable
data class Choice(
    val message: ChatMessage
)

// ElevenLabs TTS models
@Serializable
data class ElevenLabsRequest(
    val text: String,
    val model_id: String = "eleven_monolingual_v1",
    val voice_settings: VoiceSettings = VoiceSettings()
)

@Serializable
data class VoiceSettings(
    val stability: Double = 0.5,
    val similarity_boost: Double = 0.75,
    val style: Double = 1.0
)

// OpenAI Whisper transcription models
@Serializable
data class WhisperResponse(
    val text: String
)
