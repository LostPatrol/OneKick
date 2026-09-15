# CPM standard RIGHT_LEG compatibility - 1.21.1

## Result

- Implemented a CPM compatibility path limited to geometry assigned to CPM's standard
  `RIGHT_LEG` root.
- First-person and third-person kick/charge poses use CPM's public player-renderer API.
- No mixin and no CPM internal class access were added.
- CPM-unavailable execution remains behind the existing mod-loaded guard and continues
  to use One Kick's original renderer.
- Arbitrary custom geometry outside `RIGHT_LEG` is intentionally unsupported.
- The existing PAL compatibility path is unchanged; simultaneous PAL + CPM third-person
  rendering is intentionally left on the established PAL path to avoid cross-mod state
  changes in this minimal fix.

## Change scope

- Added an isolated CPM renderer bridge and one final player render layer.
- Added guarded CPM plugin registration and render lifecycle cleanup.
- Added the CPM API as a compile-only dependency; it is not bundled into One Kick.
- Updated the first-person dispatcher to prefer the CPM path only when CPM is loaded and
  the bridge successfully renders an active kick pose.

## Verification

- `gradlew.bat compileJava`: passed.
- `gradlew.bat test build`: passed.
- 50 existing unit tests passed with zero failures or errors.
- Production jar contains the CPM bridge classes.
- Built jar: `build/libs/onekick-1.21.1-0.1.3.jar`.
- SHA-256: `A82437B6102D500820E8B95218FAB3079DE6A3A4F2FB46B6AB2B4EF3DA3F6BB7`.

## Remaining verification and risk

- Automated tests cannot validate Minecraft render output. Manual testing with the
  supplied CPM model is still required for first-person, third-person, charged, and
  uncharged kicks.
- CPM models that attach apparent leg geometry outside the standard `RIGHT_LEG` root
  remain outside the agreed compatibility scope.
