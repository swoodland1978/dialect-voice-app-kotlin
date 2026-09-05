package com.dialect.voice.audio

import com.dialect.voice.R

// Bundled on-device audio for moments we speak to the user without hitting the paid
// ElevenLabs API: first app open, switching accent, pitching someone with no TTS credit
// left (whether they've never unlocked voice or unlocked it and used up their balance - the
// spoken line is generic enough to cover both), signing out, and the "what can you say"
// easter egg (see ChatViewModel.EASTER_EGG_TRIGGERS). The UI is voice-only/textless (see
// ChatScreen's AnimatedMascot and PaywallScreen) so `text` here isn't rendered anywhere -
// it exists purely as the source-of-truth transcript the audioRes must actually say, and as
// a reference when re-recording. audioRes points at a raw resource that must actually say
// the same thing in that dialect's voice - swap the placeholder files in res/raw/ for real
// recordings, keeping these exact filenames.
data class PresetPrompt(val text: String, val audioRes: Int)

data class PresetGreeting(
    val welcome: PresetPrompt,
    val switch: PresetPrompt,
    val noCredit: PresetPrompt,
    val goodbye: PresetPrompt,
    val easterEgg: PresetPrompt
)

val PRESET_AUDIO: Map<String, PresetGreeting> = mapOf(
    "geordie" to PresetGreeting(
        welcome = PresetPrompt(
            "Now then, welcome to WhyAI - howay, what can I do for ya?",
            R.raw.welcome_geordie
        ),
        switch = PresetPrompt(
            "Reet, you're listening to Geordie now, pet.",
            R.raw.switch_geordie
        ),
        noCredit = PresetPrompt(
            "Ye divvent get nowt for free, ya radgie! Buy some credit, ya tight get.",
            R.raw.upsell_geordie
        ),
        goodbye = PresetPrompt(
            "Reet, I'm off then - ta-ra, pet.",
            R.raw.goodbye_geordie
        ),
        easterEgg = PresetPrompt(
            "I can tell ya to shut yer bleedin' gob and stop ticklin' me fanny with yer nonsense shite, ya thick radgie bastard - buy some credit and hear us say it proper.",
            R.raw.egg_geordie
        )
    ),
    "scouse" to PresetGreeting(
        welcome = PresetPrompt(
            "Alright la, welcome to WhyAI - what's the craic, how can I help ya?",
            R.raw.welcome_scouse
        ),
        switch = PresetPrompt(
            "Sound - you're listening to Scouse now, la.",
            R.raw.switch_scouse
        ),
        noCredit = PresetPrompt(
            "Yer not getting nowt for free, ya soft lad! Buy some credit, ya tight get.",
            R.raw.upsell_scouse
        ),
        goodbye = PresetPrompt(
            "Right, I'm off now - ta-ra, la.",
            R.raw.goodbye_scouse
        ),
        easterEgg = PresetPrompt(
            "I can tell ya to naff off and stop geggin' in like a soft shite sket, ya boss-ache bevvied muppet - buy some credit and hear the rest, la.",
            R.raw.egg_scouse
        )
    ),
    "cockney" to PresetGreeting(
        welcome = PresetPrompt(
            "Alright guv, welcome to WhyAI - what can I do ya for?",
            R.raw.welcome_cockney
        ),
        switch = PresetPrompt(
            "You're now earwigging Cockney, me old china.",
            R.raw.switch_cockney
        ),
        noCredit = PresetPrompt(
            "You ain't getting sod all for free, ya muppet! Buy some credit, ya tight-fisted git.",
            R.raw.upsell_cockney
        ),
        goodbye = PresetPrompt(
            "Right then, I'm off - ta-ra, me old china.",
            R.raw.goodbye_cockney
        ),
        easterEgg = PresetPrompt(
            "I can tell ya to sling yer hook and stop chattin' complete bollocks out yer loaf, ya thick muppet git - buy some credit and hear it for yourself.",
            R.raw.egg_cockney
        )
    ),
    "glaswegian" to PresetGreeting(
        welcome = PresetPrompt(
            "Welcome to WhyAI pal, whats the hampden.",
            R.raw.welcome_glaswegian
        ),
        switch = PresetPrompt(
            "Right, yer listening tae Glaswegian noo, pal.",
            R.raw.switch_glaswegian
        ),
        noCredit = PresetPrompt(
            "Ye dinnae get hee haw for free, ya bampot! Buy some credit, ya cheapskate bawbag.",
            R.raw.upsell_glaswegian
        ),
        goodbye = PresetPrompt(
            "Right, ah'm aff noo - see ye, pal.",
            R.raw.goodbye_glaswegian
        ),
        easterEgg = PresetPrompt(
            "I can tell ye tae fuck off and stop fannying aboot, ya needle-nosed jakey prick. You're an absolute Bawbag - buy some credit and hear the rest, ya numpty.",
            R.raw.egg_glaswegian
        )
    ),
    "irish" to PresetGreeting(
        welcome = PresetPrompt(
            "Story, welcome to WhyAI - what's the craic, how can I help ya?",
            R.raw.welcome_irish
        ),
        switch = PresetPrompt(
            "Grand, you're listening to Irish now, sure look.",
            R.raw.switch_irish
        ),
        noCredit = PresetPrompt(
            "You're not getting feck all for free, ya eejit! Buy some credit, ya tight article.",
            R.raw.upsell_irish
        ),
        goodbye = PresetPrompt(
            "Right so, I'm off now - safe home, sure look.",
            R.raw.goodbye_irish
        ),
        easterEgg = PresetPrompt(
            "I can tell ya to feck off and stop actin' the bollocks, ya thick eejit gowl - buy some credit and hear the rest, sure look.",
            R.raw.egg_irish
        )
    ),
    "welsh" to PresetGreeting(
        welcome = PresetPrompt(
            "Now then bach, welcome to WhyAI - what can I do for you, love?",
            R.raw.welcome_welsh
        ),
        switch = PresetPrompt(
            "Tidy - you're listening to Welsh now, isn't it.",
            R.raw.switch_welsh
        ),
        noCredit = PresetPrompt(
            "Not a word out of me for free, bach - buy some credit, ya tight aul' twp.",
            R.raw.upsell_welsh
        ),
        goodbye = PresetPrompt(
            "Right then bach, I'm off now - hwyl, love.",
            R.raw.goodbye_welsh
        ),
        easterEgg = PresetPrompt(
            "I can tell you to shut your chopsy little gob and stop being a twp little slapper, bach, ya cheeky bugger - buy some credit and hear the rest, love.",
            R.raw.egg_welsh
        )
    ),
    "indian" to PresetGreeting(
        welcome = PresetPrompt(
            "Arre, welcome to WhyAI, yaar - what can I do for you?",
            R.raw.welcome_indian
        ),
        switch = PresetPrompt(
            "Achha, you're listening to Indian English now, yaar.",
            R.raw.switch_indian
        ),
        noCredit = PresetPrompt(
            "Arre, nothing free-free hai yaar! Buy some credit, don't be such a kanjoos.",
            R.raw.upsell_indian
        ),
        goodbye = PresetPrompt(
            "Achha, I'm off now - chalo, bye yaar.",
            R.raw.goodbye_indian
        ),
        easterEgg = PresetPrompt(
            "Arre, I can tell you to shut up and stop talking bloody bakwas nonsense, ya bewakoof gadha - buy some credit and hear the rest, yaar.",
            R.raw.egg_indian
        )
    ),
    "australian" to PresetGreeting(
        welcome = PresetPrompt(
            "G'day, welcome to WhyAI, mate - what can I do ya for?",
            R.raw.welcome_australian
        ),
        switch = PresetPrompt(
            "Righto, you're listening to Australian now, mate.",
            R.raw.switch_australian
        ),
        noCredit = PresetPrompt(
            "You're not getting Buckley's for free, ya drongo! Buy some credit, ya tight-arse.",
            R.raw.upsell_australian
        ),
        goodbye = PresetPrompt(
            "Righto, I'm off now - catch ya later, mate.",
            R.raw.goodbye_australian
        ),
        easterEgg = PresetPrompt(
            "I can tell ya to get fucked and stop being a dead-set drongo bogan, ya useless tight-arse - buy some credit and hear the rest, mate.",
            R.raw.egg_australian
        )
    )
)

// Nonsense non-sequitur easter eggs - a completely arbitrary trigger phrase gets the exact
// same silly line back, said in whichever accent is currently selected. Unlike the rest of
// this file, the text is identical across dialects (only the voice differs), so this is kept
// as its own extensible list rather than more one-off fields on PresetGreeting - add another
// entry here (plus 8 raw files, one per dialect) for the next one of these.
data class RandomEgg(
    // Normalized (lowercase, alphanumeric+space only, whitespace collapsed) trigger phrases -
    // see ChatViewModel.normalizeForTrigger.
    val triggers: Set<String>,
    val text: String,
    val audioResByDialect: Map<String, Int>
)

val RANDOM_EGGS: List<RandomEgg> = listOf(
    RandomEgg(
        triggers = setOf(
            "k pop is good this year",
            "kpop is good this year"
        ),
        text = "Blah blah blah blah, you wear your mother's bra!",
        audioResByDialect = mapOf(
            "geordie" to R.raw.randomegg_geordie,
            "scouse" to R.raw.randomegg_scouse,
            "cockney" to R.raw.randomegg_cockney,
            "glaswegian" to R.raw.randomegg_glaswegian,
            "irish" to R.raw.randomegg_irish,
            "welsh" to R.raw.randomegg_welsh,
            "indian" to R.raw.randomegg_indian,
            "australian" to R.raw.randomegg_australian
        )
    ),
    RandomEgg(
        triggers = setOf("apple juggler"),
        text = "Whoop, whoop, whoopedy whoopy woopersville!",
        audioResByDialect = mapOf(
            "geordie" to R.raw.applejuggler_geordie,
            "scouse" to R.raw.applejuggler_scouse,
            "cockney" to R.raw.applejuggler_cockney,
            "glaswegian" to R.raw.applejuggler_glaswegian,
            "irish" to R.raw.applejuggler_irish,
            "welsh" to R.raw.applejuggler_welsh,
            "indian" to R.raw.applejuggler_indian,
            "australian" to R.raw.applejuggler_australian
        )
    ),
    // Hundreds of arbitrary, mostly-unlikely trigger phrases sharing one payoff line - adding
    // a genuinely distinct response per trigger here would mean thousands of audio files
    // (each response needs 8, one per dialect), so this is deliberately many-triggers-to-one-
    // response rather than the 1:1 pattern the other two use.
    RandomEgg(
        triggers = setOf(
            "tom cruise", "banana hammock", "exit banana", "proper boabie", "keanu reeves",
            "dolly parton", "mr bean", "danny dyer", "ryan reynolds", "dwayne johnson",
            "betty white", "waffle ninja", "disco kettle", "velvet hedgehog", "spicy elbow",
            "moist biscuit", "rogue turnip", "banjo weather", "crusty wizard",
            "haunted cardigan", "spectacular gravy", "wobbly castle", "sneaky trombone",
            "angry kettle", "purple accordion", "soggy sandwich", "turbo hamster",
            "lunar biscuit", "grumpy walrus", "sparkly turnip", "secret waffle",
            "flaming teapot", "crispy unicorn", "wild spatula", "lazy trombone",
            "cosmic sausage", "squeaky ferret", "rubber goose", "midnight biscuit",
            "banjo emergency", "fluffy dictator", "plastic walrus", "jazz hamster",
            "thunder biscuit", "velvet spatula", "cheeky penguin", "rogue biscuit",
            "spicy hamster", "golden turnip", "haunted spatula", "wobbly ferret",
            "banjo incident", "moist trombone", "crusty penguin", "spectral biscuit",
            "rubber hamster", "sneaky waffle", "purple ferret", "turbo penguin",
            "angry spatula", "cosmic turnip", "lazy walrus", "squeaky biscuit",
            "plastic penguin", "jazz turnip", "thunder ferret", "velvet walrus",
            "cheeky spatula", "golden ferret", "haunted turnip", "wobbly biscuit",
            "flaming ferret", "crispy trombone", "wild penguin", "lunar ferret",
            "grumpy spatula", "sparkly biscuit", "secret turnip", "midnight ferret",
            "fluffy spatula", "rogue penguin", "spicy turnip", "moist ferret",
            "crusty spatula", "spectral turnip", "rubber biscuit", "sneaky penguin",
            "purple spatula", "turbo ferret", "angry turnip", "cosmic spatula",
            "lazy biscuit", "squeaky turnip", "plastic ferret", "jazz spatula",
            "thunder penguin", "velvet turnip", "cheeky ferret", "golden spatula",
            "haunted penguin", "wobbly turnip", "flaming spatula", "crispy ferret",
            "wild biscuit", "lunar spatula", "grumpy penguin", "sparkly ferret",
            "secret spatula", "disco pants", "gravy emergency", "tactical trombone",
            "interpretive kettle", "unexpected walrus", "aggressive biscuit",
            "competitive napping", "extreme gardening", "suspicious gravy",
            "mandatory disco", "banana diplomacy", "kettle uprising", "rogue umbrella",
            "underground spatula", "vintage hamster", "artisan turnip", "gourmet ferret",
            "premium nonsense", "deluxe walrus", "classic mayhem", "retro biscuit",
            "industrial cardigan", "organic chaos", "freelance penguin",
            "part time wizard", "amateur dragon", "professional nonsense",
            "certified chaos", "qualified walrus", "authorized mayhem", "diplomatic gravy",
            "olympic napping", "surprise trombone", "emergency cardigan", "budget wizard",
            "luxury turnip", "discount dragon", "executive hamster", "senior spatula",
            "junior walrus", "acting kettle", "temporary unicorn", "interim biscuit",
            "chief penguin", "regional ferret", "national gravy", "international biscuit",
            "local dragon", "global spatula", "urban walrus", "rural hamster",
            "coastal turnip", "highland biscuit", "lowland ferret", "alpine spatula",
            "arctic penguin", "tropical walrus", "desert hamster", "polar biscuit",
            "equatorial ferret"
        ),
        text = "An American's fanny is an Englishman's arse, and that's why all their trousers are pants, ya maggot muncher!",
        audioResByDialect = mapOf(
            "geordie" to R.raw.maggotmuncher_geordie,
            "scouse" to R.raw.maggotmuncher_scouse,
            "cockney" to R.raw.maggotmuncher_cockney,
            "glaswegian" to R.raw.maggotmuncher_glaswegian,
            "irish" to R.raw.maggotmuncher_irish,
            "welsh" to R.raw.maggotmuncher_welsh,
            "indian" to R.raw.maggotmuncher_indian,
            "australian" to R.raw.maggotmuncher_australian
        )
    ),
    // "Fat Bastard" only fires said on its own, never mid-sentence - that's automatic here,
    // not special-cased: ChatViewModel matches the *entire* normalized message against this
    // set (never a substring/contains check), same as every other trigger in this file.
    // Recorded in a Glasgow accent specifically (see generate-preset-audio.mjs's voiceId for
    // this kind - it's pinned to the Glaswegian voice for all 8 dialect entries below, not
    // each dialect's own voice), so it sounds the same regardless of which accent is selected.
    RandomEgg(
        triggers = setOf("austin powers dinner", "fat bastard"),
        text = "I ate a baby.",
        audioResByDialect = mapOf(
            "geordie" to R.raw.austinpowersdinner_geordie,
            "scouse" to R.raw.austinpowersdinner_scouse,
            "cockney" to R.raw.austinpowersdinner_cockney,
            "glaswegian" to R.raw.austinpowersdinner_glaswegian,
            "irish" to R.raw.austinpowersdinner_irish,
            "welsh" to R.raw.austinpowersdinner_welsh,
            "indian" to R.raw.austinpowersdinner_indian,
            "australian" to R.raw.austinpowersdinner_australian
        )
    )
)
