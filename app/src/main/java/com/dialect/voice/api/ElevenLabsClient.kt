package com.dialect.voice.api

import com.dialect.voice.domain.ElevenLabsRequest
import com.dialect.voice.domain.VoiceSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import android.util.Base64

class ElevenLabsClient(private val apiKey: String) {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    suspend fun synthesizeSpeech(
        text: String,
        voiceId: String,
        stability: Double = 0.5,
        similarityBoost: Double = 0.75,
        style: Double = 1.0
    ): String {
        val request = ElevenLabsRequest(
            text = text,
            voice_settings = VoiceSettings(
                stability = stability,
                similarity_boost = similarityBoost,
                style = style
            )
        )

        val audioBytes = client.post("https://api.elevenlabs.io/v1/text-to-speech/$voiceId") {
            header("xi-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.readBytes()

        // Convert to base64 data URL for now
        // TODO: Upload to cloud storage and return public URL instead
        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        return "data:audio/mpeg;base64,$base64Audio"
    }

    fun close() {
        client.close()
    }
}
