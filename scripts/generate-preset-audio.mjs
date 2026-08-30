#!/usr/bin/env node
// One-off script: generates the 12 preset greeting/switch audio clips via the ElevenLabs
// API and writes them straight into app/src/main/res/raw/, overwriting the silent
// placeholders. Run once; after this, the app never calls ElevenLabs for these clips again
// (see PresetAudio.kt / ChatViewModel.playPresetGreeting).
//
// Usage:
//   node scripts/generate-preset-audio.mjs                    (regenerates all clips)
//   node scripts/generate-preset-audio.mjs --dialect=irish     (just one dialect's clips)
//   node scripts/generate-preset-audio.mjs --kind=goodbye      (just one kind, all dialects)
//   node scripts/generate-preset-audio.mjs --dialect=irish --kind=goodbye  (just one clip)
// It will prompt for your ElevenLabs API key (input is hidden, never written to disk,
// never sent anywhere but api.elevenlabs.io).

import { writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import readline from "node:readline";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const RAW_DIR = path.join(__dirname, "..", "app", "src", "main", "res", "raw");

// Must match app/src/main/java/com/dialect/voice/audio/PresetAudio.kt exactly - the text
// shown in the chat bubble comes from that file, this just has to say the same thing.
const CLIPS = [
  { dialect: "geordie", kind: "welcome", voiceId: "lYz97gZSO1IVncLkczs4",
    text: "Now then, welcome to WhyAI - howay, what can I do for ya?" },
  { dialect: "geordie", kind: "switch", voiceId: "lYz97gZSO1IVncLkczs4",
    text: "Reet, you're listening to Geordie now, pet." },
  { dialect: "geordie", kind: "upsell", voiceId: "lYz97gZSO1IVncLkczs4",
    text: "Ye divvent get nowt for free, ya radgie! Buy some credit, ya tight get." },
  { dialect: "geordie", kind: "goodbye", voiceId: "lYz97gZSO1IVncLkczs4",
    text: "Reet, I'm off then - ta-ra, pet." },
  { dialect: "geordie", kind: "egg", voiceId: "lYz97gZSO1IVncLkczs4",
    text: "I can tell ya to shut yer bleedin' gob and stop ticklin' me fanny with yer nonsense shite, ya thick radgie bastard - buy some credit and hear us say it proper." },
  { dialect: "geordie", kind: "randomegg", voiceId: "lYz97gZSO1IVncLkczs4",
    text: "Blah blah blah blah, you wear your mother's bra!" },
  { dialect: "geordie", kind: "applejuggler", voiceId: "lYz97gZSO1IVncLkczs4",
    text: "Whoop, whoop, whoopedy whoopy woopersville!" },

  { dialect: "scouse", kind: "welcome", voiceId: "m3ERpbBFjTAqD5PJozID",
    text: "Alright la, welcome to WhyAI - what's the craic, how can I help ya?" },
  { dialect: "scouse", kind: "switch", voiceId: "m3ERpbBFjTAqD5PJozID",
    text: "Sound - you're listening to Scouse now, la." },
  { dialect: "scouse", kind: "upsell", voiceId: "m3ERpbBFjTAqD5PJozID",
    text: "Yer not getting nowt for free, ya soft lad! Buy some credit, ya tight get." },
  { dialect: "scouse", kind: "goodbye", voiceId: "m3ERpbBFjTAqD5PJozID",
    text: "Right, I'm off now - ta-ra, la." },
  { dialect: "scouse", kind: "egg", voiceId: "m3ERpbBFjTAqD5PJozID",
    text: "I can tell ya to naff off and stop geggin' in like a soft shite sket, ya boss-ache bevvied muppet - buy some credit and hear the rest, la." },
  { dialect: "scouse", kind: "randomegg", voiceId: "m3ERpbBFjTAqD5PJozID",
    text: "Blah blah blah blah, you wear your mother's bra!" },
  { dialect: "scouse", kind: "applejuggler", voiceId: "m3ERpbBFjTAqD5PJozID",
    text: "Whoop, whoop, whoopedy whoopy woopersville!" },

  { dialect: "cockney", kind: "welcome", voiceId: "EQx6HGDYjkDpcli6vorJ",
    text: "Alright guv, welcome to WhyAI - what can I do ya for?" },
  { dialect: "cockney", kind: "switch", voiceId: "EQx6HGDYjkDpcli6vorJ",
    text: "You're now earwigging Cockney, me old china." },
  { dialect: "cockney", kind: "upsell", voiceId: "EQx6HGDYjkDpcli6vorJ",
    text: "You ain't getting sod all for free, ya muppet! Buy some credit, ya tight-fisted git." },
  { dialect: "cockney", kind: "goodbye", voiceId: "EQx6HGDYjkDpcli6vorJ",
    text: "Right then, I'm off - ta-ra, me old china." },
  { dialect: "cockney", kind: "egg", voiceId: "EQx6HGDYjkDpcli6vorJ",
    text: "I can tell ya to sling yer hook and stop chattin' complete bollocks out yer loaf, ya thick muppet git - buy some credit and hear it for yourself." },
  { dialect: "cockney", kind: "randomegg", voiceId: "EQx6HGDYjkDpcli6vorJ",
    text: "Blah blah blah blah, you wear your mother's bra!" },
  { dialect: "cockney", kind: "applejuggler", voiceId: "EQx6HGDYjkDpcli6vorJ",
    text: "Whoop, whoop, whoopedy whoopy woopersville!" },

  { dialect: "glaswegian", kind: "welcome", voiceId: "s07KcA1KjfdDAsyJ87HW",
    text: "Welcome to WhyAI pal, whats the hampden." },
  { dialect: "glaswegian", kind: "switch", voiceId: "s07KcA1KjfdDAsyJ87HW",
    text: "Right, yer listening tae Glaswegian noo, pal." },
  { dialect: "glaswegian", kind: "upsell", voiceId: "s07KcA1KjfdDAsyJ87HW",
    text: "Ye dinnae get hee haw for free, ya bampot! Buy some credit, ya cheapskate bawbag." },
  { dialect: "glaswegian", kind: "goodbye", voiceId: "s07KcA1KjfdDAsyJ87HW",
    text: "Right, ah'm aff noo - see ye, pal." },
  { dialect: "glaswegian", kind: "egg", voiceId: "s07KcA1KjfdDAsyJ87HW",
    text: "I can tell ye tae fuck off and stop fannying aboot, ya needle-nosed jakey prick. You're an absolute Bawbag - buy some credit and hear the rest, ya numpty." },
  { dialect: "glaswegian", kind: "randomegg", voiceId: "s07KcA1KjfdDAsyJ87HW",
    text: "Blah blah blah blah, you wear your mother's bra!" },
  { dialect: "glaswegian", kind: "applejuggler", voiceId: "s07KcA1KjfdDAsyJ87HW",
    text: "Whoop, whoop, whoopedy whoopy woopersville!" },

  { dialect: "irish", kind: "welcome", voiceId: "UwtFVYnvYG6hxAbc4I6T",
    text: "Story, welcome to WhyAI - what's the craic, how can I help ya?" },
  { dialect: "irish", kind: "switch", voiceId: "UwtFVYnvYG6hxAbc4I6T",
    text: "Grand, you're listening to Irish now, sure look." },
  { dialect: "irish", kind: "upsell", voiceId: "UwtFVYnvYG6hxAbc4I6T",
    text: "You're not getting feck all for free, ya eejit! Buy some credit, ya tight article." },
  { dialect: "irish", kind: "goodbye", voiceId: "UwtFVYnvYG6hxAbc4I6T",
    text: "Right so, I'm off now - safe home, sure look." },
  { dialect: "irish", kind: "egg", voiceId: "UwtFVYnvYG6hxAbc4I6T",
    text: "I can tell ya to feck off and stop actin' the bollocks, ya thick eejit gowl - buy some credit and hear the rest, sure look." },
  { dialect: "irish", kind: "randomegg", voiceId: "UwtFVYnvYG6hxAbc4I6T",
    text: "Blah blah blah blah, you wear your mother's bra!" },
  { dialect: "irish", kind: "applejuggler", voiceId: "UwtFVYnvYG6hxAbc4I6T",
    text: "Whoop, whoop, whoopedy whoopy woopersville!" },

  { dialect: "welsh", kind: "welcome", voiceId: "DikmR0aoFXAp1A3NcovW",
    text: "Now then bach, welcome to WhyAI - what can I do for you, love?" },
  { dialect: "welsh", kind: "switch", voiceId: "DikmR0aoFXAp1A3NcovW",
    text: "Tidy - you're listening to Welsh now, isn't it." },
  { dialect: "welsh", kind: "upsell", voiceId: "DikmR0aoFXAp1A3NcovW",
    text: "Not a word out of me for free, bach - buy some credit, ya tight aul' twp." },
  { dialect: "welsh", kind: "goodbye", voiceId: "DikmR0aoFXAp1A3NcovW",
    text: "Right then bach, I'm off now - hwyl, love." },
  { dialect: "welsh", kind: "egg", voiceId: "DikmR0aoFXAp1A3NcovW",
    text: "I can tell you to shut your chopsy little gob and stop being a twp little slapper, bach, ya cheeky bugger - buy some credit and hear the rest, love." },
  { dialect: "welsh", kind: "randomegg", voiceId: "DikmR0aoFXAp1A3NcovW",
    text: "Blah blah blah blah, you wear your mother's bra!" },
  { dialect: "welsh", kind: "applejuggler", voiceId: "DikmR0aoFXAp1A3NcovW",
    text: "Whoop, whoop, whoopedy whoopy woopersville!" },

  { dialect: "indian", kind: "welcome", voiceId: "WtIqwF5CWCkaZSGmvsm1",
    text: "Arre, welcome to WhyAI, yaar - what can I do for you?" },
  { dialect: "indian", kind: "switch", voiceId: "WtIqwF5CWCkaZSGmvsm1",
    text: "Achha, you're listening to Indian English now, yaar." },
  { dialect: "indian", kind: "upsell", voiceId: "WtIqwF5CWCkaZSGmvsm1",
    text: "Arre, nothing free-free hai yaar! Buy some credit, don't be such a kanjoos." },
  { dialect: "indian", kind: "goodbye", voiceId: "WtIqwF5CWCkaZSGmvsm1",
    text: "Achha, I'm off now - chalo, bye yaar." },
  { dialect: "indian", kind: "egg", voiceId: "WtIqwF5CWCkaZSGmvsm1",
    text: "Arre, I can tell you to shut up and stop talking bloody bakwas nonsense, ya bewakoof gadha - buy some credit and hear the rest, yaar." },
  { dialect: "indian", kind: "randomegg", voiceId: "WtIqwF5CWCkaZSGmvsm1",
    text: "Blah blah blah blah, you wear your mother's bra!" },
  { dialect: "indian", kind: "applejuggler", voiceId: "WtIqwF5CWCkaZSGmvsm1",
    text: "Whoop, whoop, whoopedy whoopy woopersville!" },

  { dialect: "australian", kind: "welcome", voiceId: "9B2Vd5yQ7rKaqNmzGdy1",
    text: "G'day, welcome to WhyAI, mate - what can I do ya for?" },
  { dialect: "australian", kind: "switch", voiceId: "9B2Vd5yQ7rKaqNmzGdy1",
    text: "Righto, you're listening to Australian now, mate." },
  { dialect: "australian", kind: "upsell", voiceId: "9B2Vd5yQ7rKaqNmzGdy1",
    text: "You're not getting Buckley's for free, ya drongo! Buy some credit, ya tight-arse." },
  { dialect: "australian", kind: "goodbye", voiceId: "9B2Vd5yQ7rKaqNmzGdy1",
    text: "Righto, I'm off now - catch ya later, mate." },
  { dialect: "australian", kind: "egg", voiceId: "9B2Vd5yQ7rKaqNmzGdy1",
    text: "I can tell ya to get fucked and stop being a dead-set drongo bogan, ya useless tight-arse - buy some credit and hear the rest, mate." },
  { dialect: "australian", kind: "randomegg", voiceId: "9B2Vd5yQ7rKaqNmzGdy1",
    text: "Blah blah blah blah, you wear your mother's bra!" },
  { dialect: "australian", kind: "applejuggler", voiceId: "9B2Vd5yQ7rKaqNmzGdy1",
    text: "Whoop, whoop, whoopedy whoopy woopersville!" },

  { dialect: "geordie", kind: "maggotmuncher", voiceId: "lYz97gZSO1IVncLkczs4",
    text: "An American's fanny is an Englishman's arse, and that's why all their trousers are pants, ya maggot muncher!" },
  { dialect: "geordie", kind: "austinpowersdinner", voiceId: "s07KcA1KjfdDAsyJ87HW",
    text: "I ate a baby." },

  { dialect: "scouse", kind: "maggotmuncher", voiceId: "m3ERpbBFjTAqD5PJozID",
    text: "An American's fanny is an Englishman's arse, and that's why all their trousers are pants, ya maggot muncher!" },
  { dialect: "scouse", kind: "austinpowersdinner", voiceId: "s07KcA1KjfdDAsyJ87HW",
    text: "I ate a baby." },

  { dialect: "cockney", kind: "maggotmuncher", voiceId: "EQx6HGDYjkDpcli6vorJ",
    text: "An American's fanny is an Englishman's arse, and that's why all their trousers are pants, ya maggot muncher!" },
  { dialect: "cockney", kind: "austinpowersdinner", voiceId: "s07KcA1KjfdDAsyJ87HW",
    text: "I ate a baby." },

  { dialect: "glaswegian", kind: "maggotmuncher", voiceId: "s07KcA1KjfdDAsyJ87HW",
    text: "An American's fanny is an Englishman's arse, and that's why all their trousers are pants, ya maggot muncher!" },
  { dialect: "glaswegian", kind: "austinpowersdinner", voiceId: "s07KcA1KjfdDAsyJ87HW",
    text: "I ate a baby." },

  { dialect: "irish", kind: "maggotmuncher", voiceId: "UwtFVYnvYG6hxAbc4I6T",
    text: "An American's fanny is an Englishman's arse, and that's why all their trousers are pants, ya maggot muncher!" },
  { dialect: "irish", kind: "austinpowersdinner", voiceId: "s07KcA1KjfdDAsyJ87HW",
    text: "I ate a baby." },

  { dialect: "welsh", kind: "maggotmuncher", voiceId: "DikmR0aoFXAp1A3NcovW",
    text: "An American's fanny is an Englishman's arse, and that's why all their trousers are pants, ya maggot muncher!" },
  { dialect: "welsh", kind: "austinpowersdinner", voiceId: "s07KcA1KjfdDAsyJ87HW",
    text: "I ate a baby." },

  { dialect: "indian", kind: "maggotmuncher", voiceId: "WtIqwF5CWCkaZSGmvsm1",
    text: "An American's fanny is an Englishman's arse, and that's why all their trousers are pants, ya maggot muncher!" },
  { dialect: "indian", kind: "austinpowersdinner", voiceId: "s07KcA1KjfdDAsyJ87HW",
    text: "I ate a baby." },

  { dialect: "australian", kind: "maggotmuncher", voiceId: "9B2Vd5yQ7rKaqNmzGdy1",
    text: "An American's fanny is an Englishman's arse, and that's why all their trousers are pants, ya maggot muncher!" },
  { dialect: "australian", kind: "austinpowersdinner", voiceId: "s07KcA1KjfdDAsyJ87HW",
    text: "I ate a baby." },
];

// Same model/voice settings as functions/src/lib/elevenLabsProxy.ts, so these clips sound
// like the same voice the paid API would have produced.
const MODEL_ID = "eleven_flash_v2_5";
const VOICE_SETTINGS = { stability: 0.5, similarity_boost: 0.75, style: 1.0 };

function promptHidden(question) {
  return new Promise((resolve) => {
    process.stdout.write(question);
    const stdin = process.stdin;
    const wasRaw = stdin.isRaw;
    stdin.setRawMode(true);
    stdin.resume();
    stdin.setEncoding("utf8");

    let input = "";
    const onData = (char) => {
      switch (char) {
        case "\n":
        case "\r":
        case "\u0004":
          stdin.setRawMode(wasRaw ?? false);
          stdin.pause();
          stdin.removeListener("data", onData);
          process.stdout.write("\n");
          resolve(input.trim());
          break;
        case "\u0003": // Ctrl+C
          process.stdout.write("\n");
          process.exit(1);
          break;
        case "\u007f": // backspace
          input = input.slice(0, -1);
          break;
        default:
          input += char;
          break;
      }
    };
    stdin.on("data", onData);
  });
}

async function synthesize(apiKey, voiceId, text) {
  const res = await fetch(`https://api.elevenlabs.io/v1/text-to-speech/${voiceId}`, {
    method: "POST",
    headers: {
      "xi-api-key": apiKey,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ text, model_id: MODEL_ID, voice_settings: VOICE_SETTINGS }),
  });

  if (!res.ok) {
    throw new Error(`ElevenLabs error ${res.status}: ${await res.text()}`);
  }

  return Buffer.from(await res.arrayBuffer());
}

async function main() {
  const dialectArg = process.argv.find((a) => a.startsWith("--dialect="));
  const kindArg = process.argv.find((a) => a.startsWith("--kind="));
  const dialectFilter = dialectArg ? dialectArg.slice("--dialect=".length) : null;
  const kindFilter = kindArg ? kindArg.slice("--kind=".length) : null;

  const clips = CLIPS.filter(
    (c) => (!dialectFilter || c.dialect === dialectFilter) && (!kindFilter || c.kind === kindFilter)
  );

  if (clips.length === 0) {
    console.error(`No clips found for dialect="${dialectFilter}" kind="${kindFilter}".`);
    process.exit(1);
  }

  const apiKey = await promptHidden("Enter your ElevenLabs API key: ");
  if (!apiKey) {
    console.error("No key entered, aborting.");
    process.exit(1);
  }

  console.log(`\nGenerating ${clips.length} clip(s) into ${RAW_DIR}\n`);

  for (const clip of clips) {
    const filename = `${clip.kind}_${clip.dialect}.mp3`;
    process.stdout.write(`  ${filename} ... `);
    try {
      const audio = await synthesize(apiKey, clip.voiceId, clip.text);
      await writeFile(path.join(RAW_DIR, filename), audio);
      console.log(`done (${(audio.length / 1024).toFixed(0)} KB)`);
    } catch (e) {
      console.log("FAILED");
      console.error(`    ${e.message}`);
    }
  }

  console.log("\nAll done. Rebuild/reinstall the app to pick up the new audio.");
}

main();
