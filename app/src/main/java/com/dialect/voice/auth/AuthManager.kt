package com.dialect.voice.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.dialect.voice.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

// Google Sign-In via Credential Manager (the current recommended flow) + Firebase Auth.
// The whole app sits behind sign-in - usage/credit state is per Google account, and
// there's no anonymous fallback (that would let a cap-hit user just clear app data for a
// fresh anonymous uid).
class AuthManager(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val credentialManager = CredentialManager.create(context)

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    init {
        auth.addAuthStateListener { _currentUser.value = it.currentUser }
    }

    suspend fun signInWithGoogle(): Result<Unit> {
        return try {
            val option = GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id))
                .setNonce(UUID.randomUUID().toString())
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val response = credentialManager.getCredential(context, request)
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(response.credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            auth.signInWithCredential(firebaseCredential).await()
            Result.success(Unit)
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: GoogleIdTokenParsingException) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }
}
