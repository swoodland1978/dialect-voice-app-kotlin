package com.dialect.voice.domain

// Mirrors users/{uid} in Firestore - populated by UserRepository's live listener, never
// written to directly by the client (see firestore.rules: all writes go through Cloud
// Functions with the Admin SDK).
//
// No subscription, no billing period - two separate running balances in seconds, seeded with
// a one-time free grant on first sign-in (FREE_*_SECONDS in config.ts) and topped up together
// (10:1, text:voice) by the £8.99 credit purchase, spent independently as each is used, and
// never resetting or expiring on their own. Kept apart so running out of voice minutes
// doesn't also cut off text chat - see chatCompletion.ts / synthesizeSpeech.ts.
data class UserAccountState(
    val creditSecondsRemaining: Int = 0, // voice (ElevenLabs TTS)
    val textSecondsRemaining: Int = 0 // text (OpenAI chat)
) {
    val hasCredit: Boolean
        get() = creditSecondsRemaining > 0

    val hasTextCredit: Boolean
        get() = textSecondsRemaining > 0

    val isLowCredit: Boolean
        get() = hasCredit && creditSecondsRemaining <= 2 * 60
}
