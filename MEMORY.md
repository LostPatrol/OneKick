# Project memory

## Current baseline

- Minecraft 1.20.1 / Forge 47.4.10 targets Java 17 through the project toolchain.
- One Kick 1.20.1-0.1.2 uses its existing `KickPlayerModel` replacement for
  third-person animation and its `RenderHandEvent` renderer for first-person kicks.
- Customizable Player Models 0.6.27 is an optional, compile-only client integration.
  When CPM is present, its supported rendering API supplies the custom right-leg mesh
  and texture for the first-person kick; without CPM, the original vanilla-skin
  renderer executes unchanged.

## Known risk

- The CPM bridge intentionally renders only the right-leg root in
  `AnimationMode.HAND`. Unusual models that attach kick-visible geometry outside that
  root remain a manual compatibility edge case.
