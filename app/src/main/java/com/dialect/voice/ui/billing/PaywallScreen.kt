package com.dialect.voice.ui.billing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dialect.voice.billing.PurchaseUiState

// One product, one price: 30 minutes of spoken replies, plus a generously large (10x, ~5
// hours) text-only chat allowance bundled in at no extra cost - see UsageState.kt /
// config.ts's TEXT_CREDIT_SECONDS. No subscription, no separate unlock step - buy again any
// time you run out, credit just stacks and never expires. Led with the price/value up front
// (headline price + an expandable info affordance) rather than a wall of text, so it reads as
// an inviting offer rather than a pay-per-minute utility. Shown both as an automatic prompt
// right after sign-in and reactively whenever someone with no credit taps to hear a reply.
// priceText is Play Billing's own locale-formatted price (e.g. "£10.99") - never hardcoded
// here, so it can't go stale when the price changes in Play Console.
@Composable
fun PaywallScreen(
    purchaseState: PurchaseUiState,
    priceText: String?,
    onPurchaseClick: () -> Unit
) {
    var showDetails by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Unlock WhyAI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "A full AI that answers back in your own accent - properly in character, not just a voice filter.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    priceText ?: "…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showDetails = !showDetails }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "What's included",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (showDetails) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Around 5 hours of text chat, plus 30 minutes of spoken AI replies in " +
                        "your chosen regional accent - both included in the one price. " +
                        "No subscription - credit never expires, and you can top up any time.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onPurchaseClick, enabled = purchaseState !is PurchaseUiState.Verifying) {
                if (purchaseState is PurchaseUiState.Verifying) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verifying…")
                } else {
                    Text("Buy credit")
                }
            }
            if (purchaseState is PurchaseUiState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    purchaseState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
