# iOS port plan

Target: the family's iPads (Griffin's current AAC device runs TouchChat), then iPhone.

## Now: the web app (no Mac or Apple Developer account needed)

While the native app waits for an Apple Developer account, iPads run the **web app** (`web/`):
Compose Multiplatform compiled to WebAssembly, installed from Safari with "Add to Home
Screen", offline through a service worker. Its screens live in `shared-ui` (common code), so
they are the same screens the native iPad/iPhone app will host (`ComposeUIViewController`),
and the Android app can move onto them screen by screen. Platform services for the web are in
`web/.../WebPlatform.kt` and `platform.js` (IndexedDB storage, Web Speech, file pick/save).

Shared since the web app: `Settings` (moved to core), `Zip` (read/write in plain Kotlin, so
iOS needs no zip library), `SqliteReader` and the TouchChat import, the `Talker` state holder
and its screens.

## What is already shared

`core/` is a Kotlin Multiplatform module that builds for Android and iOS (`iosArm64`,
`iosSimulatorArm64`, `iosX64`). CI's `ios-core` job (macOS) compiles it for iOS and runs all core
tests on the iOS simulator. Everything that decides *what* the app does lives there:

| Area | Code |
|---|---|
| Vocabulary model, starter boards, upgrades between versions | `Model.kt`, `DefaultVocabulary.kt`, `DefaultSymbols.kt` |
| Editing operations, search, "Where is it?" paths | `VocabularyOps.kt` |
| Word forms, predictions, grammar helper, add-a-word wizard | `Morphology.kt`, `Predictor.kt`, `GrammarHelper.kt`, `WordWizard.kt` |
| Open Board Format import/export | `OpenBoardFormat.kt` |
| Usage log and reports | `Usage.kt` |
| Symbol library index and search | `SymbolIndex.kt` |
| Voice age and tones | `Wav.kt` (`VoiceAge`, `Tone`) |
| What each platform must provide | `platform/Platform.kt` |

## What the iOS app provides (`core/platform/Platform.kt`)

| Interface | Android today | iOS |
|---|---|---|
| `SpeechOutput` | `speech/Speaker.kt`: TextToSpeech; AudioTrack for voice age and earbud/speaker routing; MediaPlayer for recordings | `AVSpeechSynthesizer` (rate, pitch, voice; Personal Voice where allowed); `AVAudioEngine` pitch/rate units or render-to-buffer for voice age; `AVAudioSession` route for earbud vs speaker; `AVAudioPlayer` for recordings |
| `DeviceLock` | `device/AndroidDevice.kt`: screen pinning | Guided Access: `UIAccessibility.requestGuidedAccessSession` (needs the app to be supervised or the user to start Guided Access; otherwise show `setupHint`: Settings → Accessibility → Guided Access) |

## Still Android-only (to give iOS counterparts when the UI moves)

| Piece | Android | iOS plan |
|---|---|---|
| Screens (Compose) | `ui/*` | Compose Multiplatform: move `ui/` into a shared `shared-ui` module; Android-only calls (Toast, activity results, lock task) go behind small interfaces like the ones above |
| Symbol drawing | `data/SymbolLibrary.kt` (AndroidSVG → Bitmap) | Skia's SVG renderer in Compose Multiplatform, or pre-rendered PNGs; the index/search is already shared |
| Storage (vocabulary JSON, undo, snapshots) | `data/VocabularyStore.kt` (java.io files) | Same JSON (`VocabularyJson`) in the app's Documents folder; move the store to `core` with an expect/actual file API |
| Settings | `data/Settings.kt` (SharedPreferences + JSON) | NSUserDefaults (or a JSON file), same `Settings` data class once moved to core |
| Photos, crop, recordings | `data/MediaFiles.kt`, `ui/CropDialog.kt`, `speech/AudioRecorder.kt` | PHPicker / camera; same crop maths (`CropMath`); `AVAudioRecorder` |
| Backup, OBF files, PDF | `data/BackupManager.kt`, `OpenBoardFiles.kt`, `PdfExporter.kt` | Same zip layout (so backups move between Android and iPad); UIDocumentPicker; PDFKit |
| Usage log file | `data/UsageStore.kt` | Same JSON-lines format via the shared file API |
| Quick Settings tile | `tile/` | Not on iOS (a Home Screen shortcut or widget instead) |

## Order of work

1. Done: settings in `core`; the web app.
2. Done: `shared-ui` has all of the Android app's screens and `AacViewModel`, a shared data
   layer (`data/Stores.kt`) and platform interfaces for storage, files, pictures, recording and
   device services (`shared/Platform.kt`).
3. Switch the Android app to `shared-ui` (an Android `Platform`: files dir, ImageDecoder,
   MediaRecorder, TextToSpeech), then delete `app/.../ui`.
4. Add the iOS app (Xcode project hosting the shared UI) and implement `SpeechOutput` and `DeviceLock`.
5. Install on the iPads through TestFlight. That needs an Apple Developer account and the builds
   are signed on macOS (CI can do this with the signing certificate in repository secrets).
6. Cross-check: a backup made on Android restores on iPad and the other way round.
