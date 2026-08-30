package com.dialect.voice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dialect.voice.api.ElevenLabsClient
import com.dialect.voice.api.OpenAIClient
import com.dialect.voice.auth.AuthManager
import com.dialect.voice.billing.BillingManager
import com.dialect.voice.data.UserRepository
import com.dialect.voice.ui.ChatScreen
import com.dialect.voice.ui.auth.SignInScreen
import com.dialect.voice.ui.theme.DialectVoiceTheme
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // No more embedded API keys - OpenAI/ElevenLabs calls go through Cloud Functions,
        // authenticated via Firebase Auth.
        val functions = FirebaseFunctions.getInstance()
        val openAiClient = OpenAIClient(functions)
        val elevenLabsClient = ElevenLabsClient(functions)
        val authManager = AuthManager(this)
        val userRepository = UserRepository()
        billingManager = BillingManager(this, functions)
        billingManager.startConnection()

        setContent {
            DialectVoiceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val currentUser by authManager.currentUser.collectAsState()
                    var isSigningIn by remember { mutableStateOf(false) }
                    var signInError by remember { mutableStateOf<String?>(null) }
                    val scope = rememberCoroutineScope()

                    LaunchedEffect(currentUser) {
                        val uid = currentUser?.uid
                        if (uid != null) {
                            userRepository.observe(uid)
                            // Billing verification needs an auth token - let BillingManager
                            // reconcile any unconsumed purchase now that we have one.
                            billingManager.onAuthReady()
                        } else {
                            userRepository.stopObserving()
                        }
                    }

                    if (currentUser == null) {
                        SignInScreen(
                            isLoading = isSigningIn,
                            errorMessage = signInError,
                            onSignInClick = {
                                isSigningIn = true
                                signInError = null
                                scope.launch {
                                    val result = authManager.signInWithGoogle()
                                    isSigningIn = false
                                    result.onFailure {
                                        signInError = it.message ?: "Sign-in failed"
                                    }
                                }
                            }
                        )
                    } else {
                        ChatScreen(
                            openAiClient = openAiClient,
                            elevenLabsClient = elevenLabsClient,
                            userRepository = userRepository,
                            billingManager = billingManager,
                            audioCacheDir = cacheDir,
                            onSignOut = { authManager.signOut() }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.endConnection()
    }
}
