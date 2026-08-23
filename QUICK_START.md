# Quick Start — Dialect Voice App (Kotlin/Android)

## What I've Built

A **POC Android Compose app** that:
- ✅ Sends text to OpenAI with dialect system prompts
- ✅ Gets back regionally-accented responses
- ✅ Converts responses to speech via ElevenLabs
- ✅ Plays audio with regional UK accents (Geordie, Scouse, Glaswegian, Welsh, Scottish)
- ✅ Chat UI with message history
- ✅ Dialect selector dropdown

**What's NOT included yet** (intentionally left for next steps):
- ❌ Audio playback (wired UI, needs ExoPlayer integration)
- ❌ Microphone recording (needs Android audio API + Whisper integration)
- ❌ RevenueCat billing (mocked out, unlimited free queries for POC)
- ❌ Cloud storage for audio (currently base64 inline)
- ❌ Persistent message storage (Room DB)

---

## 5-Minute Setup

### 1. **Get API Keys**
- **OpenAI**: https://platform.openai.com/api-keys (gpt-4o-mini)
- **ElevenLabs**: https://elevenlabs.io → Profile → API Keys

### 2. **Clone & Open**
```bash
cd dialect-voice-app-kotlin
open -a "Android Studio" .
```

### 3. **Add Keys to MainActivity.kt**
```kotlin
// Line ~20 in MainActivity.kt
val openAiApiKey = "sk-..." 
val elevenLabsApiKey = "..." 
```

### 4. **Map Voice IDs**
```
app/src/main/java/com/dialect/voice/domain/Dialects.kt
```
Replace 5x `REPLACE_WITH_..._VOICE_ID` with IDs from ElevenLabs Voice Library

**How to find voice IDs:**
1. Go https://elevenlabs.io/voice-library
2. Search for "Geordie", "Scouse", "Scottish", "Welsh", etc.
3. Click voice → copy `voice_id` from URL or details panel
4. Paste into Dialects.kt

**Example:**
```kotlin
elevenLabsVoiceId = "JBFqnCBsd6RMkjVY45Bg"  // Replace placeholder with real ID
```

### 5. **Build & Run**
```bash
# In Android Studio:
# Build → Make Project
# Run → Run 'app'
# Or hit Shift+F10
```

Scan QR in Logcat or use Android Emulator.

---

## File Structure

```
app/src/main/java/com/dialect/voice/
├── MainActivity.kt                    ← Edit API keys here
├── api/
│   ├── OpenAIClient.kt               Calls OpenAI chat endpoint
│   └── ElevenLabsClient.kt           Calls ElevenLabs TTS
├── domain/
│   ├── Models.kt                     Message, Dialect data classes
│   ├── Dialects.kt                   ← Edit voice IDs here
├── ui/
│   ├── ChatScreen.kt                 Main UI (Compose)
│   ├── ChatViewModel.kt              State + business logic
│   └── theme/Theme.kt                Colors/typography
app/src/main/res/
├── AndroidManifest.xml               Permissions (internet, record_audio)
├── values/themes.xml                 Theme
└── xml/
    ├── data_extraction_rules.xml      API domains
    └── backup_rules.xml              Backup config
```

---

## How It Works (Flow)

```
User types "Hello" → Sends to MainActivity
                   ↓
         ChatViewModel.sendMessage()
                   ↓
     OpenAIClient.convertToDialect()
      (system: "Be Liverpudlian")
                   ↓
      "Alright la, how's it goin'?"
                   ↓
    ElevenLabsClient.synthesizeSpeech()
      (voiceId: "Scouse voice")
                   ↓
      audio bytes → base64 data URL
                   ↓
      ChatBubble.AudioPlayer()
           (▶ Play button)
```

---

## Next: Make It Actually Work

### 1. **Add Audio Playback** (5 min)
```kotlin
// In ChatScreen.kt → AudioPlayer()
// Replace the TODO with:
val context = LocalContext.current
val mediaPlayer = remember { MediaPlayer() }

Button(onClick = {
    mediaPlayer.apply {
        setDataSource(audioUrl)  // base64 data URL
        prepare()
        start()
    }
})
```

Or use **ExoPlayer** for better reliability:
```kotlin
val exoPlayer = remember { ExoPlayer.Builder(context).build() }
// Then load audioUrl as MediaItem
```

### 2. **Add Microphone Recording** (15 min)
```kotlin
// New file: AudioRecorder.kt
class AudioRecorder(val context: Context) {
    fun startRecording(): File { ... }
    fun stopRecording(): File { ... }
}

// In ChatScreen.kt → BottomBar
Button("🎤 Hold to record") {
    val audioFile = audioRecorder.startRecording()
    // onRelease: stopRecording() → transcribeAudio()
}
```

Then use Whisper:
```kotlin
suspend fun transcribeAudio(audioFile: File): String {
    val transcript = openAiClient.transcribeAudio(audioFile)
    sendMessage(transcript)
}
```

### 3. **Upload Audio to Cloud** (10 min)
```kotlin
// In ElevenLabsClient.synthesizeSpeech()
// Replace base64 with:
val uploadUrl = uploadToR2(audioBytes)  // Cloudflare R2 / S3
return uploadUrl  // Public URL instead of base64
```

### 4. **Add Persistent Storage** (15 min)
```kotlin
// New file: AppDatabase.kt
@Database(entities = [MessageEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}

// In ChatViewModel:
private val db = Room.databaseBuilder(...).build()
private val messageDao = db.messageDao()

// Save every message:
messageDao.insert(message.toEntity())
```

### 5. **Add RevenueCat Billing** (20 min)
```kotlin
// Copy logic from Expo version:
// - Free tier: 3 queries
// - Premium: unlimited
// - Paywall on 4th query if not subscribed
```

---

## Testing

**Test the OpenAI dialect prompt:**
```bash
curl -X POST https://api.openai.com/v1/chat/completions \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o-mini",
    "messages": [
      {"role": "system", "content": "You are a friendly Geordie..."},
      {"role": "user", "content": "Hello"}
    ]
  }'
```

**Test ElevenLabs voice:**
```bash
curl -X POST \
  https://api.elevenlabs.io/v1/text-to-speech/VOICE_ID \
  -H "xi-api-key: $ELEVENLABS_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"text":"Howay man, how are ya?"}' \
  --output audio.mp3
```

---

## Common Issues

| Issue | Fix |
|-------|-----|
| "Unable to resolve host" | Check internet + firewall |
| "401 Unauthorized" | API key wrong or expired |
| "No response from OpenAI" | Check `OPENAI_API_KEY` format |
| "Failed to synthesize" | Invalid `elevenLabsVoiceId` |
| Text input not sending | Check if `isLoading` is blocking |
| No audio button | AudioPlayer needs impl (see TODOs) |

---

## Costs (Monthly Estimate)

Assuming **30 queries/day, 25 days/month**:

- **OpenAI**: 30 × 25 × $0.0005 = **$0.38/month**
- **ElevenLabs**: 30 × 25 × 100 chars × $0.30/1M = **~$2.25/month**
- **Total**: ~**$2.63/month** 💰

---

## Next Steps

1. ✅ **Run the POC** — Get the app working end-to-end
2. ✅ **Test accents** — Tweak voice settings for better dialect
3. ⬜ **Add audio playback** — Implement ExoPlayer or MediaPlayer
4. ⬜ **Add recording** — Microphone + Whisper transcription
5. ⬜ **Cloud storage** — S3/R2 for audio
6. ⬜ **Persistent DB** — Room for chat history
7. ⬜ **Billing** — RevenueCat or Play Billing
8. ⬜ **Polish UI** — Better colors, animations, error handling
9. ⬜ **iOS** — Kotlin Multiplatform Mobile (later)

---

## Questions?

Check the full `README.md` for architecture details, or reply here!
