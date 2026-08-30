export const REGION = "us-central1";

// Must match the one-time product created in Play Console -> Monetize -> Products ->
// One-time products. Consumable - can be bought repeatedly, credit just keeps stacking.
export const CREDIT_PRODUCT_ID = "dialect_voice_credit_30min"; // £8.99
export const CREDIT_SECONDS = 30 * 60; // 30 min of TTS (voice) credit granted per purchase

// Text chat (chatCompletion.ts) is metered separately from voice, on its own much larger
// allowance - OpenAI's per-message cost is roughly 1/500th of ElevenLabs' TTS cost, so a 10x
// bigger allowance is still comfortably safe while feeling unlimited in practice. This also
// closes a loophole where muting voice forever would make a single purchase's chat access
// last indefinitely, since voice-only metering never touched a muted user's balance.
export const TEXT_CREDIT_SECONDS = CREDIT_SECONDS * 10; // 5 hours of text, granted alongside
