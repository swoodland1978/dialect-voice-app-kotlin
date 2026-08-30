import { onCall, HttpsError } from "firebase-functions/v2/https";
import { synthesizeSpeech as callElevenLabs } from "../lib/elevenLabsProxy";
import { ELEVENLABS_API_KEY } from "../lib/secrets";
import { checkCapacity, estimateSeconds, recordUsage } from "../lib/usage";
import { REGION } from "../lib/config";

interface SynthesizeSpeechRequest {
  text: string;
  voiceId: string;
}

// The core metering function - text chat (chatCompletion.ts) is uncapped, only this is
// gated against the caller's credit balance (see usage.ts).
export const synthesizeSpeech = onCall<SynthesizeSpeechRequest>(
  { region: REGION, secrets: [ELEVENLABS_API_KEY] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in required");
    }
    const uid = request.auth.uid;

    const { text, voiceId } = request.data ?? {};
    if (!text || !voiceId) {
      throw new HttpsError("invalid-argument", "text and voiceId are required");
    }

    const estimatedSeconds = estimateSeconds(text);
    const capacity = await checkCapacity(uid, estimatedSeconds, request.auth.token.email ?? null);

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

    // Only record usage after a successful synthesis.
    await recordUsage(uid, estimatedSeconds);

    return {
      audioBase64: audioBuffer.toString("base64"),
      remainingSeconds: capacity.remainingSeconds,
    };
  }
);
