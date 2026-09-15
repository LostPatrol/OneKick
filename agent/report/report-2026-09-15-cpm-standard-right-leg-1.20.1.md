# CPM standard RIGHT_LEG compatibility - 1.20.1

## Result

- Implemented a CPM compatibility path limited to geometry assigned to CPM's standard
  `RIGHT_LEG` root.
- First-person and third-person kick/charge poses use CPM's public player-renderer API.
- No mixin and no CPM internal class access were added.
- CPM-unavailable execution remains behind the existing mod-loaded guard and continues
  to use One Kick's original renderer.
- Arbitrary custom geometry outside `RIGHT_LEG` is intentionally unsupported.

## Change scope

- Extended the existing isolated CPM renderer bridge and added one final player render
  layer.
- Added guarded render preparation and lifecycle cleanup around the standard right leg.
- The CPM API remains compile-only and is not bundled into One Kick.
- Updated the first-person bridge to apply the kick pose after CPM binds its redirect
  part, while retaining the original renderer as its failure fallback.

## Verification

- `gradlew.bat compileJava`: passed.
- `gradlew.bat test build`: passed.
- 50 existing unit tests passed with zero failures or errors.
- Production jar contains the CPM bridge classes.
- Built jar: `build/libs/onekick-1.20.1-0.1.2.jar`.
- SHA-256: `9AE55D279688714A73871620C56145CD237553826D03204F9FB6A4BE91F50A46`.

## Remaining verification and risk

- Automated tests cannot validate Minecraft render output. Manual testing with the
  supplied CPM model is still required for first-person, third-person, charged, and
  uncharged kicks.
- CPM models that attach apparent leg geometry outside the standard `RIGHT_LEG` root
  remain outside the agreed compatibility scope.
