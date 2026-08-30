import { defineSecret } from "firebase-functions/params";

export const OPENAI_API_KEY = defineSecret("OPENAI_API_KEY");
export const ELEVENLABS_API_KEY = defineSecret("ELEVENLABS_API_KEY");
