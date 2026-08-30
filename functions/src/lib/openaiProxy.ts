const CHAT_URL = "https://api.openai.com/v1/chat/completions";
const TRANSCRIBE_URL = "https://api.openai.com/v1/audio/transcriptions";

// Model and max_tokens are fixed server-side, not client-controlled - a modified client
// can't request a longer/pricier completion than the app's own cost budget allows. 600
// tokens is enough room for a genuinely detailed answer (e.g. explaining trigonometry) when
// the system prompt's length hint calls for one, while still bounding worst-case cost -
// gpt-4o-mini is cheap enough per token that this is a non-issue either way.
const MODEL = "gpt-4o-mini";
const MAX_TOKENS = 600;

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

export async function chatCompletion(
  apiKey: string,
  userText: string,
  systemPrompt: string
): Promise<string> {
  const messages: ChatMessage[] = [
    { role: "system", content: systemPrompt },
    { role: "user", content: userText },
  ];

  const res = await fetch(CHAT_URL, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model: MODEL,
      messages,
      temperature: 0.7,
      max_tokens: MAX_TOKENS,
    }),
  });

  if (!res.ok) {
    throw new Error(`OpenAI error: ${await parseErrorMessage(res)}`);
  }

  const data = (await res.json()) as {
    choices?: Array<{ message?: { content?: string } }>;
  };
  const text = data.choices?.[0]?.message?.content;
  if (!text) {
    throw new Error("No response from OpenAI");
  }
  return text;
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
