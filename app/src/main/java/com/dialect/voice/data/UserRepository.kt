package com.dialect.voice.data

import com.dialect.voice.domain.UserAccountState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Live read-only view of users/{uid}. There is no write path here on purpose - every field
// (creditSecondsRemaining, textSecondsRemaining, ...) is only ever mutated by Cloud
// Functions via the Admin SDK (see firestore.rules), so the client can only ever observe.
class UserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val _accountState = MutableStateFlow(UserAccountState())
    val accountState: StateFlow<UserAccountState> = _accountState.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null

    fun observe(uid: String) {
        stopObserving()
        listenerRegistration = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null || !snapshot.exists()) {
                    _accountState.value = UserAccountState()
                    return@addSnapshotListener
                }
                _accountState.value = UserAccountState(
                    creditSecondsRemaining = (snapshot.getLong("creditSecondsRemaining") ?: 0L).toInt(),
                    textSecondsRemaining = (snapshot.getLong("textSecondsRemaining") ?: 0L).toInt()
                )
            }
    }

    fun stopObserving() {
        listenerRegistration?.remove()
        listenerRegistration = null
        _accountState.value = UserAccountState()
    }
}
