import { FieldValue } from "firebase-admin/firestore";
import { db, USERS_COLLECTION } from "./firebaseAdmin";

// ElevenLabs' own rule of thumb: ~1000 characters ~= 1 minute of audio. Reused for text too
// (see TEXT_CREDIT_SECONDS in config.ts) - not literally spoken, just a consistent length
// proxy so both meters use the same unit without decoding real audio duration server-side.
const CHARS_PER_MINUTE = 1000;

export function estimateSeconds(text: string): number {
  return Math.ceil((text.length / CHARS_PER_MINUTE) * 60);
}

export interface UsageDoc {
  email?: string | null;
  // Two separate running balances in seconds, both granted together by the same purchase
  // (see verifyPurchase.ts / config.ts) and never resetting or expiring on their own - no
  // subscription, no billing period, no free allowance. Kept apart specifically so a muted
  // (voice-exhausted) user can still text-chat instead of one balance covering both and
  // making a single purchase's chat access effectively unlimited once voice stops being used.
  creditSecondsRemaining: number; // voice (ElevenLabs TTS)
  textSecondsRemaining: number; // text (OpenAI chat)
}

export type CapacityCheck =
  | { ok: true; remainingSeconds: number }
  | { ok: false; reason: "no_credit"; remainingSeconds: number };

// DEV ALLOWLIST - these accounts get unmetered access (both meters) regardless of balance,
// so the app owner can test without paying. Everyone else still goes through the normal
// checks below. Remove this once real billing is fully tested end-to-end.
const DEV_BYPASS_EMAILS = new Set<string>([]);

type MeterField = "creditSecondsRemaining" | "textSecondsRemaining";

// Self-heals accounts where the onCreate trigger didn't run for whatever reason. .create()
// is idempotent against a concurrent duplicate (ALREADY_EXISTS is swallowed, the doc just
// gets re-read below).
async function ensureUserDoc(uid: string, email: string | null): Promise<UsageDoc | undefined> {
  const docRef = db.collection(USERS_COLLECTION).doc(uid);
  let snap = await docRef.get();
  let data = snap.data() as UsageDoc | undefined;

  if (!data) {
    try {
      await docRef.create({
        email,
        creditSecondsRemaining: 0,
        textSecondsRemaining: 0,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    } catch (e) {
      // ALREADY_EXISTS - a concurrent call created it first, fine.
    }
    snap = await docRef.get();
    data = snap.data() as UsageDoc | undefined;
  }

  return data;
}

// Read-only check. Not run inside a transaction with the actual API call (external network
// I/O must never live inside a Firestore transaction body - it can retry and double-fire).
// A rare race between two near-simultaneous requests both passing this check is an accepted
// v1 limitation; the atomic increment in recordUsage/recordTextUsage still keeps the stored
// total honest.
async function checkMeter(
  uid: string,
  estimatedSeconds: number,
  email: string | null,
  field: MeterField
): Promise<CapacityCheck> {
  const data = await ensureUserDoc(uid, email);

  if (data?.email && DEV_BYPASS_EMAILS.has(data.email)) {
    return { ok: true, remainingSeconds: Number.MAX_SAFE_INTEGER };
  }

  const remaining = data?.[field] ?? 0;
  if (estimatedSeconds > remaining) {
    return { ok: false, reason: "no_credit", remainingSeconds: remaining };
  }

  return { ok: true, remainingSeconds: remaining - estimatedSeconds };
}

export function checkCapacity(uid: string, estimatedSeconds: number, email: string | null): Promise<CapacityCheck> {
  return checkMeter(uid, estimatedSeconds, email, "creditSecondsRemaining");
}

export function checkTextCapacity(uid: string, estimatedSeconds: number, email: string | null): Promise<CapacityCheck> {
  return checkMeter(uid, estimatedSeconds, email, "textSecondsRemaining");
}

// Only call these after the underlying API call has actually succeeded - never charge for a
// failed request. FieldValue.increment is atomic on its own, no transaction needed.
export async function recordUsage(uid: string, seconds: number): Promise<void> {
  await db.collection(USERS_COLLECTION).doc(uid).update({
    creditSecondsRemaining: FieldValue.increment(-seconds),
    updatedAt: FieldValue.serverTimestamp(),
  });
}

export async function recordTextUsage(uid: string, seconds: number): Promise<void> {
  await db.collection(USERS_COLLECTION).doc(uid).update({
    textSecondsRemaining: FieldValue.increment(-seconds),
    updatedAt: FieldValue.serverTimestamp(),
  });
}

// Called by verifyPurchase after a purchase is confirmed against Google's servers - grants
// both meters together from the one purchase. Credit only ever moves up here or down in
// recordUsage/recordTextUsage - nothing else touches it.
export async function grantCredit(uid: string, voiceSeconds: number, textSeconds: number): Promise<void> {
  await db.collection(USERS_COLLECTION).doc(uid).update({
    creditSecondsRemaining: FieldValue.increment(voiceSeconds),
    textSecondsRemaining: FieldValue.increment(textSeconds),
    updatedAt: FieldValue.serverTimestamp(),
  });
}
