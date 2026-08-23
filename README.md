# Dialect Voice App — Android Kotlin Edition

A native Android app that lets users chat with an AI that responds in authentic regional British accents (Geordie, Scouse, Glaswegian, Welsh, Scottish).

**How it works:**
1. User types or speaks a message
2. Message is sent to Claude (OpenAI API)
3. Claude responds with a system prompt to respond in the chosen dialect
4. Response is sent to ElevenLabs Text-to-Speech
5. Audio plays back with the regional accent

## Setup

### Prerequisites
- Android Studio Hedgehog or later
- Android SDK 26+
- Gradle 8.1+

### 1. Clone & Open

```bash
git clone <this-repo>
cd dialect-voice-app-kotlin
```

Open in Android Studio.

### 2. Get API Keys

- **OpenAI API Key**: https://platform.openai.com/api-keys
- **ElevenLabs API Key**: https://elevenlabs.io → Profile → API Keys

### 3. Configure Keys

Open `app/src/main/java/com/dialect/voice/MainActivity.kt` and replace:

```kotlin
val openAiApiKey = "YOUR_OPENAI_API_KEY_HERE"
val elevenLabsApiKey = "YOUR_ELEVENLABS_API_KEY_HERE"
```

**For production**, use Android BuildConfig variables:
```kotlin
// In app/build.gradle.kts
buildTypes {
    release {
        buildConfigField("String", "OPENAI_API_KEY", "\"${System.getenv("OPENAI_API_KEY")}\"")
        buildConfigField("String", "ELEVENLABS_API_KEY", "\"${System.getenv("ELEVENLABS_API_KEY")}\"")
    }
}
```

Then reference in code:
```kotlin
val openAiApiKey = BuildConfig.OPENAI_API_KEY
val elevenLabsApiKey = BuildConfig.ELEVENLABS_API_KEY
```

### 4. Map ElevenLabs Voice IDs

Open `app/src/main/java/com/dialect/voice/domain/Dialects.kt`

Replace each `REPLACE_WITH_..._VOICE_ID` with real ElevenLabs voice IDs:

1. Go to https://elevenlabs.io/voice-library
2. Search for regional UK accents (Geordie, Scouse, Scottish, Welsh)
3. Copy the `voice_id` for each
4. Paste into `Dialects.kt`

**Or train custom voices:**
- Use ElevenLabs' Voice Cloning for more authentic accents
- https://elevenlabs.io/docs/voices/professional-voice-cloning

### 5. Build & Run

```bash
# Debug build
./gradlew installDebug

# Or in Android Studio: Run → Run 'app'
```

## Project Structure

```
app/src/main/java/com/dialect/voice/
├── MainActivity.kt                    Entry point
├── api/
│   ├── OpenAIClient.kt               LLM dialect conversion
│   └── ElevenLabsClient.kt           TTS synthesis
├── domain/
│   ├── Models.kt                     Data models
│   ├── Dialects.kt                   Dialect definitions + prompts
├── ui/
│   ├── ChatScreen.kt                 Main chat UI (Compose)
│   ├── ChatViewModel.kt              State management
│   └── theme/
│       └── Theme.kt                  Material Design 3 theme
```

## What's Missing / TODOs

- [ ] **Audio playback** — AudioPlayer component needs ExoPlayer integration
- [ ] **Audio recording** — Microphone input + Whisper transcription
- [ ] **Cloud storage** — Upload generated audio to S3/R2 instead of base64
- [ ] **Subscription/billing** — RevenueCat or Google Play Billing
- [ ] **Persistent storage** — Room DB for message history
- [ ] **Error handling** — More robust error messages
- [ ] **Styling** — Polish UI with better colors/animations

## Architecture

**State Management:** Jetpack ViewModel + MutableStateFlow
- `ChatViewModel` holds messages, dialect, loading state
- UI observes state via `collectAsState()`
- All API calls run in `viewModelScope.launch`

**API Clients:** Ktor HTTP client with serialization
- `OpenAIClient` → `/v1/chat/completions` for dialect conversion
- `ElevenLabsClient` → `/v1/text-to-speech/{voiceId}` for TTS

**UI:** Jetpack Compose
- Single-screen app
- LazyColumn for message feed
- TextField for text input
- Dropdowns for dialect selection

## Extending

### Add a new dialect
1. Open `Dialects.kt`
2. Add entry to `DIALECTS` map with system prompt + voice ID
3. It auto-appears in dialect selector

### Change TTS voice settings
In `Dialects.kt` or `ElevenLabsClient.kt`:
- `stability` (0.0–1.0) — Higher = more monotone
- `similarity_boost` (0.0–1.0) — Higher = more authentic
- `style` (0.0–1.0) — Higher = more dramatic

## Costs

- **OpenAI**: ~$0.0005 per message (gpt-4o-mini)
- **ElevenLabs**: ~$0.30 per 1000 characters (standard voices)
- **Total per message**: ~$0.0015–$0.003

## Troubleshooting

**"Unable to resolve host"** → Check internet connection
**"401 Unauthorized"** → API keys are wrong or expired
**"No response from OpenAI"** → Response parsing failed (check JSON)
**Audio not playing** → AudioPlayer component needs ExoPlayer setup

## Next Steps

1. Implement audio playback with ExoPlayer
2. Add microphone recording + Whisper transcription
3. Integrate Google Play Billing
4. Add Room DB for persistent chat history
5. Create Firestore sync for cloud backup
6. iOS version via Kotlin Multiplatform Mobile (KMM)

## License

MIT (or your preferred license)
