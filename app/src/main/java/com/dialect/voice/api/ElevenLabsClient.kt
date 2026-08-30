package com.dialect.voice.api

import android.util.Base64
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

// Thrown when the synthesizeSpeech Cloud Function rejects the call because the caller's
// credit balance can't cover it - whether that's a never-purchased account (0 seconds) or an
// unlocked account that's run its balance down. Distinct from a generic Exception so
// ChatViewModel can render "buy more time" instead of a plain error, while leaving the
// already-visible text reply untouched.
class NoCreditException(val remainingSeconds: Int) : Exception("Not enough TTS credit")

// Calls the synthesizeSpeech Cloud Function - the ElevenLabs key and the credit-balance
// enforcement both live server-side now, never in the APK. Signature unchanged from the
// old direct-HTTP version.
class ElevenLabsClient(private val functions: FirebaseFunctions) {

    suspend fun synthesizeSpeech(text: String, voiceId: String): ByteArray {
        val data = hashMapOf(
            "text" to text,
            "voiceId" to voiceId
        )
        try {
            val result = functions.getHttpsCallable("synthesizeSpeech").call(data).await()
            @Suppress("UNCHECKED_CAST")
            val map = result.getData() as? Map<String, Any?>
                ?: throw Exception("Empty response from synthesizeSpeech")
            val audioBase64 = map["audioBase64"] as? String ?: throw Exception("No audio returned")
            return Base64.decode(audioBase64, Base64.NO_WRAP)
        } catch (e: FirebaseFunctionsException) {
            when (e.code) {
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> {
                    @Suppress("UNCHECKED_CAST")
                    val details = e.details as? Map<String, Any?>
                    val remainingSeconds = (details?.get("remainingSeconds") as? Number)?.toInt() ?: 0
                    throw NoCreditException(remainingSeconds)
                }
                else -> throw e
            }
        }
    }
}
