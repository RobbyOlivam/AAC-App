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
- Tests: `./gradlew :core:jvmTest :app:recordRoborazziDebug` (screenshots land in
  `app/build/screenshots/`). AlertDialog-based popups hang Robolectric; prefer `Dialog`.
