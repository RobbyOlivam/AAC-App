# AAC Talk

An Android-first AAC (augmentative and alternative communication) app. It works offline, keeps your data private, and is built around a fixed motor plan. This is the **MVP**, aimed at a Pixel phone.

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

## Install on your Pixel (no Android Studio needed)

1. Open the repo's **Actions** tab → **Android build** → the latest green run.
2. Download the **aac-app-debug-apk** artifact (a zip) and unzip it on your phone. You get `app-debug.apk`.
3. Open the APK and allow "Install unknown apps" for your browser or Files app when asked.
4. Optional: swipe down twice → ✏️ edit → drag the **Talk** tile into Quick Settings.

All builds are signed with the same checked-in debug key, so a newer APK installs over an older one **without losing your words**.

## Build locally

You need Android Studio (or the Android SDK with platform 35) and JDK 17+.

```bash
./gradlew :core:test          # shared logic unit tests
./gradlew :app:assembleDebug  # APK at app/build/outputs/apk/debug/
./gradlew :app:installDebug   # install on a connected phone (USB debugging on)
```

## Project layout

```
core/  Pure Kotlin, no Android: vocabulary model, starter vocabulary, morphology,
       predictor, grammar helper, search. Becomes a Kotlin Multiplatform module for iOS/web.
app/   Android app (Jetpack Compose)
  data/    vocabulary storage (atomic JSON + undo + snapshots), settings, media, backup
  speech/  text-to-speech and audio recording
  ui/      board, message bar, keyboard, dialogs, editor, settings
  tile/    Quick Settings tile
```

## Design rules the code follows

1. **Speech never needs the network, an account, or AI.**
2. **Buttons never move on their own.** Hidden buttons keep their slot. Predictions and suggestions live in their own row.
3. **Suggestions never speak by themselves.** The user picks.

## Next steps (from the roadmap)

Gemini Nano "expand my words" (on-device), kid voices, switch scanning, vocabulary levels, OBF import/export, kiosk mode, and a tablet layout.
