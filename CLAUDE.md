# AAC App: notes for contributors (people and Claude)

- **Keep the user guide current.** The in-app manual is `app/src/main/assets/help/manual.md`
  (Settings → How to use). Any change that adds, removes or renames a feature or setting must
  update it in the same change. `ScreenTest.manualCoversTheFeatures` lists the features the
  guide must mention: add new ones there too. If the main screen layout changes, check the
  guided tour steps in `ui/Tutorial.kt` still point at the right places.
- **Motor plan:** buttons never move on their own. Nothing automatic may shift the board
  (no banners that push it down, no reordering). Suggestions never speak by themselves.
- **Starter pictures must fit their folder.** Symbols are mapped by `scripts/build-symbols.py`;
  use `CELL_OVERRIDES` for words whose library match means something else in that folder, and
  bump `Vocabulary.CURRENT_VERSION` with a migration when existing boards should change.
- **Offline and private:** the app has no internet permission. Don't add network features to
  speech or logging.
- **Modules:** `core` (shared logic, Kotlin Multiplatform: JVM/Android, iOS, web), `shared-ui`
  (Compose Multiplatform screens: the web app today, the iPad/iPhone and Android apps as they
  move over), `web` (the installable web app for iPads), `app` (Android). New logic goes in
  `core`; new screens should be written so they can live in `shared-ui`.
- **Web app:** keep it offline-first: everything it needs ships with it (symbols, fonts) and
  the service worker (`web/src/wasmJsMain/resources/sw.js`) keeps it on the device. Bump the
  cache name there when cached files change shape.
- Tests: `./gradlew :core:jvmTest :shared-ui:jvmTest :app:recordRoborazziDebug` (screenshots land in
  `app/build/screenshots/`). AlertDialog-based popups hang Robolectric; prefer `Dialog`.
  Web app: `./gradlew :web:wasmJsBrowserDevelopmentExecutableDistribution` builds to
  `web/build/dist/wasmJs/developmentExecutable/` (serve it and open it in a browser); it needs
  Node.js and Yarn installed.
