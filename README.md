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
./gradlew :core:test                  # shared logic unit tests
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

Symbol assets are rebuilt with `scripts/build-symbols.py` (see the script header).

## Project layout

```
core/  Pure Kotlin, no Android: vocabulary model (grids, photo scenes, levels), starter
       vocabulary, morphology, predictor, grammar helper, search, Open Board Format.
       Becomes a Kotlin Multiplatform module for iOS/web.
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

Gemini Nano "expand my words" (on-device), downloadable neural voices (Piper/Kokoro via sherpa-onnx), partner "ghost modeling" app, Listen mode, usage reports for SLPs, Wear OS companion, then iOS/web via Kotlin Multiplatform.
