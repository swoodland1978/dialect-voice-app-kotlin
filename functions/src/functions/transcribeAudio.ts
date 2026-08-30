import { onCall, HttpsError } from "firebase-functions/v2/https";
import { transcribeAudio as callWhisper } from "../lib/openaiProxy";
import { OPENAI_API_KEY } from "../lib/secrets";
import { REGION } from "../lib/config";

interface TranscribeAudioRequest {
  audioBase64: string;
  filename?: string;
}

// Uncapped, same as chatCompletion - this is speech-to-text for the user's own input, not
// the ElevenLabs TTS output the 60-min cap applies to.
export const transcribeAudio = onCall<TranscribeAudioRequest>(
  { region: REGION, secrets: [OPENAI_API_KEY] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in required");
    }

    const { audioBase64, filename } = request.data ?? {};
    if (!audioBase64) {
      throw new HttpsError("invalid-argument", "audioBase64 is required");
    }

    try {
      const buffer = Buffer.from(audioBase64, "base64");
      const text = await callWhisper(OPENAI_API_KEY.value(), buffer, filename ?? "recording.m4a");
      return { text };
    } catch (e) {
      throw new HttpsError("internal", (e as Error).message);
    }
  }
);
