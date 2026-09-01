import { onCall, HttpsError } from "firebase-functions/v2/https";
import { synthesizeSpeech as callElevenLabs } from "../lib/elevenLabsProxy";
import { ELEVENLABS_API_KEY } from "../lib/secrets";
import { checkCapacity, estimateSeconds, recordUsage } from "../lib/usage";
import { REGION } from "../lib/config";

// Latency instrumentation - see also chatCompletion.ts.
const INSTANCE_START = Date.now();

interface SynthesizeSpeechRequest {
  text: string;
  voiceId: string;
}

// The core metering function - text chat (chatCompletion.ts) is uncapped, only this is
// gated against the caller's credit balance (see usage.ts).
export const synthesizeSpeech = onCall<SynthesizeSpeechRequest>(
  // minInstances 1: same reasoning as chatCompletion - a spoken reply already pays two
  // round-trips, so a cold start here on top is the worst of the "feels slow" cases.
  { region: REGION, secrets: [ELEVENLABS_API_KEY], minInstances: 1 },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in required");
    }
    const uid = request.auth.uid;

    const { text, voiceId } = request.data ?? {};
    if (!text || !voiceId) {
      throw new HttpsError("invalid-argument", "text and voiceId are required");
    }

    const t0 = Date.now();
    const estimatedSeconds = estimateSeconds(text);
    const capacity = await checkCapacity(uid, estimatedSeconds, request.auth.token.email ?? null);
    const tCheck = Date.now();

    if (!capacity.ok) {
      throw new HttpsError("resource-exhausted", "no_credit", {
        remainingSeconds: capacity.remainingSeconds,
      });
    }

    let audioBuffer: Buffer;
    try {
      audioBuffer = await callElevenLabs(ELEVENLABS_API_KEY.value(), text, voiceId);
    } catch (e) {
      // Failed synthesis never burns credit.
      throw new HttpsError("internal", (e as Error).message);
    }
    const tEleven = Date.now();

    // Only record usage after a successful synthesis - fire and forget, don't block the
    // audio response on the metering decrement.
    recordUsage(uid, estimatedSeconds).catch((e) => console.error("recordUsage failed", e));

    const audioBase64 = audioBuffer.toString("base64");
    console.log("synthesizeSpeech timing", {
      checkMs: tCheck - t0,
      elevenLabsMs: tEleven - tCheck,
      encodeMs: Date.now() - tEleven,
      totalMs: Date.now() - t0,
      textChars: text.length,
      audioKB: Math.round(audioBuffer.length / 1024),
      instanceAgeMs: t0 - INSTANCE_START,
    });

    return {
      audioBase64,
      remainingSeconds: capacity.remainingSeconds,
    };
  }
);
