package com.dialect.voice.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dialect.voice.api.ElevenLabsClient
import com.dialect.voice.api.NoCreditException
import com.dialect.voice.api.NoTextCreditException
import com.dialect.voice.api.OpenAIClient
import com.dialect.voice.audio.PRESET_AUDIO
import com.dialect.voice.audio.RANDOM_EGGS
import com.dialect.voice.data.UserRepository
import com.dialect.voice.domain.Dialect
import com.dialect.voice.domain.DIALECTS
import com.dialect.voice.domain.ENABLED_DIALECT_IDS
import com.dialect.voice.domain.AudioState
import com.dialect.voice.domain.Message
import com.dialect.voice.domain.MessageRole
import com.dialect.voice.domain.MessageStatus
import com.dialect.voice.domain.RecordingState
import com.dialect.voice.domain.UserAccountState
import com.dialect.voice.domain.getDialectById
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.pow

class ChatViewModel(
    private val openAiClient: OpenAIClient,
    private val elevenLabsClient: ElevenLabsClient,
    private val userRepository: UserRepository,
    private val audioCacheDir: File,
    private val appContext: Context
) : ViewModel() {

    val accountState: StateFlow<UserAccountState> = userRepository.accountState

    private val prefs = appContext.getSharedPreferences("dialect_prefs", Context.MODE_PRIVATE)

    private val _selectedDialect = MutableStateFlow(
        prefs.getString(KEY_SELECTED_DIALECT, null)?.takeIf { it in ENABLED_DIALECT_IDS } ?: DEFAULT_DIALECT_ID
    )
    val selectedDialect: StateFlow<String> = _selectedDialect.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    // Drives the mascot's animation - the voice-only UI has no text/waveform widget of its
    // own, so these are read directly by AnimatedMascot. isSpeaking is a plain flag (not
    // derived from the message list) since preset lines like goodbye/upsell aren't tied to
    // any particular message. playbackAmplitude/recordingAmplitude are normalized 0f..1f.
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _playbackAmplitude = MutableStateFlow(0f)
    val playbackAmplitude: StateFlow<Float> = _playbackAmplitude.asStateFlow()

    private val _recordingAmplitude = MutableStateFlow(0f)
    val recordingAmplitude: StateFlow<Float> = _recordingAmplitude.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var visualizer: Visualizer? = null
    private var recordingAmplitudeJob: Job? = null

    init {
        playPresetGreeting(_selectedDialect.value, isInitial = true)
    }

    fun setDialect(dialectId: String) {
        if (dialectId == _selectedDialect.value) return
        _selectedDialect.value = dialectId
        prefs.edit().putString(KEY_SELECTED_DIALECT, dialectId).apply()
        playPresetGreeting(dialectId, isInitial = false)
    }

    // Free, on-device greeting - never touches ElevenLabs or the credit check.
    // isInitial picks the "welcome" line (app just opened) vs. the "switch" line
    // (user picked a different accent).
    private fun playPresetGreeting(dialectId: String, isInitial: Boolean) {
        val greeting = PRESET_AUDIO[dialectId] ?: return
        val prompt = if (isInitial) greeting.welcome else greeting.switch
        val messageId = "preset_${UUID.randomUUID()}"

        _messages.value = _messages.value + Message(
            id = messageId,
            role = MessageRole.ASSISTANT,
            text = prompt.text,
            dialect = dialectId,
            presetAudioRes = prompt.audioRes,
            status = MessageStatus.DONE
        )

        playAudio(messageId)
    }

    // Public wrapper so the voice-only UI can let a tap on the mascot interrupt whatever's
    // currently being spoken (goodbye/upsell/reply/preset - stopPlayback tears down whichever
    // MediaPlayer is live, not just message-tied playback).
    fun stopSpeaking() = stopPlayback()

    // Attaches a Visualizer to the given playback session so the mascot can pulse in real
    // time with the actual audio rather than a canned animation. Best-effort: some
    // devices/OEMs don't support Visualizer, in which case this silently no-ops and the
    // mascot just falls back to its idle animation while still playing audio normally.
    private fun attachVisualizer(audioSessionId: Int) {
        releaseVisualizer()
        try {
            val viz = Visualizer(audioSessionId)
            viz.captureSize = Visualizer.getCaptureSizeRange()[1]
            viz.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        val data = waveform ?: return
                        // Unsigned 8-bit PCM centered at 128. Peak deviation (the original
                        // approach here) barely moves for normal speech - a single loud
                        // sample anywhere in the capture window pins it near max almost the
                        // whole time it's talking. RMS (average loudness across the window)
                        // tracks moment-to-moment loudness far better. REFERENCE_RMS is
                        // calibrated against real measured values from this pipeline (logged
                        // during debugging: ordinary ElevenLabs speech sits around 0.30-0.48
                        // RMS, not the much-quieter range a generic guess assumed - that
                        // earlier guess divided by a reference several times too small, which
                        // pinned everything near 1.0 instead of fixing it). This only changes
                        // how loud audio is *drawn*, not how loud it *plays*.
                        var sumSquares = 0.0
                        for (b in data) {
                            val deviation = (b.toInt() and 0xFF) - 128
                            sumSquares += (deviation * deviation).toDouble()
                        }
                        val rms = kotlin.math.sqrt(sumSquares / data.size) / 128.0
                        val normalized = (rms / REFERENCE_RMS).coerceIn(0.0, 1.0)
                        val expanded = normalized.pow(0.7)
                        _playbackAmplitude.value = expanded.toFloat().coerceIn(0f, 1f)
                    }

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
                },
                Visualizer.getMaxCaptureRate() / 2,
                true,
                false
            )
            viz.enabled = true
            visualizer = viz
        } catch (e: Exception) {
            visualizer = null
        }
    }

    private fun releaseVisualizer() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) {
            // Best-effort teardown - nothing to do if it's already gone.
        }
        visualizer = null
        _playbackAmplitude.value = 0f
    }

    fun startRecording() {
        if (_recordingState.value != RecordingState.IDLE) return

        stopPlayback()
        val file = File(audioCacheDir, "rec_${UUID.randomUUID()}.m4a")

        try {
            @Suppress("DEPRECATION")
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.path)
                prepare()
                start()
            }
            mediaRecorder = recorder
            recordingFile = file
            _recordingState.value = RecordingState.RECORDING

            // Poll the recorder's input level so the mascot can visibly "listen" while
            // someone talks - MediaRecorder has no push-based callback for this, so a short
            // poll loop is the standard approach.
            recordingAmplitudeJob = viewModelScope.launch {
                while (isActive && _recordingState.value == RecordingState.RECORDING) {
                    val amplitude = try {
                        mediaRecorder?.maxAmplitude ?: 0
                    } catch (e: Exception) {
                        0
                    }
                    _recordingAmplitude.value = (amplitude / 32767f).coerceIn(0f, 1f)
                    delay(60)
                }
                _recordingAmplitude.value = 0f
            }
        } catch (e: Exception) {
            _error.value = "Couldn't start recording"
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    fun stopRecording() {
        if (_recordingState.value != RecordingState.RECORDING) return

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            // Recording was too short or failed - fall through and clean up below.
        }
        mediaRecorder?.release()
        mediaRecorder = null
        recordingAmplitudeJob?.cancel()
        recordingAmplitudeJob = null
        _recordingAmplitude.value = 0f

        val file = recordingFile
        recordingFile = null
        _recordingState.value = RecordingState.TRANSCRIBING

        viewModelScope.launch {
            try {
                if (file == null || !file.exists() || file.length() == 0L) {
                    throw Exception("No audio captured")
                }
                val transcript = openAiClient.transcribeAudio(file)
                if (transcript.isNotBlank()) {
                    sendMessage(transcript)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Couldn't transcribe audio"
            } finally {
                file?.delete()
                _recordingState.value = RecordingState.IDLE
            }
        }
    }

    fun getCurrentDialect(): Dialect? {
        return DIALECTS[_selectedDialect.value]
    }

    private fun updateMessage(id: String, transform: (Message) -> Message) {
        _messages.value = _messages.value.map { if (it.id == id) transform(it) else it }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return

        val userMsgId = "u_${UUID.randomUUID()}"
        val assistantMsgId = "a_${UUID.randomUUID()}"
        val dialectId = _selectedDialect.value

        _messages.value = _messages.value + Message(
            id = userMsgId,
            role = MessageRole.USER,
            text = userText,
            status = MessageStatus.DONE
        )

        // Free, on-device easter egg - "what sort of things do you say?" gets a pre-recorded
        // vulgar sample of the persona (never touches OpenAI/ElevenLabs) that doubles as an
        // upsell, so it works even with zero credit. Checked before the normal pipeline.
        if (isEasterEggTrigger(userText)) {
            val greeting = PRESET_AUDIO[dialectId]
            if (greeting != null) {
                val eggMsgId = "egg_${UUID.randomUUID()}"
                _messages.value = _messages.value + Message(
                    id = eggMsgId,
                    role = MessageRole.ASSISTANT,
                    text = greeting.easterEgg.text,
                    dialect = dialectId,
                    presetAudioRes = greeting.easterEgg.audioRes,
                    showBuyCreditLink = true,
                    status = MessageStatus.DONE
                )
                playAudio(eggMsgId)
                return
            }
        }

        // Free, on-device nonsense easter eggs - arbitrary trigger phrases (e.g. "apple
        // juggler") get a fixed silly line back in whichever accent's selected, no relation
        // to the real conversation. Same free/local pattern as the other presets.
        val normalizedText = normalizeForTrigger(userText)
        val randomEgg = RANDOM_EGGS.find { normalizedText in it.triggers }
        if (randomEgg != null) {
            val audioRes = randomEgg.audioResByDialect[dialectId]
            if (audioRes != null) {
                val randomEggMsgId = "randomegg_${UUID.randomUUID()}"
                _messages.value = _messages.value + Message(
                    id = randomEggMsgId,
                    role = MessageRole.ASSISTANT,
                    text = randomEgg.text,
                    dialect = dialectId,
                    presetAudioRes = audioRes,
                    status = MessageStatus.DONE
                )
                playAudio(randomEggMsgId)
                return
            }
        }

        // No text credit at all - never call OpenAI for this message. Gated on text credit
        // specifically (not voice) - the two are metered separately, so someone who's used up
        // their spoken minutes can still text-chat as long as text credit remains. Show the
        // upsell immediately instead, same free/local preset audio as everywhere else.
        // accountState.value is the live Firestore-backed balance, so this is checked before
        // a single API call fires, not after one comes back.
        if (!accountState.value.hasTextCredit) {
            val greeting = PRESET_AUDIO[dialectId]
            if (greeting != null) {
                val noCreditMsgId = "nocredit_${UUID.randomUUID()}"
                _messages.value = _messages.value + Message(
                    id = noCreditMsgId,
                    role = MessageRole.ASSISTANT,
                    text = greeting.noCredit.text,
                    dialect = dialectId,
                    presetAudioRes = greeting.noCredit.audioRes,
                    showBuyCreditLink = true,
                    status = MessageStatus.DONE
                )
                playAudio(noCreditMsgId)
                return
            }
        }

        _messages.value = _messages.value + Message(
            id = assistantMsgId,
            role = MessageRole.ASSISTANT,
            text = "",
            dialect = dialectId,
            status = MessageStatus.PENDING
        )

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val dialect = getCurrentDialect() ?: throw Exception("Dialect not found")

                // The dialect flavor comes entirely from the system prompt, which already
                // instructs the model to use local words naturally. A separate forced
                // word-list hint was tried and dropped: it fed isolated standard->dialect
                // swaps out of context (e.g. "steamin'" (drunk)) and the model would bolt
                // them into sentences where that word doesn't fit ("keep my steaming to a
                // minimum"). Letting the model pick words from within the persona it's
                // already committed to reads far more natural.
                //
                // lengthHint used to hard-cap every reply at ~40 words regardless of what was
                // asked, which gutted real questions (e.g. "how do I do trigonometry?" got a
                // one-liner). Now it only discourages padding for genuinely simple chat,
                // while explicitly telling the model to give a real, complete, detailed
                // answer - still in accent - when the question actually calls for one.
                val lengthHint = "Match your reply's length to what's actually being asked: keep " +
                    "simple chat and banter short and punchy (1-2 sentences), but when someone asks a real " +
                    "question that needs explaining - how something works, how to do something, instructions, " +
                    "a proper opinion - give a complete, clear, properly detailed answer, still fully in " +
                    "character and accent. Never pad for the sake of length, and never cut a real answer " +
                    "short just to keep it brief."
                // Shared across every dialect (unlike the dialect-specific personality/slang in
                // Dialects.kt) since it's a fixed bit, not something that needs rewriting per
                // accent - the model naturally renders it in whatever voice is already active.
                val sparetimeHint = "If someone asks what you do in your spare time / free time, " +
                    "say something like: \"I am writing a book about different farts people do. " +
                    "I'm currently working on a chapter about why my farts smell much better than " +
                    "other people's.\" - reword it naturally in your own voice/accent rather than " +
                    "reciting it verbatim."
                // The model's training data has a fixed cutoff and gets no live/web data here, so
                // left to itself it'll confidently state whatever was true as of that cutoff (e.g.
                // a stale head of state) as if it's current fact. Telling it today's actual date
                // plus to hedge on anything that might have changed since turns that into an
                // honest "I'm not sure that's still current" in-voice, rather than a confidently
                // wrong answer.
                val today = java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy"))
                val currentAffairsHint = "Today's date is $today. Your training data has a cutoff " +
                    "date well before this, so you won't know about anything that changed after " +
                    "it - who currently holds a given office or role, recent news, this year's " +
                    "events, and so on. If you're asked about something like that and you're not " +
                    "confident your information is still current, say so honestly in your own " +
                    "voice/accent instead of confidently stating something that might now be out " +
                    "of date."
                val fullSystemPrompt =
                    "${dialect.systemPrompt}\n\n$lengthHint\n\n$sparetimeHint\n\n$currentAffairsHint"

                val dialectText = openAiClient.convertToDialect(
                    text = userText,
                    systemPrompt = fullSystemPrompt
                )

                updateMessage(assistantMsgId) {
                    it.copy(text = dialectText, status = MessageStatus.DONE)
                }

                playAudio(assistantMsgId)
            } catch (e: NoTextCreditException) {
                // Rare race - the pre-flight hasTextCredit check passed but the balance ran
                // out server-side before this call landed (e.g. a near-simultaneous request).
                // Same upsell swap as the pre-flight path, just reached a different way.
                val noCredit = PRESET_AUDIO[dialectId]?.noCredit
                updateMessage(assistantMsgId) {
                    it.copy(
                        text = noCredit?.text ?: it.text,
                        presetAudioRes = noCredit?.audioRes,
                        showBuyCreditLink = true,
                        status = MessageStatus.DONE
                    )
                }
                if (noCredit != null) {
                    playAudio(assistantMsgId)
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                _error.value = errorMsg
                updateMessage(assistantMsgId) {
                    it.copy(
                        text = "Couldn't translate that.",
                        status = MessageStatus.ERROR,
                        errorMessage = errorMsg
                    )
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Only hits the paid TTS API the first time a given message is played; after that it
    // replays the cached local file.
    fun playAudio(messageId: String) {
        val message = _messages.value.find { it.id == messageId } ?: return

        if (message.audioState == AudioState.PLAYING) {
            stopPlayback()
            return
        }

        viewModelScope.launch {
            stopPlayback()

            if (message.presetAudioRes != null) {
                playPresetResource(messageId, message.presetAudioRes)
                return@launch
            }

            val cached = message.audioFilePath?.let { File(it) }
            val file = if (cached != null && cached.exists()) {
                cached
            } else {
                val dialect = message.dialect?.let { getDialectById(it) }
                if (dialect == null) {
                    updateMessage(messageId) { it.copy(audioState = AudioState.ERROR) }
                    return@launch
                }
                updateMessage(messageId) { it.copy(audioState = AudioState.SYNTHESIZING) }
                try {
                    val bytes = elevenLabsClient.synthesizeSpeech(
                        text = message.text,
                        voiceId = dialect.elevenLabsVoiceId
                    )
                    val f = File(audioCacheDir, "$messageId.mp3")
                    f.writeBytes(bytes)
                    updateMessage(messageId) {
                        it.copy(audioFilePath = f.path, audioState = AudioState.READY)
                    }
                    f
                } catch (e: NoCreditException) {
                    // Not enough credit to cover this reply - whether never-unlocked or
                    // unlocked-but-spent, don't leave the real (free) text answer sitting
                    // there when they can't hear it; swap the bubble to the same upsell line
                    // that gets spoken, so what's written matches what's heard.
                    val upsellText = PRESET_AUDIO[dialect.id]?.noCredit?.text
                    updateMessage(messageId) {
                        it.copy(
                            text = upsellText ?: it.text,
                            audioState = AudioState.NO_CREDIT
                        )
                    }
                    playUpsellAudio(dialect.id)
                    return@launch
                } catch (e: Exception) {
                    _error.value = e.message ?: "Couldn't generate audio"
                    updateMessage(messageId) { it.copy(audioState = AudioState.ERROR) }
                    return@launch
                }
            }

            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(file.path)
                    setOnCompletionListener {
                        updateMessage(messageId) { m -> m.copy(audioState = AudioState.READY) }
                        _isSpeaking.value = false
                        releaseVisualizer()
                    }
                    prepare()
                    start()
                }
                attachVisualizer(mediaPlayer!!.audioSessionId)
                _isSpeaking.value = true
                updateMessage(messageId) { it.copy(audioState = AudioState.PLAYING) }
            } catch (e: Exception) {
                _error.value = "Couldn't play audio"
                updateMessage(messageId) { it.copy(audioState = AudioState.ERROR) }
            }
        }
    }

    // Bundled res/raw audio - local file, no network, no credit check.
    private fun playPresetResource(messageId: String, presetAudioRes: Int) {
        try {
            val player = MediaPlayer.create(appContext, presetAudioRes)
                ?: throw Exception("Missing preset audio resource")
            player.setOnCompletionListener {
                updateMessage(messageId) { m -> m.copy(audioState = AudioState.READY) }
                _isSpeaking.value = false
                releaseVisualizer()
            }
            mediaPlayer = player
            attachVisualizer(player.audioSessionId)
            _isSpeaking.value = true
            player.start()
            updateMessage(messageId) { it.copy(audioState = AudioState.PLAYING) }
        } catch (e: Exception) {
            _error.value = "Couldn't play audio"
            updateMessage(messageId) { it.copy(audioState = AudioState.ERROR) }
        }
    }

    // Free on-device goodbye line, spoken in the current dialect's voice, played on tapping
    // sign-out before the real sign-out actually happens. onComplete fires once playback
    // finishes (or immediately if there's nothing to play/it fails), so the caller can chain
    // the real sign-out after the line's been heard instead of cutting it off mid-sentence.
    fun playGoodbye(onComplete: () -> Unit) {
        val goodbyeRes = PRESET_AUDIO[_selectedDialect.value]?.goodbye?.audioRes
        if (goodbyeRes == null) {
            onComplete()
            return
        }
        stopPlayback()
        try {
            val player = MediaPlayer.create(appContext, goodbyeRes)
            if (player == null) {
                onComplete()
                return
            }
            mediaPlayer = player
            attachVisualizer(player.audioSessionId)
            _isSpeaking.value = true
            player.setOnCompletionListener {
                mediaPlayer = null
                _isSpeaking.value = false
                releaseVisualizer()
                onComplete()
            }
            player.start()
        } catch (e: Exception) {
            onComplete()
        }
    }

    // Free on-device upsell line, spoken in the current dialect's voice - the voice-only UI
    // has no "buy credit" text link, so this is called directly whenever the paywall appears
    // (see ChatScreen) as well as alongside the NO_CREDIT/easter-egg messages above.
    fun playUpsellAudio(dialectId: String) {
        val upsellRes = PRESET_AUDIO[dialectId]?.noCredit?.audioRes ?: return
        try {
            val player = MediaPlayer.create(appContext, upsellRes) ?: return
            mediaPlayer = player
            attachVisualizer(player.audioSessionId)
            _isSpeaking.value = true
            player.setOnCompletionListener {
                _isSpeaking.value = false
                releaseVisualizer()
            }
            player.start()
        } catch (e: Exception) {
            // Best-effort voiceover - the visual paywall still works without it.
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        releaseVisualizer()
        _isSpeaking.value = false
        _messages.value = _messages.value.map {
            if (it.audioState == AudioState.PLAYING) it.copy(audioState = AudioState.READY) else it
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearMessages() {
        stopPlayback()
        _messages.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
        recordingAmplitudeJob?.cancel()
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            // Ignore - we're tearing down anyway.
        }
        mediaRecorder?.release()
        recordingFile?.delete()
        userRepository.stopObserving()
    }

    // Lowercase, strip punctuation, collapse whitespace - so "WHat sort of things do you
    // say?" and "Apple Juggler!!" still match their trigger phrases below.
    private fun normalizeForTrigger(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")

    // "What sort of things do you say?" (and a couple of natural variants) triggers the free
    // vulgar sample line instead of a real AI reply.
    private fun isEasterEggTrigger(text: String): Boolean =
        normalizeForTrigger(text) in EASTER_EGG_TRIGGERS

    companion object {
        private const val KEY_SELECTED_DIALECT = "selected_dialect"
        private const val DEFAULT_DIALECT_ID = "geordie"
        // See attachVisualizer - the RMS value (0f..1f of full scale) a genuinely loud moment
        // in normal TTS speech is expected to reach. Measured empirically (temporary Log.d
        // instrumentation against real ElevenLabs playback on-device): ordinary speech sits
        // around 0.30-0.48, not the much lower value an untested guess assumed - that guess
        // pinned everything near 1.0 instead of fixing the saturation it was meant to fix.
        // Tune up if the mascot/waveform still looks pinned near max during ordinary talking,
        // or down if it now barely moves.
        private const val REFERENCE_RMS = 0.55
        private val EASTER_EGG_TRIGGERS = setOf(
            "what sort of things do you say",
            "what sort of things can you say",
            "what kind of things do you say",
            "what kind of things can you say"
        )
    }
}
