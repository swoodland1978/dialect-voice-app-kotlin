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
    val audioFilePath: String? = null, // Cached local file once synthesized
    val presetAudioRes: Int? = null, // Bundled res/raw audio - bypasses ElevenLabs/paywall entirely
    val showBuyCreditLink: Boolean = false, // Persistent "Buy credit" CTA under this bubble (easter egg)
    val audioState: AudioState = AudioState.NONE,
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

// Audio is synthesized lazily (only when the user presses play) since TTS is the expensive API call.
enum class AudioState {
    NONE, SYNTHESIZING, READY, PLAYING, ERROR, NO_CREDIT
}

enum class RecordingState {
    IDLE, RECORDING, TRANSCRIBING
}

// OpenAI API models
@Serializable
data class OpenAIRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 100
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

// OpenAI error responses look like {"error": {"message": ..., "type": ..., "code": ...}}
@Serializable
data class OpenAIErrorResponse(
    val error: OpenAIError
)

@Serializable
data class OpenAIError(
    val message: String,
    val type: String? = null,
    val code: String? = null
)

// ElevenLabs TTS models
@Serializable
data class ElevenLabsRequest(
    val text: String,
    val model_id: String = "eleven_flash_v2_5",
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
