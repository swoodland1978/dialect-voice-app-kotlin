package com.dialect.voice.ui

import android.app.Activity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dialect.voice.api.ElevenLabsClient
import com.dialect.voice.api.OpenAIClient
import com.dialect.voice.billing.BillingManager
import com.dialect.voice.billing.PurchaseUiState
import com.dialect.voice.data.UserRepository
import com.dialect.voice.domain.AudioState
import com.dialect.voice.domain.DIALECTS
import com.dialect.voice.domain.Message
import com.dialect.voice.domain.MessageRole
import com.dialect.voice.domain.MessageStatus
import com.dialect.voice.domain.RecordingState
import com.dialect.voice.domain.UserAccountState
import com.dialect.voice.ui.billing.PaywallScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
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
    val isMuted by viewModel.isMuted.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val accountState by viewModel.accountState.collectAsState()
    val purchaseState by billingManager.purchaseState.collectAsState()
    val priceText by billingManager.priceText.collectAsState()
    val micPermissionState = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)
    val activity = LocalContext.current as Activity

    var inputText by remember { mutableStateOf("") }
    var dialectDropdownExpanded by remember { mutableStateOf(false) }
    // No auto-prompt on sign-in - straight into chat so nothing gets in the way before
    // someone's had a chance to try the app. The paywall only ever appears reactively, via
    // the "Buy credit" links under a no-credit reply or the easter egg.
    var showPaywall by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(purchaseState) {
        if (purchaseState is PurchaseUiState.Success) {
            showPaywall = false
        }
    }

    if (showPaywall) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            PaywallScreen(
                purchaseState = purchaseState,
                priceText = priceText,
                onPurchaseClick = { billingManager.launchBillingFlow(activity) }
            )
            IconButton(
                onClick = { showPaywall = false },
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close"
                )
            }
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top bar - dialect picker, styled like a model switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "WhyAI",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.setMuted(!isMuted) }) {
                Icon(
                    imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            IconButton(onClick = { viewModel.playGoodbye(onComplete = onSignOut) }) {
                Icon(
                    imageVector = Icons.Filled.Logout,
                    contentDescription = "Sign out",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Box {
                Surface(
                    onClick = { dialectDropdownExpanded = true },
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
                    expanded = dialectDropdownExpanded,
                    onDismissRequest = { dialectDropdownExpanded = false }
                ) {
                    DIALECTS.forEach { (key, dialect) ->
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
                                viewModel.setDialect(key)
                                dialectDropdownExpanded = false
                            }
                        )
                    }
                }
            }
            }
        }

        UsageBanner(accountState = accountState)

        // Messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(messages) { message ->
                if (message.status == MessageStatus.PENDING) {
                    TypingIndicator()
                } else {
                    ChatMessageRow(
                        message = message,
                        onPlayAudio = { viewModel.playAudio(message.id) },
                        onPaywallClick = { showPaywall = true }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(4.dp)) }
        }

        // Input bar - rounded pill like Gemini/ChatGPT
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

@Composable
fun ChatMessageRow(
    message: Message,
    onPlayAudio: () -> Unit,
    onPaywallClick: () -> Unit
) {
    val isUser = message.role == MessageRole.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            Surface(
                modifier = Modifier.widthIn(max = 280.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 15.sp
                )
            }
        } else {
            // Plain text, no bubble - matches Gemini/ChatGPT assistant styling
            Column(modifier = Modifier.widthIn(max = 320.dp)) {
                DIALECTS[message.dialect]?.let {
                    Text(
                        text = it.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Text(
                    text = message.text,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp
                )

                if (message.status == MessageStatus.ERROR) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        message.errorMessage ?: "Error",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    AudioControl(
                        state = message.audioState,
                        onClick = onPlayAudio,
                        onPaywallClick = onPaywallClick
                    )
                    if (message.showBuyCreditLink) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(onClick = onPaywallClick)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VolumeUp,
                                contentDescription = "Buy credit",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Buy credit to hear more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioControl(
    state: AudioState,
    onClick: () -> Unit,
    onPaywallClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            AudioState.SYNTHESIZING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Generating audio…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AudioState.PLAYING -> {
                IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = "Pause",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    "Playing…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AudioState.ERROR -> {
                IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Retry audio",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    "Audio failed - tap to retry",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            AudioState.NONE, AudioState.READY -> {
                IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = "Play audio",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Listen",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AudioState.NO_CREDIT -> {
                IconButton(onClick = onPaywallClick, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = "Buy credit to unlock voice replies",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    "Buy credit to unlock voice",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun UsageBanner(accountState: UserAccountState) {
    // No nagging top banner for someone with no credit - whether they've never bought
    // anything or unlocked and spent their balance, the only prompt is the clickable "Buy
    // credit to unlock voice" link under the upsell message itself (see AudioControl's
    // NO_CREDIT branch), which only shows up once they've actually tried to hear voice. This
    // banner is purely informational, for when there's an actual balance to report.
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

@Composable
fun TypingIndicator() {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        val transition = rememberInfiniteTransition(label = "typing")
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = index * 150, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            )
            if (index < 2) Spacer(modifier = Modifier.width(5.dp))
        }
    }
}
