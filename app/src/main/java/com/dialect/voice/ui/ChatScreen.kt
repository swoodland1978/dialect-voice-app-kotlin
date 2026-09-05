package com.dialect.voice.ui

import android.app.Activity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dialect.voice.R
import com.dialect.voice.api.ElevenLabsClient
import com.dialect.voice.api.OpenAIClient
import com.dialect.voice.billing.BillingManager
import com.dialect.voice.billing.PurchaseUiState
import com.dialect.voice.data.UserRepository
import com.dialect.voice.domain.AudioState
import com.dialect.voice.domain.DIALECTS
import com.dialect.voice.domain.ENABLED_DIALECT_IDS
import com.dialect.voice.domain.RecordingState
import com.dialect.voice.domain.UserAccountState
import com.dialect.voice.ui.billing.PaywallScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    openAiClient: OpenAIClient,
    elevenLabsClient: ElevenLabsClient,
    userRepository: UserRepository,
    billingManager: BillingManager,
    audioCacheDir: File,
    onSignOut: () -> Unit,
    viewModel: ChatViewModel = LocalContext.current.applicationContext.let { appContext ->
        remember {
            ChatViewModel(openAiClient, elevenLabsClient, userRepository, audioCacheDir, appContext)
        }
    }
) {
    val selectedDialect by viewModel.selectedDialect.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val accountState by viewModel.accountState.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val playbackAmplitude by viewModel.playbackAmplitude.collectAsState()
    val recordingAmplitude by viewModel.recordingAmplitude.collectAsState()
    val purchaseState by billingManager.purchaseState.collectAsState()
    val priceText by billingManager.priceText.collectAsState()
    val micPermissionState = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)
    val activity = LocalContext.current as Activity

    var inputText by remember { mutableStateOf("") }

    // No auto-prompt on sign-in - straight into listening so nothing gets in the way before
    // someone's had a chance to try the app. The paywall only ever appears reactively - see
    // the LaunchedEffect(messages) below.
    var showPaywall by remember { mutableStateOf(false) }
    LaunchedEffect(showPaywall) {
        // Deliberately NOT calling viewModel.playUpsellAudio here - every path that flips
        // showPaywall to true (see LaunchedEffect(messages) below) already played the right
        // spoken line itself (the no-credit preset, the easter egg line, etc.) when it set
        // that message's flags. Calling it again here overlapped that with a second, generic
        // "buy credit" line - the "two voices at once" bug.
        if (showPaywall) {
            billingManager.ensureProductLoaded()
        }
    }

    LaunchedEffect(purchaseState) {
        if (purchaseState is PurchaseUiState.Success) {
            showPaywall = false
        }
    }

    // Replaces the old "Buy credit to hear more" text link - the moment a message comes back
    // flagged as out-of-credit, the paywall opens on its own instead of waiting for a tap on
    // text that no longer exists.
    LaunchedEffect(messages) {
        val last = messages.lastOrNull()
        if (last != null && (last.audioState == AudioState.NO_CREDIT || last.showBuyCreditLink)) {
            showPaywall = true
        }
    }

    // No Snackbar text for errors - a brief red pulse on the mascot is the entire error UI,
    // then it clears itself the same way the old Snackbar auto-dismissed.
    var errorPulse by remember { mutableStateOf(false) }
    LaunchedEffect(error) {
        if (error != null) {
            errorPulse = true
            delay(900)
            errorPulse = false
            viewModel.clearError()
        }
    }

    if (showPaywall) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            PaywallScreen(
                purchaseState = purchaseState,
                priceText = priceText,
                isSpeaking = isSpeaking,
                playbackAmplitude = playbackAmplitude,
                onPurchaseClick = { billingManager.launchBillingFlow(activity) },
                onClose = { showPaywall = false }
            )
        }
        return
    }

    val isBusy = isLoading || recordingState == RecordingState.TRANSCRIBING

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // No title, no version string - just a manual way into the paywall (it already
            // has the price + info icon, so this button doesn't need to repeat any of that)
            // and sign-out. Mute doesn't make sense here since there's no text transcript to
            // fall back on if you silence the audio - this app has nothing else to show you.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { showPaywall = true }) {
                    Text("Buy credit")
                }
                IconButton(onClick = { viewModel.playGoodbye(onComplete = onSignOut) }) {
                    Icon(
                        imageVector = Icons.Filled.Logout,
                        contentDescription = "Sign out",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            UsageBanner(accountState = accountState)

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedMascot(
                    isSpeaking = isSpeaking,
                    isRecording = recordingState == RecordingState.RECORDING,
                    isBusy = isBusy,
                    hasError = errorPulse,
                    playbackAmplitude = playbackAmplitude,
                    recordingAmplitude = recordingAmplitude,
                    onTap = {
                        when {
                            isBusy -> {}
                            isSpeaking -> viewModel.stopSpeaking()
                            recordingState == RecordingState.RECORDING -> viewModel.stopRecording()
                            else -> {
                                if (micPermissionState.status.isGranted) {
                                    viewModel.startRecording()
                                } else {
                                    micPermissionState.launchPermissionRequest()
                                }
                            }
                        }
                    }
                )
            }

            // Real waveform, driven by the same amplitude signals as the mascot's rings -
            // sits right under the cap image so the audio has a second, more literal visual.
            AudioWaveform(
                isSpeaking = isSpeaking,
                isRecording = recordingState == RecordingState.RECORDING,
                isBusy = isBusy,
                playbackAmplitude = playbackAmplitude,
                recordingAmplitude = recordingAmplitude,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(40.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DialectDropdown(
                    selectedDialect = selectedDialect,
                    onSelect = { viewModel.setDialect(it) }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // "Thinking…"/"…" style progress cue - the old per-turn dot strip here didn't
            // read as meaningful to anyone, this is just "the AI is working on it".
            ThinkingIndicator(
                isThinking = isBusy,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Restored: either speak (mic button, same start/stop as tapping the mascot) or
            // type - the mascot tap is a shortcut, not the only way in.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Message WhyAI…") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        maxLines = 5
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        when (recordingState) {
                            RecordingState.IDLE -> {
                                if (micPermissionState.status.isGranted) {
                                    viewModel.startRecording()
                                } else {
                                    micPermissionState.launchPermissionRequest()
                                }
                            }
                            RecordingState.RECORDING -> viewModel.stopRecording()
                            RecordingState.TRANSCRIBING -> {}
                        }
                    },
                    enabled = recordingState != RecordingState.TRANSCRIBING,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (recordingState == RecordingState.RECORDING)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    when (recordingState) {
                        RecordingState.TRANSCRIBING -> CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        RecordingState.RECORDING -> Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "Stop recording",
                            tint = MaterialTheme.colorScheme.onError
                        )
                        RecordingState.IDLE -> Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Record voice message",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                val canSend = inputText.isNotBlank() && !isLoading
                IconButton(
                    onClick = {
                        if (canSend) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// The entire interface, replacing every chat bubble/label the old screen had: a big circular
// mascot with soundwave rings behind it. Idle = a slow breathing pulse. Recording = pulses with
// mic input level. Speaking = pulses with actual playback amplitude via Visualizer (see
// ChatViewModel). Busy (thinking/transcribing) = a fast, small pulse distinct from both. A tap
// on the mascot is the only "button" in this screen - it starts/stops listening, or interrupts
// whatever's currently being spoken.
@Composable
fun AnimatedMascot(
    isSpeaking: Boolean,
    isRecording: Boolean,
    isBusy: Boolean,
    hasError: Boolean,
    playbackAmplitude: Float,
    recordingAmplitude: Float,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "mascotIdle")
    val idlePulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle"
    )
    val busyPulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "busy"
    )

    val targetAmplitude = when {
        isSpeaking -> playbackAmplitude
        isRecording -> recordingAmplitude
        isBusy -> busyPulse * 0.55f
        else -> idlePulse * 0.12f
    }
    val amplitude by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = tween(if (isSpeaking || isRecording) 60 else 220),
        label = "amplitude"
    )

    val ringColor = when {
        hasError -> MaterialTheme.colorScheme.error
        isRecording -> Color(0xFFE53935)
        isSpeaking -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .size(280.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseRadius = size.minDimension / 2f * 0.42f
            repeat(3) { ring ->
                val ringAmplitude = (amplitude * (1f - ring * 0.18f)).coerceIn(0f, 1f)
                val radius = baseRadius * (1.25f + ring * 0.28f + ringAmplitude * 0.5f)
                drawCircle(
                    color = ringColor.copy(alpha = (0.30f - ring * 0.08f) * (0.35f + amplitude)),
                    radius = radius,
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        Image(
            painter = painterResource(id = R.drawable.logo_why_ai),
            contentDescription = if (isRecording) "Listening - tap to stop" else "Tap to talk",
            modifier = Modifier
                .size(170.dp)
                .scale(1f + amplitude * 0.2f)
                .clip(CircleShape)
        )
    }
}

// Restored text dropdown for picking a dialect (the colored-swatch version didn't stick) -
// same Surface-trigger + DropdownMenu pattern the app used before the redesign.
@Composable
fun DialectDropdown(selectedDialect: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DIALECTS[selectedDialect]?.label ?: "Select",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Choose dialect",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ENABLED_DIALECT_IDS.forEach { key ->
                val dialect = DIALECTS.getValue(key)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(dialect.label, fontWeight = FontWeight.Medium)
                            Text(
                                dialect.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

// A real bar-style waveform under the mascot - keeps a short rolling history of the same
// amplitude signal driving AnimatedMascot's rings, so it reads as one continuous strip
// scrolling past rather than every bar jumping in lockstep. rememberUpdatedState is required
// here since the sampling loop below is a single long-lived LaunchedEffect(Unit) - without it,
// the loop would keep reading whatever amplitude values were current at first composition.
@Composable
fun AudioWaveform(
    isSpeaking: Boolean,
    isRecording: Boolean,
    isBusy: Boolean,
    playbackAmplitude: Float,
    recordingAmplitude: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 32
) {
    val infinite = rememberInfiniteTransition(label = "waveformIdle")
    val idlePulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle"
    )
    val busyPulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "busy"
    )

    val currentAmplitude = when {
        isSpeaking -> playbackAmplitude
        isRecording -> recordingAmplitude
        isBusy -> busyPulse * 0.55f
        else -> idlePulse * 0.12f
    }
    val latestAmplitude = rememberUpdatedState(currentAmplitude)

    val history = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0.05f) } } }
    LaunchedEffect(Unit) {
        while (true) {
            history.add(latestAmplitude.value.coerceIn(0f, 1f))
            if (history.size > barCount) history.removeAt(0)
            delay(55)
        }
    }

    val color = when {
        isRecording -> Color(0xFFE53935)
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        history.forEach { level ->
            val barHeight = 4.dp + (36.dp - 4.dp) * level
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = 0.4f + level * 0.6f))
            )
        }
    }
}

// Replaces the old per-turn dot strip, which didn't read as meaningful to anyone. Cycles
// "." -> ".." -> "..." -> "...." -> "....." and loops back to the start for as long as
// isThinking stays true, so a slow reply still visibly looks alive rather than jammed.
@Composable
fun ThinkingIndicator(isThinking: Boolean, modifier: Modifier = Modifier) {
    val steps = listOf(".", "..", "...", "....", ".....")
    var stepIndex by remember { mutableStateOf(0) }

    LaunchedEffect(isThinking) {
        if (isThinking) {
            stepIndex = 0
            while (true) {
                delay(350)
                stepIndex = (stepIndex + 1) % steps.size
            }
        }
    }

    Box(modifier = modifier.height(24.dp), contentAlignment = Alignment.Center) {
        if (isThinking) {
            Text(
                text = steps[stepIndex],
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// Restored from the pre-redesign screen - shown only when there's an actual balance to
// report, same as before (no nagging "buy credit" banner here; that's the paywall's job).
@Composable
fun UsageBanner(accountState: UserAccountState) {
    if (!accountState.hasCredit) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = "${accountState.creditSecondsRemaining / 60} min of voice credit left",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
