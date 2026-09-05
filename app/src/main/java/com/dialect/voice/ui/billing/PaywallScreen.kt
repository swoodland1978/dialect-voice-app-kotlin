package com.dialect.voice.ui.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dialect.voice.billing.PurchaseUiState
import com.dialect.voice.ui.AnimatedMascot

// Mostly visual + audio - the offer itself is spoken (ChatViewModel.playUpsellAudio,
// triggered by ChatScreen the moment this screen appears) in the current dialect's voice,
// same as every other on-device preset line. Price + an expandable "what's included" line
// are still shown though - people want to actually see what they're buying before tapping
// buy, not just hear it once, so that disclosure lives here as well as in Play Billing's own
// system purchase sheet.
@Composable
fun PaywallScreen(
    purchaseState: PurchaseUiState,
    priceText: String?,
    isSpeaking: Boolean,
    playbackAmplitude: Float,
    onPurchaseClick: () -> Unit,
    onClose: () -> Unit
) {
    val isVerifying = purchaseState is PurchaseUiState.Verifying
    val hasError = purchaseState is PurchaseUiState.Error
    var showDetails by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedMascot(
                isSpeaking = isSpeaking,
                isRecording = false,
                isBusy = isVerifying,
                hasError = hasError,
                playbackAmplitude = playbackAmplitude,
                recordingAmplitude = 0f,
                onTap = {}
            )

            Spacer(modifier = Modifier.size(24.dp))

            Button(onClick = onPurchaseClick, enabled = !isVerifying) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Verifying…")
                } else {
                    Text("Buy credit")
                }
            }

            Spacer(modifier = Modifier.size(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    priceText ?: "…",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(onClick = { showDetails = !showDetails }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "What's included",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            if (showDetails) {
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    "30 minutes of spoken AI replies in your chosen regional accent, plus " +
                        "plenty of extra chat time once that runs out - all included in the " +
                        "one price. No subscription - credit never expires, and you can top " +
                        "up any time.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                )
            }

            if (hasError) {
                Spacer(modifier = Modifier.size(16.dp))
                Text(
                    "Something went wrong - try again",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.padding(8.dp).align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
