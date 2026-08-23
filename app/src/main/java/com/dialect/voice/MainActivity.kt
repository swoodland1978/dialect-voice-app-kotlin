package com.dialect.voice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dialect.voice.api.ElevenLabsClient
import com.dialect.voice.api.OpenAIClient
import com.dialect.voice.ui.ChatScreen
import com.dialect.voice.ui.theme.DialectVoiceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Load API keys from BuildConfig or secure storage
        val openAiApiKey = "YOUR_OPENAI_API_KEY_HERE"
        val elevenLabsApiKey = "YOUR_ELEVENLABS_API_KEY_HERE"

        val openAiClient = OpenAIClient(openAiApiKey)
        val elevenLabsClient = ElevenLabsClient(elevenLabsApiKey)

        setContent {
            DialectVoiceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatScreen(
                        openAiClient = openAiClient,
                        elevenLabsClient = elevenLabsClient
                    )
                }
            }
        }
    }
}
