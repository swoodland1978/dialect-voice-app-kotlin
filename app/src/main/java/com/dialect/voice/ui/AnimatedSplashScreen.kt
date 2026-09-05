package com.dialect.voice.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

// The real "hello" moment - shows the mascot on white and actually plays the welcome greeting
// (the same preset line ChatViewModel would otherwise only speak once ChatScreen itself was
// already on screen), then reveals the full chat UI once that's done. viewModel is
// constructed by the caller (MainActivity) and handed to ChatScreen afterwards unchanged, so
// the greeting - triggered once, in ChatViewModel's init - is heard exactly once, here.
@Composable
fun WelcomeSplashScreen(viewModel: ChatViewModel, onFinished: () -> Unit) {
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val playbackAmplitude by viewModel.playbackAmplitude.collectAsState()
    var hasStartedSpeaking by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { entered = true }

    LaunchedEffect(isSpeaking) {
        if (isSpeaking) hasStartedSpeaking = true
        if (hasStartedSpeaking && !isSpeaking) {
            // Greeting finished playing - hold the frame a beat so the reveal doesn't feel
            // abrupt the instant the voice stops, then hand off to the real UI.
            delay(250)
            onFinished()
        }
    }

    // Safety net: if the audio fails outright (missing resource, playback error - see
    // ChatViewModel.playPresetResource's catch), isSpeaking may never flip true at all, which
    // would otherwise strand this screen forever with nothing to reveal the app.
    LaunchedEffect(Unit) {
        delay(4000)
        onFinished()
    }

    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "splashScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(500),
        label = "splashAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.scale(scale).alpha(alpha)) {
            AnimatedMascot(
                isSpeaking = isSpeaking,
                isRecording = false,
                isBusy = !hasStartedSpeaking,
                hasError = false,
                playbackAmplitude = playbackAmplitude,
                recordingAmplitude = 0f,
                onTap = {}
            )
        }
    }
}
