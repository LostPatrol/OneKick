# CPM special-model fallback — 1.21.1

## Outcome

Removed the experimental CPM custom-leg renderer and restored One Kick's established
vanilla-skin first-person path. CPM may remain installed when a vanilla player model is
used. CPM models that replace or disable vanilla limb animation are intentionally not
supported.

## Changes

- Removed the CPM API repository, compile-only dependency, and version property.
- Removed CPM IMC plugin registration and optional-class loading from client events.
- Removed the CPM-specific first-person renderer bridge.
- Kept the original kick animation, networking, gameplay, particle, HUD, and PAL paths
  unchanged.
- Documented the supported fallback boundary in `MEMORY.md`.

## Reason for conservative fallback

CPM's public API cannot identify an arbitrary model author's semantic right-leg geometry.
A general custom-model solution would require a second player renderer, per-frame hiding
and restoration of CPM parts, cancellation cleanup, and special handling for armor and
nonstandard bone layouts. That scope is disproportionate to the requested minimal,
stability-first change.

## Verification

- `gradlew.bat test build`: passed.
- Production jar: `build/libs/onekick-1.21.1-0.1.3.jar`.
- SHA-256: `956C816E45E71739FDA3186D3A7EC43A13BC313445585E1FCC4D6173435E9DB7`.
- Source scan confirms no CPM API reference remains outside the documented support
  boundary.

## Known limitation

With a CPM special model active, One Kick does not guarantee that its visible custom leg
will animate. The gameplay kick still functions; only CPM-owned custom-model rendering is
outside the supported compatibility scope.
