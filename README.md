# AAC Talk

An Android-first AAC (augmentative and alternative communication) app. It works offline, keeps your data private, and is built around a fixed motor plan. Phase 1 (MVP) targeted a Pixel phone; **phase 2** adds tablet and landscape support, switch access and more.

- 📄 [Market analysis & product requirements](docs/AAC-Market-Analysis-and-Requirements.md)

## What's in the MVP

| Area | Features |
|---|---|
| Communicate | 6×8 core board (Fitzgerald colors, fixed positions), 8 fringe folders, 36 quick phrases, always-on strip (Yes / No / Stop / Help / Break / Hurts) |
| Message bar | Build sentences, tap to speak, delete word, clear, share, **partner display** (huge text, flip 180°) |
| Language | Word forms (long-press, or **Forms** then tap: go → goes/going/went/gone, dog → dogs, big → bigger), next-word predictions that learn from you, ✨ grammar helper suggestion ("want go park" → "I want to go to the park.") that never speaks on its own |
| Keyboard | Big-key QWERTY/ABC keyboard with word completion |
| Find | "Where is it?" search shows the path and flashes the buttons to tap |
| Voice | Offline Android TTS, voice/speed/pitch, recorded audio per button, pronunciation fixes |
| Access | Hold time, select-on-release, ignore repeated taps, haptics, TalkBack labels |
| Edit | Edit any button: label, emoji, photo/camera, type/color, folder link, recording, hide (keeps position), move, delete, undo/redo |
| Safety | Settings protected by a 1.5 s hold or a PIN, automatic snapshots every 10 min (last 30 kept), backup/restore to a .zip, Quick Settings "Talk" tile, optional show-on-lock-screen and keep-screen-on |

## Phase 2 additions

| Area | Features |
|---|---|
| Layout | Landscape and tablet layout: menu on the left, quick words on the right, short wide buttons put the picture beside the word. Orientation setting (auto / portrait / landscape) |
| Navigation | **In-app Back bar** above every folder and the keyboard ("◀ Back to Core" plus the page name). Settings has a "Back to talking" button. Nobody needs the phone's back gesture |
| Vocabulary levels | 1 · First words (12), 2 · Early, 3 · Growing, 4 · All. Lower levels hide words in place, so nothing moves as vocabulary grows. Set per button in the editor |
| Switch scanning | 1-switch auto scan or 2-switch step scan, row then button. Works with Bluetooth/USB switches (Space/Enter or 1/2) or by tapping anywhere on screen. Optional spoken cues. Covers the board, keyboard, predictions, quick strip, message buttons and menu |
| Photo scenes | Visual scene displays: a photo with tappable spots. Make one from the editor (folder → "+ New photo scene"), tap the photo to add spots, drag to move, set spot size |
| Pages | Page options in edit mode: rename and resize (buttons keep their row and column) |
| Accessibility | High-contrast (CVI) buttons: black with a thick colored border |
| Sharing | Open Board Format **.obz / .obf** import and export (CoughDrop, Cboard and others), and **print all boards to PDF** as a paper backup |
| Device | "Lock to this app" (screen pinning) so a child stays in the talker |

## iPad web app (0.12.0)

A first version of the talker for iPads runs in Safari and installs from the Share menu ("Add to Home Screen"): full screen, offline, no App Store or developer account. It shares its logic with the Android app (`core`) and its screens are Compose Multiplatform (`shared-ui`), so the same code can later become the native iPad/iPhone and Android screens.

| Area | Features |
|---|---|
| Talk | The boards with symbols and word colours, message bar (speak, delete, clear), folders, words that open a page, quick strip, Back/Home bar of fixed height |
| Boards in and out | Import a TouchChat vocabulary (.ce), a backup from Android or another iPad (.zip), or Open Board files; save a backup (same layout as Android's); go back to the previous boards |
| Voice | The iPad's own voices (speed, pitch, volume, voice age by pitch); recorded sounds from imported boards |
| Settings | Protected by a hold; voice, speak each word, return home after a folder word, labels, high contrast, theme |
| Offline | A service worker keeps the app and all 5,300 symbols on the device; boards, settings and pictures live in the browser's private storage |

Build: `./gradlew :web:wasmJsBrowserDistribution` (needs Node.js and Yarn); CI builds the **aac-web** artifact. Not yet in the web app: editing, word forms, predictions, keyboard, scanning, usage log.

## Install on your Pixel (no Android Studio needed)

1. Open the repo's **Actions** tab → **Android build** → the latest green run (in Chrome, signed in to GitHub).
2. Download the **aac-app-debug-apk** artifact (a zip) and unzip it on your phone. You get `app-debug.apk`.
3. Open the APK and allow "Install unknown apps" for your browser or Files app when asked.
4. Optional: swipe down twice → ✏️ edit → drag the **Talk** tile into Quick Settings.

All builds are signed with the same checked-in debug key, so a newer APK installs over an older one **without losing your words**.

## Build locally

You need Android Studio (or the Android SDK with platform 35) and JDK 17+. Without Android Studio, `scripts/setup-android-sdk.sh` installs the SDK (cloud sessions run it automatically).

The screen tests drive the real app on the JVM (Robolectric) on a Pixel-sized and a tablet-sized screen, portrait and landscape. They save a screenshot of each state; CI attaches them to every build as the **screenshots** artifact.

```bash
./gradlew :core:jvmTest               # shared logic unit tests (iOS: :core:iosSimulatorArm64Test on a Mac)
./gradlew :shared-ui:jvmTest          # shared screen logic (the web app's talker)
./gradlew :app:recordRoborazziDebug   # screen tests; screenshots in app/build/screenshots/
./gradlew :app:assembleDebug  # APK at app/build/outputs/apk/debug/
./gradlew :app:installDebug   # install on a connected phone (USB debugging on)
```

## Phase 3 additions

| Area | Features |
|---|---|
| Symbols | **Mulberry** (3,430 AAC symbols) and **OpenMoji** (1,914) bundled offline, CC BY-SA 4.0. Every starter word has a picture; search the library from the button editor. Settings → Symbols switches back to the phone's emoji. Credits in Settings |
| Vocabulary | ~650 starter words. A 7th core column holds 8 new folders: Body, School, Animals, Activities, Little words, More actions, Health, Weather. Existing boards are upgraded without moving any button |
| Voice age | **Adult / Teen / Kid** for any installed voice (Settings → Voice). The voice is rendered and played back faster with the engine slowed to match, which raises pitch and vocal-tract size without changing speed. Rendered words are cached so repeats are instant |

## Batch A additions (0.4.0)

| Area | Features |
|---|---|
| Voice | **Volume** slider (on top of the phone's media volume), applied to speech and recorded button audio |
| Touch | **Slide to select**: keep a finger on the board and slide; resting on a button for the set time chooses it (lifting cancels). **Spaces between buttons count as touches** (no dead zones) |
| Editing | **Copy / paste buttons** (Copy in the button editor, then tap an empty spot) and **Duplicate page** (Page options) |
| Photos | New photos go through a **crop** step (drag and pinch to frame; or use the whole photo) |
| Vocabulary | **Explore mode** (Settings → Show Explore button): shows words hidden by level or by hand, faded, until turned off. Nothing moves |
| Keyboard | **Abbreviations** (Settings → Voice → Abbreviations): "hru" + space becomes "how are you"; also offered as a prediction |

## Batch B additions (0.5.0)

| Area | Features |
|---|---|
| Usage log | **Opt-in** (Settings → Usage log). Logs words added to the message and messages spoken, with times. Stays on the device, excluded from Android cloud backup and device transfer, trimmed after 400 days |
| Privacy pause | While logging is on, a red dot next to the suggestions shows it. The person can tap it to pause (and again to resume) without opening settings |
| Usage report | Settings → Usage report: words used, different words, active days, messages spoken, average words per message (MLU), core-word share, words per day, new words, most-used words, and a Core-page heat map. 7 days / 30 days / everything. **Export CSV** and **Clear log** |

## Batch C additions (0.6.0)

| Area | Features |
|---|---|
| Tone buttons | Settings → Voice → Show tone buttons: a row under the message (Question, Excited, Whisper, Loud, Sad, Sarcastic). One tap says the message in that tone, using speed, pitch, loudness and end punctuation (approximations: the phone's voices can't truly whisper) |
| Private earbud | With a headset or earbud connected, scanning cues, typed-letter echoes and previews play only in the earbud, and real speech is sent to the phone's speaker. Without one, everything plays out loud |
| Preview on first tap | The first tap on a board button says it (privately with the earbud option); a second tap on the same button uses it. The quick strip always works on the first tap |
| Auditory scanning | "Speak each row and button as it lights up" plus the private earbud: only the person hears the options, and only their choice is said out loud |

## 0.7.0 additions

| Area | Features |
|---|---|
| Add a word | In edit mode, the **Add a word** button: type a word and the wizard picks a symbol, the kind of word (its color), a folder and the first empty spot, flags words already on a board, and shows the word forms. Everything can be changed before adding |

## 0.8.0 additions

| Area | Changes |
|---|---|
| How to use | Settings → **How to use**: a guided tour of the main screen (offered the first time the app opens) and the full instruction manual (`app/src/main/assets/help/manual.md`) |
| Settings | Big **Edit boards** and **How to use** cards at the top |
| Pictures | Starter pictures checked against their folder: "tablet" is now a tablet computer, and nails, bottom, chicken, orange (colour), fly, answer, park and letters fixed. Existing boards are updated unless the picture was changed by hand. Picture search prefers symbols that fit the folder |
| Forms | Tapping **Forms** after a word changes that word (want → wanted); with an empty message it says what to do next |
| Crop | A zoomed photo stays inside the crop square |

## 0.8.1 changes

| Area | Changes |
|---|---|
| Tour | Spotlights now line up with their targets on real phones (they were shifted up by the status bar height) |
| Expand my words | Shelved: the on-device Gemini feature is removed until it can be tested on a supported phone. The code is kept on the `shelved/gemini-expand-words` branch |

## 0.9.0: Fire tablet

| Area | Changes |
|---|---|
| Install | Every build of `main` publishes a plain **aac-app.apk** on the **latest** release, so phones and Fire tablets can install it straight from the browser (no zip) |
| Fire HD 10 | Screen tests run as Android 11 (Fire OS 8) at the Fire HD 10 size (1280×800 dp) in both orientations |
| Voices | On Fire tablets, voice help points to Fire Settings → Keyboard & Language → Text-to-Speech |
| Lock | If "Lock to this app" doesn't take (Fire needs Screen Pinning turned on), the app says how to turn it on |
| Layout | One label size per page (only unusually long words get smaller); bigger message words on tablets |

Symbol assets are rebuilt with `scripts/build-symbols.py` (see the script header).

## Project layout

```
core/  Pure Kotlin, no Android: vocabulary model (grids, photo scenes, levels), starter
       vocabulary, morphology, predictor, grammar helper, search, Open Board Format.
       Kotlin Multiplatform: builds for Android (JVM) and iOS (iosArm64,
       iosSimulatorArm64, iosX64; compiled and tested on macOS in CI).
app/   Android app (Jetpack Compose)
  data/    vocabulary storage (atomic JSON + undo + snapshots), settings, media, backup,
           Open Board files, PDF printing
  speech/  text-to-speech and audio recording
  ui/      board, photo scenes, message bar, keyboard, switch scanner, dialogs, editor, settings
  tile/    Quick Settings tile
```

## Design rules the code follows

1. **Speech never needs the network, an account, or AI.**
2. **Buttons never move on their own.** Hidden buttons keep their slot. Predictions and suggestions live in their own row.
3. **Suggestions never speak by themselves.** The user picks.

## Next steps (from the roadmap)

Gemini Nano "expand my words" (on-device), neural voice packs (tried in 0.3.0, shelved for buzz on Teen/Kid and mispronounced single words; code on the `shelved/neural-voice-packs` branch), downloadable neural voices (Piper/Kokoro via sherpa-onnx), partner "ghost modeling" app, Listen mode, usage reports for SLPs, Wear OS companion, then iOS/web via Kotlin Multiplatform.
