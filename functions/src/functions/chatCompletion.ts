import { onCall, HttpsError } from "firebase-functions/v2/https";
import { chatCompletion as callOpenAiChat } from "../lib/openaiProxy";
import { OPENAI_API_KEY } from "../lib/secrets";
import { checkTextCapacity, estimateSeconds, recordTextUsage } from "../lib/usage";
import { REGION } from "../lib/config";

interface ChatCompletionRequest {
  userText: string;
  systemPrompt: string;
}

// Metered against its own much larger text allowance (see TEXT_CREDIT_SECONDS in config.ts) -
// separate from the voice/TTS balance in synthesizeSpeech.ts, so someone who's used up their
// spoken minutes can still text-chat, and a single purchase can't be stretched into
// unlimited-forever chat just by never actually playing audio.
export const chatCompletion = onCall<ChatCompletionRequest>(
  { region: REGION, secrets: [OPENAI_API_KEY] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in required");
    }
    const uid = request.auth.uid;

    const { userText, systemPrompt } = request.data ?? {};
    if (!userText || !systemPrompt) {
      throw new HttpsError("invalid-argument", "userText and systemPrompt are required");
    }

    // Estimated off the incoming message, same char-based proxy as voice - good enough to
    // gate on before the real reply (whose actual length isn't known yet) exists.
    const estimatedSeconds = estimateSeconds(userText);
    const capacity = await checkTextCapacity(uid, estimatedSeconds, request.auth.token.email ?? null);

    if (!capacity.ok) {
      throw new HttpsError("resource-exhausted", "no_text_credit", {
        remainingSeconds: capacity.remainingSeconds,
      });
    }

    let text: string;
    try {
      text = await callOpenAiChat(OPENAI_API_KEY.value(), userText, systemPrompt);
    } catch (e) {
      // Failed call never burns credit.
      throw new HttpsError("internal", (e as Error).message);
    }

    // Charge for the reply actually generated, not the estimate the gate used - more
    // accurate, and the gate's estimate was only ever a pre-flight sanity check.
    await recordTextUsage(uid, estimateSeconds(text));

    return { text };
  }
);
