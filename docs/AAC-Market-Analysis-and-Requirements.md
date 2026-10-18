# AAC App — Market Analysis & Product Requirements

**Status:** Draft v0.1 · **Date:** 2026-10-18
**Target platforms (in order):** Android phone (Pixel) → Android kids tablet (8–11") → iOS/iPadOS and/or Web

---

## 1. Executive Summary

Augmentative and Alternative Communication (AAC) apps give a voice to people who can't rely on speech: autistic children and adults, people with cerebral palsy, Down syndrome, apraxia, ALS/MND, aphasia after stroke, and others. The market is split into three parts:

1. **Premium "robust" iPad systems** (Proloquo2Go, TD Snap, LAMP Words for Life, TouchChat) with deep, research-backed vocabularies. They're expensive, mostly iOS-only, and tied to vendor ecosystems.
2. **Dedicated speech-generating devices (SGDs)** from Tobii Dynavox and PRC-Saltillo. These are insurance-funded and locked down, and they cost thousands of dollars.
3. **Cheaper or free apps, many on Android** (Avaz, Spoken, CoughDrop, Cboard, Jabtalk, Leeloo, Otsimo). They're easier to get, but they vary in vocabulary depth, motor-planning consistency, and polish.

**The opportunity:** Android has no best-in-class robust AAC app. Families on Android, which includes most low-cost kids tablets, schools on tight budgets, and most users outside the US, are underserved. Meanwhile, on-device AI (Gemini Nano on Pixel, on-device speech recognition and TTS) makes it possible to build features that legacy vendors can't easily add to their decade-old codebases. Legacy vendors are adding AI (PRC-Saltillo's 2026.1 update added AI features to its iOS apps), but they're doing it on iOS first.

**The proposed product:** an Android-first, offline-first, privacy-first robust AAC system that:
- matches the clinical essentials (core vocabulary, consistent motor planning, grammar support, switch and scanning access);
- removes the market's common frustrations (price, lock-in, slow editing, robotic child voices, poor partner support);
- adds a layer of AI-assisted communication that **never moves buttons and never speaks for the user without their selection**.

---

## 2. Market Landscape

### 2.1 Major competitors

| Product | Vendor | Platforms | Price (approx.) | Strengths | Weaknesses / gaps |
|---|---|---|---|---|---|
| **Proloquo2Go** | AssistiveWare | iOS only | ~$250 one-time | Polished UI, Crescendo core vocabulary, good grammar, strong teaching resources | No Android; expensive; editing on phone is cramped |
| **Proloquo / Proloquo4Text** | AssistiveWare | iOS | Subscription / one-time | Modern design, literacy focus (4Text) | iOS only |
| **TD Snap (Core First)** | Tobii Dynavox | iOS, Windows, TD devices | ~$50 app + subscriptions / device funding | Pageset ecosystem, eye-gaze leader, Boardmaker/PCS symbols, Pathways teaching | Best experience is on expensive hardware; PCS licensing; complex editing |
| **LAMP Words for Life** | PRC-Saltillo | iOS (iPad/iPhone), PRC devices | ~$300 | Motor-planning approach (single fixed location per word), strong autism outcomes | Steep learning curve for partners; expensive; iOS/devices only |
| **TouchChat (w/ WordPower)** | PRC-Saltillo | iOS, PRC devices | ~$150–300 | WordPower vocabulary, highly customizable | Dated UI; iOS only |
| **Avaz** | Avaz (acquired by PRC-Saltillo) | iOS, **Android** | ~$10/mo or ~$100/yr | Graded vocabulary levels, multilingual (strong in India), Android available | Feature parity and future roadmap uncertain after the acquisition |
| **Spoken** | Spoken | iOS, **Android** | Free + premium (~$13/mo) | Text-based with predictive phrases, adapts to user, good for literate adults/teens | Not symbol-first; less suited to early communicators |
| **CoughDrop** | CoughDrop (open source) | Web, iOS, **Android**, Windows | ~$9/mo or ~$295 lifetime | Cloud sync, team collaboration, OBF format, eye-gaze support | Web-wrapper feel; lower Play Store ratings; performance on cheap tablets |
| **Cboard** | Cboard (open source) | Web/PWA, **Android** | Free | Free, multilingual, ARASAAC/Mulberry symbols | Basic feature set; limited grammar and morphology |
| **Grid 3 / Grid for iPad** | Smartbox | Windows, iOS | High | Powerful access methods, computer and environment control | Complex to program; Windows-centric |
| **GoTalk NOW** | Attainment | iOS | ~$80 | Simple, visual scene displays, good for beginners | Not a robust language system |
| **Jabtalk, Leeloo, Let Me Talk, Otsimo** | Various | **Android** | Free–low | Accessible entry point | Small vocabularies, ads/IAP, weak clinical grounding |
| **Speak for Yourself** | SFY | iOS | ~$300 | Motor-planning based, "babble" mode (explore all words) | iOS only |
| **Lingraphica / TalkPath** | Lingraphica | Devices, iOS/Android companion apps | Device funding | Adult aphasia specialization | Narrow audience |

> **Verify before publishing externally:** platform availability and prices change often. Several third-party lists disagree on whether TD Snap and LAMP WFL have Android Play Store listings. Check Google Play directly.

### 2.2 Dedicated device (SGD) market

- **Tobii Dynavox** (I-series, TD Pilot for iPad eye gaze, TD Navio) and **PRC-Saltillo** (Accent, Via, NovaChat) dominate funded devices.
- In the US, Medicaid and private insurance fund SGDs as durable medical equipment (DME). Many states **won't fund a general-purpose tablet**, only "dedicated" locked devices. This is why vendors sell locked-down hardware.
- **Implication for us:** a fundable path later requires a **dedicated / kiosk mode** (the device can't be used for anything else), documentation and data that support SLP evaluation reports, and possibly a hardware partner. This is not a V1 concern, but the architecture should allow it.

### 2.3 Market trends (2024–2026)

1. **AI is arriving.** PRC-Saltillo announced AI features across LAMP WFL, TouchChat, Unity and Dialogue (2026.1, iOS). Research focuses on whole-utterance prediction, context-aware suggestions (conversation partner, environment, location), and LLM letter and word prediction.
2. **Consolidation.** PRC-Saltillo acquired Avaz, one of the better Android options, which creates uncertainty for Android users.
3. **Phones as AAC.** LAMP WFL came to iPhone; teens and adults want something discreet that isn't a big tablet.
4. **Eye gaze on mainstream devices.** iPadOS added native eye tracking; Google's Project Gameface (open source) enables face-gesture control on Android.
5. **Voice banking and personal voice.** iOS Personal Voice, ElevenLabs, and others for ALS users; demand for **age- and identity-appropriate voices** (kids, non-binary, regional accents) is still unmet.
6. **Neurodiversity-affirming practice.** Respect for autonomy, honoring all communication (including stimming and scripting), no compliance-only vocabulary ("I want" is not enough; users need "no", "stop", "I don't like that", jokes, and opinions).

### 2.4 Common user and clinician pain points (synthesized)

| Pain point | Who | Our response |
|---|---|---|
| Robust apps are iOS-only or very expensive | Families, schools, international users | Android-first, fair pricing, free core |
| Editing is slow and fiddly, especially on phones | Parents, SLPs | Fast edit mode, AI-assisted button creation, edit from a partner phone or web |
| Buttons move when vocabulary is added, which breaks motor memory | Users (especially autistic users) | Hard rule: fixed motor plan, with hide/reveal instead of rearranging |
| Robotic or wrong voices (adult voice on a 5-year-old) | Kids, teens, families | Kid and teen voices, voice customization, personal voice |
| Partners don't model; learning curve for caregivers | Families, teachers | Partner app, "where is it?" search, modeling mode, short coaching tips |
| Data loss: vocabulary gone after device breaks or reset | Everyone | Automatic versioned backup, export, print backup |
| Lock-in: can't move vocabulary between apps | SLPs, families | Open Board Format (OBF/OBZ) import and export |
| Device used for YouTube or games instead of talking | Parents, teachers | Kiosk or dedicated mode, with a balanced approach (see §6.10) |
| Hard to track progress for IEP goals | SLPs, teachers | Privacy-respecting usage analytics and goal reports |
| Slow conversation speed (often <20 wpm vs ~150 wpm speech) | Literate users, ALS | Prediction, AI-assisted utterance expansion, quick phrases |

---

## 3. Target Users & Personas

| Persona | Description | Key needs |
|---|---|---|
| **Maya, 4, autistic, minimally speaking** | Early communicator, emerging symbolic understanding | Large buttons, symbols, consistent motor plan, fun/engaging, durability on a kids tablet |
| **Jayden, 9, cerebral palsy** | Limited fine motor control, uses a keyguard and might need switch scanning | Big targets, dwell/hold settings, switch scanning, accept-on-release |
| **Sam, 15, autistic teen** | Literate, sometimes loses speech under stress, wants discretion | Phone-sized, typing with prediction, quick phrases, doesn't look "babyish" |
| **Linda, 62, ALS** | Progressive loss of speech and motor control | Voice banking, typing with strong prediction, later eye gaze and head tracking |
| **Robert, 70, aphasia after stroke** | Language impairment, not motor | Visual scene displays, photos, personal topics, simple layouts |
| **Parent / caregiver** | Sets up and edits, models language | Easy editing, modeling tools, coaching |
| **SLP / teacher** | Evaluates, programs, tracks goals | Templates, OBF import, data reports, multi-client management |

**V1 primary persona:** The product owner on a personal Pixel phone, as both builder and tester. Design for **Maya / Jayden / Sam** from day one so the tablet phase isn't a rewrite.

---

## 4. Product Principles (non-negotiable)

1. **Never take away the voice.** Speaking never requires an internet connection, an account, a subscription, or an AI service. Core communication is always free and offline.
2. **Motor plan is sacred.** A word's button location never changes unless the user or their team explicitly moves it. AI, prediction and context features live in **separate, dedicated zones**.
3. **AI suggests, the human decides.** Nothing is spoken that the user didn't select. AI output is clearly marked and editable.
4. **Robust language, not just requests.** Core words, grammar, morphology and spelling are available from day one. "Presume competence."
5. **Speed is accessibility.** Under 100 ms from tap to speech. Instant page changes. App launches straight to the communication board.
6. **Own your words.** Open formats, export at any time, automatic backups, no lock-in.
7. **Privacy by default.** What a person says is extremely sensitive data. It is processed on-device by default, and any cloud use is opt-in, explained, and minimal.
8. **Designed for the communication partner too.** AAC is a two-person activity.

---

## 5. Platform Strategy & Technical Recommendations

### 5.1 Recommended stack

| Layer | Recommendation | Rationale |
|---|---|---|
| Language | **Kotlin** | Native Android, first-class Google support |
| UI | **Jetpack Compose → Compose Multiplatform** | Native performance on Android now; shares UI with iOS later (Compose MP for iOS is stable) and with web via Kotlin/Wasm |
| Shared logic | **Kotlin Multiplatform (KMP)** module from day one | Vocabulary model, grammar engine, prediction, OBF import/export and sync are all shared across platforms |
| Storage | **SQLDelight** (KMP) or Room KMP; vocabulary as structured DB + OBF-compatible export | Fast queries, versioning, offline |
| TTS | Android `TextToSpeech` (Google voices) + **bundled neural voices** (e.g., Piper/Sherpa-ONNX on-device) + optional cloud premium voices | Offline guarantee; kid voices via fine-tuned models |
| Speech recognition (partner listening) | Android `SpeechRecognizer` on-device / ML Kit | For "Listen mode" (see §7) |
| On-device AI | **Gemini Nano via ML Kit GenAI APIs** (Pixel 9/10 and others), with fallback to a small bundled model (e.g., Gemma-class via MediaPipe LLM Inference) or an n-gram model | Private, offline, low latency |
| Symbols | Open sets: **Mulberry** (CC BY-SA), **OpenMoji** (CC BY-SA), **Twemoji** (CC BY), plus user photos. **ARASAAC** and **Sclera** are **CC BY-NC**, so they can't be used in a paid product without permission. Licensed sets (SymbolStix, PCS) later via commercial deals | Licensing matters for monetization |
| Interop | **Open Board Format (OBF/OBZ)** import/export | Import from CoughDrop and Cboard; anti-lock-in |
| Sync (later) | Optional end-to-end encrypted sync (e.g., CRDT-based document sync) | Multi-device and partner editing without a vendor reading the data |

**Alternative:** Flutter offers mature web and iOS from one codebase, but its native Android accessibility integration (TalkBack, switch access, keyboard) and its on-device AI integrations are less direct. Kotlin + KMP gives the best Android experience and still has a path to iOS and web. **Decision: Kotlin/Compose + KMP.**

### 5.2 Phased platform plan

| Phase | Target | Focus |
|---|---|---|
| **V1 (MVP)** | Personal Pixel phone (Android 14+) | Core communication loop, phone-optimized layouts, editing, TTS, prediction, backup |
| **V2** | Android kids tablet (8–11", low-end hardware: 3–4 GB RAM, Fire-class / Samsung Tab A / Lenovo kids tabs) | Larger grids, rugged UX, kiosk mode, switch scanning, performance on weak CPUs, partner app |
| **V3** | iOS/iPadOS and/or Web (PWA) | Compose Multiplatform port; web for SLP editing and the "try before you buy" experience |
| **V4** | Dedicated device / funding path | Kiosk-locked hardware, SLP report generation, insurance documentation |

**Note on Amazon Fire tablets:** These are the most common cheap kids tablets but run Fire OS without Google Play Services. Gemini Nano, Play-delivered voices and Play billing are unavailable there. Build so that all core features work **without Google Play Services** and AI features degrade gracefully.

---

## 6. Functional Requirements

Priority key: **P0** = V1 MVP (Pixel) · **P1** = V2 (tablet) · **P2** = V3+ · **R** = research/experiment

### 6.1 Communication board (core)

| ID | Requirement | Priority |
|---|---|---|
| CB-1 | Grid of buttons with symbol + label; tap speaks word/phrase and/or adds it to the message bar | P0 |
| CB-2 | Configurable grid sizes (phone: 3×4 up to 6×8; tablet: up to 10×15) | P0 |
| CB-3 | **Message bar (sentence strip)** that builds utterances; tap to speak all; backspace (word), clear, share/copy | P0 |
| CB-4 | Folders/pages with navigation; persistent "home" and "back"; optional auto-return to home after selection | P0 |
| CB-5 | **Core vocabulary board** included (~400+ high-frequency words) in a fixed motor-plan layout, with Fitzgerald Key / modified Fitzgerald color coding (customizable) | P0 |
| CB-6 | Fringe vocabulary categories (food, people, places, feelings, body, school, activities…) | P0 |
| CB-7 | **Keyboard page** with word prediction and spelling; speak as you type option (letter/word/sentence) | P0 |
| CB-8 | Quick phrases / scripts board (greetings, "wait, I'm talking", "I need a break", repairs) | P0 |
| CB-9 | **Always-available emergency/regulation strip** (e.g., "Stop", "Help", "I need a break", "Something hurts") reachable from any page | P0 |
| CB-10 | Multiple vocabulary levels (e.g., 12 / 36 / 60 / 84 / full) that **keep the same motor plan**: words are revealed in place rather than relocated | P1 |
| CB-11 | Visual scene displays (VSDs): photo with tappable hotspots | P1 |
| CB-12 | "Babble" / explore mode that shows all hidden words temporarily without changing settings | P1 |
| CB-13 | Button types: speak, navigate, speak+navigate, add-to-message-only, action (clear, speak, share, app action), audio clip, video | P0 (basic) / P1 (media) |
| CB-14 | Hide, mask or highlight buttons without moving them | P0 |

### 6.2 Language & grammar

| ID | Requirement | Priority |
|---|---|---|
| LG-1 | **Morphology engine:** inflect words via long-press or grammar button (go → goes/going/went/gone; dog → dogs; big → bigger/biggest) | P0 (English) |
| LG-2 | Smart grammar: optional auto-conjugation based on the subject in the message ("he + want" → "he wants") | P1 |
| LG-3 | Word prediction (n-gram + user-adaptive), shown in a **dedicated prediction row** | P0 |
| LG-4 | Multilingual support; bilingual boards that keep the **same motor plan** across languages (toggle EN ↔ ES) | P1 |
| LG-5 | Pronunciation dictionary / phonetic overrides per word | P0 |
| LG-6 | Abbreviation expansion (e.g., "hru" → "how are you") | P1 |

### 6.3 Voice & audio output

| ID | Requirement | Priority |
|---|---|---|
| VO-1 | Offline TTS with choice of system voices; rate, pitch and volume controls | P0 |
| VO-2 | **Bundled child and teen voices** (on-device neural) | P1 |
| VO-3 | Recorded audio per button (parent/sibling voice, a favorite song) | P0 |
| VO-4 | **Expressive speech:** tone buttons for question, excited, whisper, shout, sad and sarcastic (SSML/prosody) | P1 |
| VO-5 | Personal voice / voice banking import (record before speech loss) | P2 |
| VO-6 | Audio routing: speaker vs. Bluetooth; **private auditory feedback through an earbud** while speech goes to the speaker (for auditory scanning/previewing) | P1 |
| VO-7 | Speak while the screen is locked / quick access from lock screen or Quick Settings tile | P0 |

### 6.4 Access methods (motor access)

| ID | Requirement | Priority |
|---|---|---|
| AM-1 | Touch with configurable **hold time, dwell, accept-on-release, debounce (ignore repeated taps), touch-area enlargement** | P0 |
| AM-2 | Keyguard-friendly layouts (fixed margins, printable keyguard templates for 3D printing or laser cutting) | P1 |
| AM-3 | **Switch scanning** (1 and 2 switch; row/column, group, linear; auto and step) with Bluetooth switch support | P1 |
| AM-4 | **Partner-assisted auditory scanning** | P1 |
| AM-5 | **Head and face gesture control** using the front camera (Project Gameface-style: raise eyebrows = select, head move = pointer) | P2 |
| AM-6 | Eye-gaze support via external eye trackers (USB/Bluetooth), and research into camera-based gaze | R |
| AM-7 | Full TalkBack, Switch Access and external keyboard compatibility | P0 |
| AM-8 | Haptic feedback on select (configurable) | P0 |

### 6.5 Editing & customization

| ID | Requirement | Priority |
|---|---|---|
| ED-1 | In-place edit mode (locked behind a passcode or long-press gesture) | P0 |
| ED-2 | Add/edit button: label, spoken text, symbol (search library, camera, gallery, AI-generated), color, border, audio | P0 |
| ED-3 | **"Add a word" wizard** that suggests the correct grammatical category, color, folder and location, and offers inflections | P1 |
| ED-4 | Copy/paste/move buttons and pages; bulk edit | P0 |
| ED-5 | Unlimited **undo history & versioned snapshots** ("restore yesterday's board") | P0 |
| ED-6 | Templates: starter vocabularies by age and level | P0 |
| ED-7 | **OBF/OBZ import & export** | P1 |
| ED-8 | Remote editing from a partner phone or web editor | P2 |
| ED-9 | Print boards to PDF (no-tech backup, communication books, keyguard sizing) | P1 |

### 6.6 Symbols & visuals

| ID | Requirement | Priority |
|---|---|---|
| SV-1 | Bundled open symbol set (Mulberry + OpenMoji) with search, synonyms, and skin-tone/gender variants | P0 |
| SV-2 | Photos from camera/gallery with auto-crop | P0 |
| SV-3 | Symbol style consistency: borders, background colors and label position | P0 |
| SV-4 | High-contrast, dark mode and cortical visual impairment (CVI) friendly themes (e.g., black background, single highlight color, reduced clutter) | P1 |
| SV-5 | Licensed symbol sets (SymbolStix, PCS) | P2 |

### 6.7 Partner & team features

| ID | Requirement | Priority |
|---|---|---|
| PT-1 | **"Where is it?"** search: shows the path to a word with an animated highlight (for partners learning the system and for modeling) | P0 |
| PT-2 | **Partner/modeling app:** a parent's phone mirrors the child's board; taps on the partner's device show "ghost highlights" on the child's device to model language without taking it away from them | P2 |
| PT-3 | **Show-to-partner display:** flip the message to large text on screen (for noisy rooms and for people with hearing loss) | P0 |
| PT-4 | Coaching tips: short modeling suggestions for partners ("Try modeling 'more' and 'go' during play") | P1 |
| PT-5 | Team sharing: invite SLP, teacher or aide with role-based permissions | P2 |

### 6.8 Data, progress & reports

| ID | Requirement | Priority |
|---|---|---|
| DR-1 | Local, opt-in usage logging (words, timestamps, page) with a clear on/off indicator | P1 |
| DR-2 | Reports: unique words, mean length of utterance (MLU), core vs. fringe ratio, words-per-minute, new words this week, heat map over the board | P1 |
| DR-3 | IEP goal tracking (e.g., "uses 2-word combinations 10×/day") | P2 |
| DR-4 | Export de-identified reports as PDF/CSV for SLP evaluations and funding documentation | P2 |
| DR-5 | "Privacy pause": the user can turn off logging at any time (a teen's right to private conversation) | P1 |

### 6.9 Backup, sync & reliability

| ID | Requirement | Priority |
|---|---|---|
| BR-1 | Automatic local snapshots + export to a file (Drive/local storage) | P0 |
| BR-2 | Android Auto Backup integration | P0 |
| BR-3 | Optional end-to-end encrypted cloud sync across devices | P2 |
| BR-4 | Crash resilience: app restarts directly to the last board; never shows a blank screen | P0 |
| BR-5 | Low-battery warning to caregivers (optional notification to a partner phone) | P2 |

### 6.10 Device management & safety

| ID | Requirement | Priority |
|---|---|---|
| DM-1 | Edit lock (PIN or pattern; also "hold 3 seconds" option) | P0 |
| DM-2 | **Kiosk mode** via Android screen pinning (V1) and Device Owner lock-task mode (V2) | P1 |
| DM-3 | **Balanced access:** schedules or rules allowing specific apps (e.g., music, a timer) while keeping AAC one tap away. A floating "talk" bubble over other apps | P1 |
| DM-4 | Kid-safe: no ads, no external links without a parent gate, no social features for children | P0 |

---

## 7. Bold / Innovative Ideas

These ideas could differentiate the product. Each is labeled with feasibility and risk. **All AI features obey Principles 2 and 3:** they never move buttons, and they never speak without the user's selection.

### 7.1 🎧 "Listen mode": conversation-aware suggestions
On-device speech recognition hears the **partner's** question ("What do you want for lunch?") and fills a dedicated suggestion row with relevant reply words and phrases **from the user's own vocabulary**, such as "pizza", "sandwich", "not hungry", or "I don't know". It cuts navigation time drastically.
- Runs fully on-device (Gemini Nano / small model), with a visible mic indicator and an easy off switch.
- Also serves as live captions for users with hearing loss.
- *Feasibility: high on Pixel. Risk: privacy (mitigated by on-device processing and no storage).*

### 7.2 ✨ "Expand my words" (telegraphic → full sentence)
The user selects "want … park … mom". The AI offers 2–3 expansions: "I want to go to the park with Mom", "Mom, can we go to the park?". The user picks one or keeps their original words. This keeps authorship and adds speed. Optionally it learns the user's style ("make it sound like me").
- *A configurable SLP setting can disable it for language-learning goals where the user should build grammar themselves.*

### 7.3 📸 "Point & say": instant visual scenes from the camera
The user takes a photo of the playground, the fridge, or a menu. The app auto-detects objects and creates a temporary VSD with hotspots ("swing", "slide", "juice") linked to existing vocabulary. A menu photo becomes an orderable board.

### 7.4 🧭 Context-aware "fringe drawer" (no button movement)
The app uses time, location (home/school/grandma's), calendar events and connected Bluetooth devices (car = "car talk") to **suggest** a relevant fringe page in a side drawer. The core board never changes. Users or caregivers can approve suggested pages for each context.

### 7.5 🗣️ Identity voices
Most AAC voices are adult and generic. Offer **real kid and teen voices**, regional accents, gender-neutral voices, and a "sibling voice" blend (a sibling records 5 minutes, and a voice inspired by the family is created with consent). A voice should grow with the child: a voice "age" slider over the years.

### 7.6 🎭 Expressive prosody & emoji-tone
Emoji modifiers (😂 😠 🤔 🙄) change *how* the message is spoken (laughing, angry, questioning, sarcastic). Humor and sarcasm are socially important and currently almost impossible in AAC.

### 7.7 ⌚ Wear OS companion
Pixel Watch / Wear OS tile with 6–12 quick phrases and a regulation button ("I need a break") that speaks from the watch or the phone. Discreet, always on the body, survives "tablet left in the car" moments. *Novel in Android AAC.*

### 7.8 👥 Ghost-modeling (partner phone)
Parents and teachers model on their **own** phone while the child's device shows a soft animated highlight of the path ("I", then "want", then "more"). Modeling (aided language input) is the most evidence-backed AAC teaching strategy, and today it requires physically touching the child's device, which many autistic users find invasive.

### 7.9 🎨 Generated symbols in "house style"
When a word has no symbol ("Minecraft creeper", "Grandma's dog Biscuit"), generate one in the **same visual style** as the bundled set, or stylize a photo into that style. Visual consistency helps learners. *Generated images are reviewed by the editor before they're saved.*

### 7.10 🧠 "Rescue" / repair assistant
A one-tap "I'm not done", "That's not what I meant", or "Let me try again" strip, plus a "hold the floor" signal: a gentle chime or light that tells the partner "I'm composing". This addresses one of the biggest social barriers: partners interrupt or walk away during slow message construction.

### 7.11 📖 "Tell about my day" narrative builder
Caregivers and school staff add photos and short notes during the day (from their phones). At home, the child gets a **visual story board** from those items so they can tell parents what happened. This supports narrative language, which is a rarely-addressed AAC goal.

### 7.12 🏠 Smart home & phone integration
Buttons that control lights, TV and music (Google Home / Home Assistant), send a text message, or start a video call. AAC words become **actions in the world**, which is powerful motivation for early communicators. Research using AAC speech in phone calls (Android call audio/RTT constraints).

### 7.13 🎮 Playful practice without compromising the board
Optional mini-games ("find 'go'", "build a sentence for this picture") that use **the user's real board layout**, so practice strengthens the actual motor plan. No separate game vocabulary.

### 7.14 🔤 Literacy bridge
For users moving from symbols to text: the keyboard shows the symbol for a word as it's typed, phonics feedback when letters are tapped (sound, not name), and a gradual "fade symbols" setting.

### 7.15 🛡️ Communication passport & medical mode
A shareable one-page profile (how I communicate, what my signs mean, how to support me) and a **medical board** (pain scale, body map, yes/no, "I am allergic to…") that EMTs and hospital staff can use. Optional lock-screen display.

### 7.16 📊 Fair, transparent business model (also an innovation)
"**Speech is free forever**": core boards, TTS, editing and backup are free. Paid tiers cover premium voices, AI features that need compute, cloud sync and team/SLP tools. This differs sharply from $300 paywalls, and it builds community trust and word of mouth.

---

## 8. Non-Functional Requirements

| Area | Requirement |
|---|---|
| **Performance** | Tap → audio < 100 ms (cached TTS for buttons); page switch < 50 ms; cold start to usable board < 1.5 s on Pixel and < 3 s on a 3 GB RAM tablet |
| **Offline** | 100% of core communication works in airplane mode |
| **Reliability** | Crash-free sessions > 99.9%; no data loss on crash, force-stop or OS update |
| **Battery** | < 5% per hour of active use on a Pixel; AI listening mode clearly indicated and auto-timed out |
| **Accessibility** | WCAG 2.2 AA for caregiver UI; minimum target size 48dp (default much larger on the board); TalkBack labels on all controls |
| **Privacy / compliance** | COPPA (children under 13), GDPR/UK-GDPR, FERPA awareness for school data; HIPAA-ready architecture before handling clinical reports; on-device default; no third-party ad or analytics SDKs in the child experience |
| **Security** | Encrypted local DB (SQLCipher or Android Keystore-backed); E2E-encrypted sync; edit PIN |
| **Localization** | Strings externalized from day one; RTL support; English first, then Spanish |
| **Device range** | Android 10+ for tablets (V2); Android 14+ for MVP; works without Google Play Services (Fire OS) |
| **Durability UX** | Accidental-touch protection (palm rejection, debounce), no swipe gestures required on the board, rotation lock option |

---

## 9. V1 MVP Scope (Pixel phone)

**Goal:** A daily-usable AAC app on the product owner's Pixel that proves the core loop and the architecture.

**In scope**
1. Core vocabulary board (phone layout ~5×6 / 6×8) with fixed motor plan + Fitzgerald colors
2. Fringe folders (8–12 categories), quick phrases, emergency/regulation strip
3. Message bar with speak/clear/backspace/share; show-to-partner big-text mode
4. Keyboard with word prediction (on-device n-gram, user-adaptive)
5. Basic morphology (plurals, verb tenses) via long-press
6. Offline TTS (system voices) with rate/pitch; recorded audio per button
7. Edit mode: add/edit/move/hide buttons, symbols from bundled library + camera/gallery, undo + snapshots
8. "Where is it?" word search with path highlight
9. Touch access settings: hold time, accept-on-release, debounce, haptics
10. Local backup/export + Android Auto Backup
11. Edit lock PIN; Quick Settings tile for instant launch
12. **One AI spike (behind a flag):** "Expand my words" using Gemini Nano on the Pixel, with offline fallback = feature hidden

**Out of scope for V1:** switch scanning, kiosk/Device Owner, sync, partner app, OBF, reports, licensed symbols, iOS/web.

**Success criteria**
- The owner can hold a 5-minute conversation using only the app.
- Adding a new word with a photo takes < 30 seconds.
- Zero motor-plan changes caused by any automatic feature.
- Cold start < 1.5 s; tap-to-speech < 100 ms.

---

## 10. Roadmap

| Milestone | Contents |
|---|---|
| **M0 — Foundations** (2–3 wks) | KMP project, vocabulary data model (OBF-compatible), board renderer, TTS service, symbol pipeline |
| **M1 — MVP on Pixel** (6–8 wks) | §9 scope |
| **M2 — Tablet** (6–8 wks) | Responsive grids, vocabulary levels, switch scanning, kiosk mode, VSDs, kid voices, OBF import/export, PDF print, CVI theme, performance on low-end tablets, Fire OS build |
| **M3 — Partner & insight** | Partner/ghost-modeling app, Listen mode, usage reports, coaching tips, Wear OS companion |
| **M4 — Cross-platform** | Compose Multiplatform iOS app; web editor (Kotlin/Wasm or React) for SLPs; E2E sync |
| **M5 — Clinical & funding** | Evaluation reports, dedicated-device mode, SLP pilot studies, licensed symbol partnerships, Medicaid/insurance documentation |

---

## 11. Risks & Open Questions

| Risk / question | Notes / mitigation |
|---|---|
| **Clinical credibility** | Recruit an SLP advisor early; base the core vocabulary layout on published core-word research; run pilots |
| **Symbol licensing** | ARASAAC and Sclera are non-commercial; start with Mulberry/OpenMoji; budget for SymbolStix/PCS later |
| **AI harms** | Hallucinated or "putting words in mouth" suggestions. Mitigate with selection-only output, clear labeling, SLP toggle, no auto-speak, and logs of AI-assisted vs. self-generated utterances |
| **Child data compliance** | COPPA/GDPR-K review before any cloud feature or public release |
| **Funding market** | General tablets are often not fundable; decide later whether to pursue a dedicated-device partner |
| **Fire OS / low-end tablets** | No Play Services → AI features unavailable; ensure graceful degradation |
| **Medical device regulation** | SGDs are generally Class II exempt devices in the US (FDA); confirm before making medical claims or seeking DME funding |
| **Open question** | Brand name, vocabulary layout ownership (create our own vs. license), monetization timing, open-source or not |

---

## 12. Sources & Further Reading

- Spoken — Best AAC Apps for Android: https://spokenaac.com/best-aac-for-android/
- Spoken — Best AAC Apps of 2026: https://spokenaac.com/best-aac-apps/
- Spoken — Is Proloquo2Go available on Android?: https://spokenaac.com/is-proloquo-on-android/
- AbleNet — AAC app comparison guide: https://support.ablenetinc.com/speech-apps/app-comparison-guide/
- Tobii Dynavox — TD Snap 1.37 release notes (Sept 2025): https://www.tobiidynavox.com/blogs/product-news/get-ready-for-td-snap-1-37
- PRC-Saltillo — LAMP WFL on iPhone: https://prc-saltillo.com/blog/LAMPWFL-iPhone
- PRC-Saltillo apps: https://prc-saltillo.com/apps
- CoughDrop — "BAACk to the Future with AI": https://blog.mycoughdrop.com/baack-to-the-future-with-ai/
- "It's Complicated: On the Design and Evaluation of AI-Powered AAC Interfaces" (arXiv): https://arxiv.org/pdf/2606.24854
- "Adapting Large Language Models for Character-based AAC" (arXiv 2501.10582): https://arxiv.org/html/2501.10582v2
- Android Developers Blog — Gemini Nano with on-device ML Kit GenAI APIs (Aug 2025): https://android-developers.googleblog.com/2025/08/the-latest-gemini-nano-with-on-device-ml-kit-genai-apis.html
- Univ. of Colorado — AAC tablet-based devices & Medicaid: https://www.ucdenver.edu/center-for-innovative-design-and-engineering/community-engagement/tech-for-tykes---early-intervention-colorado/ei-resources/tech-tips/tech-tips/aac-tablet-based-devices
- AUCD — LEND Medicaid coverage of iPads/tablets: https://www.aucd.org/conference/detail/session_presentation.cfm?id=24614
- RERC on AAC White Paper: https://communicationmatrix.org/Uploads/Posts/8250/RERC-AAC%20White%20Paper.pdf
- Open Board Format spec: https://www.openboardformat.org/
- Global Symbols (Mulberry, ARASAAC, etc.): https://globalsymbols.com/

*Competitor pricing and platform details are approximate and based on public listings and reviews as of late 2026. Verify them on the app stores before external use.*
