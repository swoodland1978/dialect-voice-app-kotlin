const CHAT_URL = "https://api.openai.com/v1/chat/completions";
const TRANSCRIBE_URL = "https://api.openai.com/v1/audio/transcriptions";

// Model and max_tokens are fixed server-side, not client-controlled - a modified client
// can't request a longer/pricier completion than the app's own cost budget allows. 600
// tokens is enough room for a genuinely detailed answer (e.g. explaining trigonometry) when
// the system prompt's length hint calls for one, while still bounding worst-case cost - this
// tier is cheap enough per token that this is a non-issue either way.
// MODEL is the one actually in use - change this to try something new. FALLBACK_MODEL is a
// separate, deliberately-not-touched safety net: chatCompletion() below tries MODEL first and
// automatically retries once against FALLBACK_MODEL if that call fails outright, so a bad
// MODEL value (wrong ID, deprecated, account doesn't have access, whatever) degrades to a
// working reply instead of breaking every single request in production - which is exactly
// what happened the one time this safety net didn't exist yet. Only repoint FALLBACK_MODEL
// once a new model has been running reliably in production for a while - it exists
// specifically so MODEL can be experimented with safely.
//
// gpt-5.6-luna verified directly against the real API before this deploy (not from a docs
// page - that's what broke it last time): it needs `max_completion_tokens` instead of the
// older `max_tokens`, and only supports the default temperature (no custom value), both
// confirmed by a live test call. gpt-4o-mini (the fallback) was independently confirmed to
// accept the same request shape, so one code path below serves both without branching.
// Typed as `string`, not inferred as a literal - MODEL and FALLBACK_MODEL are meant to be
// independently changeable, and TS's literal-type narrowing would otherwise flag the
// MODEL === FALLBACK_MODEL check below as "always false" (true today, but the whole point of
// that check is to keep working correctly once they're no longer different).
const MODEL: string = "gpt-5.6-luna";
const FALLBACK_MODEL: string = "gpt-4o-mini";
const MAX_COMPLETION_TOKENS = 600;

interface ChatMessage {
  role: string;
  content: string;
}

async function parseErrorMessage(res: Response): Promise<string> {
  const bodyText = await res.text();
  try {
    const parsed = JSON.parse(bodyText);
    return parsed?.error?.message ?? bodyText;
  } catch {
    return bodyText;
  }
}

async function callModel(
  apiKey: string,
  model: string,
  messages: ChatMessage[]
): Promise<string> {
  const res = await fetch(CHAT_URL, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model,
      messages,
      max_completion_tokens: MAX_COMPLETION_TOKENS,
    }),
  });

  if (!res.ok) {
    throw new Error(`OpenAI error (model=${model}): ${await parseErrorMessage(res)}`);
  }

  const data = (await res.json()) as {
    choices?: Array<{ message?: { content?: string } }>;
  };
  const text = data.choices?.[0]?.message?.content;
  if (!text) {
    throw new Error(`No response from OpenAI (model=${model})`);
  }
  return text;
}

export async function chatCompletion(
  apiKey: string,
  userText: string,
  systemPrompt: string
): Promise<string> {
  const messages: ChatMessage[] = [
    { role: "system", content: systemPrompt },
    { role: "user", content: userText },
  ];

  try {
    return await callModel(apiKey, MODEL, messages);
  } catch (primaryError) {
    if (MODEL === FALLBACK_MODEL) {
      // No distinct fallback configured - nothing left to try.
      throw primaryError;
    }
    // Logged loudly on purpose - this firing at all means MODEL is currently broken and
    // every request is paying the cost of two calls instead of one. It's meant to be
    // noticed and fixed, not to quietly become the new normal.
    console.error(
      `chatCompletion: primary model "${MODEL}" failed, falling back to "${FALLBACK_MODEL}"`,
      primaryError
    );
    try {
      return await callModel(apiKey, FALLBACK_MODEL, messages);
    } catch (fallbackError) {
      console.error(`chatCompletion: fallback model "${FALLBACK_MODEL}" also failed`, fallbackError);
      throw fallbackError;
    }
  }
}

export async function transcribeAudio(
  apiKey: string,
  audioBuffer: Buffer,
  filename: string
): Promise<string> {
  const form = new FormData();
  form.append("model", "whisper-1");
  form.append("file", new Blob([audioBuffer]), filename);

  const res = await fetch(TRANSCRIBE_URL, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
    },
    body: form,
  });

  if (!res.ok) {
    throw new Error(`Transcription failed: ${await parseErrorMessage(res)}`);
  }

  const data = (await res.json()) as { text?: string };
  if (typeof data.text !== "string") {
    throw new Error("No transcript in Whisper response");
  }
  return data.text;
}
