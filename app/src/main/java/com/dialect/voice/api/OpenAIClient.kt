package com.dialect.voice.api

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import java.io.File
import android.util.Base64

// Thrown when the chatCompletion Cloud Function rejects the call because the caller's text
// balance can't cover it. Separate from NoCreditException (voice) - see
// functions/src/lib/usage.ts for why text and voice are metered independently.
class NoTextCreditException(val remainingSeconds: Int) : Exception("Not enough text credit")

// Both calls go through Cloud Functions now (chatCompletion / transcribeAudio) - the OpenAI
// key lives server-side in Secret Manager, never in the APK. Method signatures are
// unchanged from the old direct-HTTP version so ChatViewModel's call sites don't need to
// change shape, only the constructor wiring in MainActivity does.
class OpenAIClient(private val functions: FirebaseFunctions) {

    suspend fun convertToDialect(text: String, systemPrompt: String): String {
        val data = hashMapOf(
            "userText" to text,
            "systemPrompt" to systemPrompt
        )
        try {
            val result = functions.getHttpsCallable("chatCompletion").call(data).await()
            @Suppress("UNCHECKED_CAST")
            val map = result.getData() as? Map<String, Any?>
            return map?.get("text") as? String ?: throw Exception("No response from OpenAI")
        } catch (e: FirebaseFunctionsException) {
            if (e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) {
                @Suppress("UNCHECKED_CAST")
                val details = e.details as? Map<String, Any?>
                val remainingSeconds = (details?.get("remainingSeconds") as? Number)?.toInt() ?: 0
                throw NoTextCreditException(remainingSeconds)
            }
            throw e
        }
    }

    suspend fun transcribeAudio(audioFile: File): String {
        val audioBase64 = Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP)
        val data = hashMapOf(
            "audioBase64" to audioBase64,
            "filename" to audioFile.name
        )
        val result = functions.getHttpsCallable("transcribeAudio").call(data).await()
        @Suppress("UNCHECKED_CAST")
        val map = result.getData() as? Map<String, Any?>
        return map?.get("text") as? String ?: throw Exception("No transcript returned")
    }
}
